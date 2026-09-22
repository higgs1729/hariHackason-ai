package com.hanamizuki.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.domain.Photo;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.integration.storage.FileStorage;
import com.hanamizuki.backend.repository.AlbumPhotoRepository;
import com.hanamizuki.backend.repository.PhotoRepository;

@Service
public class PhotoService {

    private final PhotoRepository photos;
    private final AlbumPhotoRepository albumPhotos;
    private final FileStorage storage;

    public PhotoService(PhotoRepository photos, AlbumPhotoRepository albumPhotos,
                        FileStorage storage) {
        this.photos = photos;
        this.albumPhotos = albumPhotos;
        this.storage = storage;
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
}
