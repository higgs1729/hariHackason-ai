package com.hanamizuki.backend.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.api.decoration.DecorationDtos.DecorationVo;
import com.hanamizuki.backend.common.Times;
import com.hanamizuki.backend.domain.AlbumMember;
import com.hanamizuki.backend.domain.AlbumPhoto;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.integration.storage.FileStorage;
import com.hanamizuki.backend.repository.AlbumPhotoRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The drawing on one photo in one album.
 *
 * <p>{@code overlayData} is the record and the two images are renderings of it.
 * The elements are stored as opaque JSON: the backend never needs to know what
 * a stroke is, and keeping it uninterpreted means the frontend can add an
 * element type without a backend release.
 */
@Service
public class DecorationService {

    private final AlbumPhotoRepository albumPhotos;
    private final AlbumService albumService;
    private final FileStorage storage;
    private final ObjectMapper objectMapper;

    public DecorationService(AlbumPhotoRepository albumPhotos, AlbumService albumService,
                             FileStorage storage, ObjectMapper objectMapper) {
        this.albumPhotos = albumPhotos;
        this.albumService = albumService;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DecorationVo get(Long albumId, Long albumPhotoId, Long userId) {
        albumService.requireMember(albumId, userId);
        return toVo(require(albumId, albumPhotoId));
    }

    /**
     * Replaces the whole element list.
     *
     * <p>A photo nobody has drawn on yet still has a version — editing its
     * caption moves it — so the caller's {@code If-Match} is compared against
     * the live value rather than assumed to be zero.
     */
    @Transactional
    public DecorationVo replace(Long albumId, Long albumPhotoId, Long userId,
                                JsonNode elements, Integer ifMatch) {
        AlbumMember me = albumService.requireMember(albumId, userId);
        AlbumPhoto ap = require(albumId, albumPhotoId);
        checkVersion(ap, ifMatch);

        if (elements == null || !elements.isArray()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "elements must be an array");
        }

        ap.setOverlayData(objectMapper.writeValueAsString(elements));
        ap.setOverlayUserId(userId);
        ap.setOverlayUserName(me.getUserName());
        ap.setOverlayUpdateTime(LocalDateTime.now());
        // Both renderings are now stale. They are replaced by the follow-up
        // upload; until then the composite URL falls back to the plain photo.
        ap.setOverlayPath(null);
        ap.setCompositePath(null);

        albumPhotos.saveAndFlush(ap);
        return toVo(ap);
    }

    /**
     * Stores the images the client rendered.
     *
     * <p>Deliberately a separate call from {@link #replace}: if this one fails,
     * the element list has already been saved and nothing is lost but a cache.
     */
    @Transactional
    public void saveRendered(Long albumId, Long albumPhotoId, Long userId,
                             byte[] overlayPng, byte[] compositeJpg) {
        albumService.requireMember(albumId, userId);
        AlbumPhoto ap = require(albumId, albumPhotoId);

        if (overlayPng != null && overlayPng.length > 0) {
            ap.setOverlayPath(storage.write("overlays/%d.png".formatted(ap.getId()), overlayPng));
        }
        if (compositeJpg != null && compositeJpg.length > 0) {
            ap.setCompositePath(storage.write("composites/%d.jpg".formatted(ap.getId()), compositeJpg));
        }
        albumPhotos.save(ap);
    }

    /**
     * The flattened image, or the untouched photo when no rendering exists.
     *
     * <p>Falling back rather than answering 404 keeps a share link working even
     * if the render upload failed — the drawing is missing, the album is not.
     */
    @Transactional(readOnly = true)
    public byte[] composite(Long albumId, Long albumPhotoId) {
        AlbumPhoto ap = require(albumId, albumPhotoId);
        if (storage.exists(ap.getCompositePath())) {
            return storage.read(ap.getCompositePath());
        }
        if (storage.exists(ap.getPhotoUrl())) {
            return storage.read(ap.getPhotoUrl());
        }
        throw new ApiException(ErrorCode.PHOTO_NOT_FOUND, "No image on disk for this photo");
    }

    private AlbumPhoto require(Long albumId, Long albumPhotoId) {
        return albumPhotos.findByIdAndAlbumId(albumPhotoId, albumId)
                .orElseThrow(() -> new ApiException(ErrorCode.PHOTO_NOT_FOUND,
                        "This photo is not in that album"));
    }

    private void checkVersion(AlbumPhoto ap, Integer ifMatch) {
        if (ifMatch == null) {
            throw new ApiException(ErrorCode.IF_MATCH_REQUIRED,
                    "If-Match is required on this route");
        }
        if (ifMatch != ap.getVersion()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT,
                    "Someone else changed this photo first",
                    Map.of("current", toVo(ap)));
        }
    }

    private DecorationVo toVo(AlbumPhoto ap) {
        JsonNode elements = ap.getOverlayData() == null
                ? objectMapper.createArrayNode()
                : objectMapper.readTree(ap.getOverlayData());
        return new DecorationVo(elements, ap.getOverlayUserId(),
                Times.toOffset(ap.getOverlayUpdateTime()), ap.getVersion());
    }
}
