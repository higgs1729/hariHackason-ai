package com.hanamizuki.backend.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hanamizuki.backend.api.photo.PhotoVos.PhotoVo;
import com.hanamizuki.backend.api.photo.PhotoVos.RejectedUploadVo;
import com.hanamizuki.backend.api.photo.PhotoVos.UploadResultVo;
import com.hanamizuki.backend.api.photo.PhotoVos.UploadedPhotoVo;
import com.hanamizuki.backend.common.PageVo;
import com.hanamizuki.backend.domain.Photo;
import com.hanamizuki.backend.domain.User;
import com.hanamizuki.backend.domain.enums.TakenTimeSource;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.integration.image.ExifMetadata;
import com.hanamizuki.backend.integration.image.ExifReader;
import com.hanamizuki.backend.integration.image.ImageProcessor;
import com.hanamizuki.backend.integration.storage.FileStorage;
import com.hanamizuki.backend.repository.AlbumPhotoRepository;
import com.hanamizuki.backend.repository.PhotoRepository;
import com.hanamizuki.backend.repository.UserRepository;

/** Ingesting photos, listing them, and serving their bytes. */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    /** HEIC is absent on purpose: the JDK cannot decode it. See Q-2. */
    private static final Set<String> ACCEPTED = Set.of("image/jpeg", "image/png");
    private static final int THUMB_EDGE = 400;

    private final PhotoRepository photos;
    private final AlbumPhotoRepository albumPhotos;
    private final UserRepository users;
    private final FileStorage storage;
    private final ExifReader exifReader;
    private final ImageProcessor images;

    public PhotoService(PhotoRepository photos, AlbumPhotoRepository albumPhotos,
                        UserRepository users, FileStorage storage,
                        ExifReader exifReader, ImageProcessor images) {
        this.photos = photos;
        this.albumPhotos = albumPhotos;
        this.users = users;
        this.storage = storage;
        this.exifReader = exifReader;
        this.images = images;
    }

    /**
     * Ingests a batch.
     *
     * <p>Each file is handled independently and failures are collected rather
     * than thrown: nineteen good photos should not be lost because the twentieth
     * was a video.
     */
    @Transactional
    public UploadResultVo upload(List<MultipartFile> files, Long userId) {
        User owner = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        List<UploadedPhotoVo> uploaded = new ArrayList<>();
        List<RejectedUploadVo> rejected = new ArrayList<>();

        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
            try {
                uploaded.add(UploadedPhotoVo.of(ingest(file, owner)));
            } catch (ApiException e) {
                rejected.add(new RejectedUploadVo(name, e.code().name()));
            } catch (Exception e) {
                log.warn("Could not ingest {}", name, e);
                rejected.add(new RejectedUploadVo(name, ErrorCode.UNSUPPORTED_MEDIA.name()));
            }
        }
        return new UploadResultVo(uploaded, rejected);
    }

    private Photo ingest(MultipartFile file, User owner) throws IOException {
        if (!ACCEPTED.contains(file.getContentType())) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA,
                    "Only JPEG and PNG are accepted");
        }
        byte[] original = file.getBytes();
        String hash = sha256(original);

        // The same file twice is the same photo. Returning the existing row is
        // friendlier than an error and makes a retried upload harmless.
        Photo existing = photos.findByUserIdAndSha256(owner.getId(), hash).orElse(null);
        if (existing != null) {
            return existing;
        }

        // Read EXIF first: the re-encode below destroys it.
        ExifMetadata exif = exifReader.read(original);
        BufferedImage upright = images.applyOrientation(images.decode(original), exif.orientation());
        byte[] jpeg = images.toJpeg(upright);
        byte[] thumb = images.toJpeg(images.scaleToFit(upright, THUMB_EDGE));

        Photo photo = new Photo();
        photo.setUserId(owner.getId());
        photo.setUserName(owner.getUserName());
        photo.setPicName(file.getOriginalFilename());
        photo.setSha256(hash);
        // Dimensions after rotation, not before — a portrait photo stored from
        // a landscape sensor would otherwise be described the wrong way round.
        photo.setPicWidth(upright.getWidth());
        photo.setPicHeight(upright.getHeight());
        photo.setPicScale((double) upright.getWidth() / upright.getHeight());
        photo.setPicSize((long) jpeg.length);
        photo.setPicFormat("jpeg");
        photo.setExifOrientation(exif.orientation());
        photo.setTakenTime(exif.takenTime() == null ? LocalDateTime.now() : exif.takenTime());
        photo.setTakenTimeSource(exif.takenTime() == null
                ? TakenTimeSource.UPLOAD : TakenTimeSource.EXIF);
        photo.setLatitude(exif.latitude());
        photo.setLongitude(exif.longitude());
        // The filename needs the id, and the id needs the insert, but filePath
        // is NOT NULL. Insert with a placeholder, then overwrite while managed.
        photo.setFilePath("");
        photos.save(photo);

        photo.setFilePath(storage.write(pathFor(photo, "photos", "jpg"), jpeg));
        photo.setThumbPath(storage.write(pathFor(photo, "thumbs", "jpg"), thumb));

        owner.setPhotoNum(owner.getPhotoNum() + 1);
        return photo;
    }

    /** Dated folders keep a single directory from collecting thousands of files. */
    private String pathFor(Photo photo, String kind, String extension) {
        LocalDateTime when = photo.getTakenTime();
        return "%s/%d/%02d/%d.%s".formatted(kind, when.getYear(), when.getMonthValue(),
                photo.getId(), extension);
    }

    /**
     * @param unassigned when true, only photos not yet in any album — which is
     *                   {@code albumNum = 0}, a single indexed column rather
     *                   than a NOT EXISTS against album_photo
     */
    @Transactional(readOnly = true)
    public PageVo<PhotoVo> list(Long userId, boolean unassigned, int limit) {
        List<Photo> found = unassigned
                ? photos.findByUserIdAndAlbumNumOrderByTakenTimeAsc(userId, 0, Limit.of(limit))
                : photos.findByUserIdOrderByTakenTimeAsc(userId, Limit.of(limit));
        // No cursor yet: the demo never has enough photos for a second page,
        // and the envelope leaves room to add one without a shape change.
        return PageVo.of(found.stream().map(PhotoVo::of).toList(), found.size());
    }

    /**
     * Photo bytes, thumbnail or original.
     *
     * <p>Ids are sequential, so this checks that the caller may actually see
     * this photo: they uploaded it, or they are in an album that contains it.
     * Without the check, counting upwards would walk everyone's camera roll.
     */
    @Transactional(readOnly = true)
    public byte[] serve(Long photoId, Long userId, boolean thumb) {
        Photo photo = photos.findById(photoId)
                .orElseThrow(() -> new ApiException(ErrorCode.PHOTO_NOT_FOUND));

        boolean mine = photo.getUserId().equals(userId);
        if (!mine && !albumPhotos.isVisibleThroughAlbum(photoId, userId)) {
            throw new ApiException(ErrorCode.ALBUM_FORBIDDEN, "This photo is not shared with you");
        }

        String path = thumb && storage.exists(photo.getThumbPath())
                ? photo.getThumbPath()
                : photo.getFilePath();
        if (!storage.exists(path)) {
            throw new ApiException(ErrorCode.PHOTO_NOT_FOUND, "The file is missing on disk");
        }
        return storage.read(path);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
