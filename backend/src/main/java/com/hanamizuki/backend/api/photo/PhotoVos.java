package com.hanamizuki.backend.api.photo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.hanamizuki.backend.common.Times;
import com.hanamizuki.backend.domain.Photo;

/** Response shapes for /api/photos, matching {@code frontend/src/api/types.ts}. */
public final class PhotoVos {

    private PhotoVos() {
    }

    static String url(Long photoId) {
        return "/api/photos/" + photoId;
    }

    static String thumbUrl(Long photoId) {
        return "/api/photos/" + photoId + "/thumb";
    }

    /** The full row, used by the picker on screen 03. */
    public record PhotoVo(Long id, String url, String thumbUrl,
                          Integer picWidth, Integer picHeight, Double picScale,
                          OffsetDateTime takenTime, String takenTimeSource,
                          BigDecimal latitude, BigDecimal longitude, int albumNum) {

        public static PhotoVo of(Photo photo) {
            return new PhotoVo(photo.getId(), PhotoVos.url(photo.getId()),
                    PhotoVos.thumbUrl(photo.getId()),
                    photo.getPicWidth(), photo.getPicHeight(), photo.getPicScale(),
                    Times.toOffset(photo.getTakenTime()), photo.getTakenTimeSource().name(),
                    photo.getLatitude(), photo.getLongitude(), photo.getAlbumNum());
        }
    }

    /** The slimmer shape the camera screen gets back after an upload. */
    public record UploadedPhotoVo(Long id, String url, Integer picWidth, Integer picHeight,
                                  OffsetDateTime takenTime,
                                  BigDecimal latitude, BigDecimal longitude) {

        public static UploadedPhotoVo of(Photo photo) {
            return new UploadedPhotoVo(photo.getId(), PhotoVos.url(photo.getId()),
                    photo.getPicWidth(), photo.getPicHeight(),
                    Times.toOffset(photo.getTakenTime()),
                    photo.getLatitude(), photo.getLongitude());
        }
    }

    /** {@code code} is an ErrorCode name, so the UI can explain the rejection. */
    public record RejectedUploadVo(String filename, String code) {
    }

    /**
     * Split so one bad file cannot fail the batch. Someone who took twenty
     * photos and has one unreadable among them should still get nineteen.
     */
    public record UploadResultVo(List<UploadedPhotoVo> uploaded,
                                 List<RejectedUploadVo> rejected) {
    }
}
