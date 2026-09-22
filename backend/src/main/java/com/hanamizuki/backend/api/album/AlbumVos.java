package com.hanamizuki.backend.api.album;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.hanamizuki.backend.common.Times;
import com.hanamizuki.backend.domain.Album;
import com.hanamizuki.backend.domain.AlbumMember;
import com.hanamizuki.backend.domain.AlbumPhoto;

/**
 * Response shapes for /api/albums, matching {@code frontend/src/api/types.ts}.
 *
 * <p>Both of these are built from {@code album} and {@code album_photo} alone.
 * Neither {@code photo} nor {@code user} is read: the columns that would need a
 * join were copied in when the rows were written, which is the whole point of
 * the denormalisation policy (03-detailed-design section 1.2.1).
 */
public final class AlbumVos {

    private AlbumVos() {
    }

    static String photoUrl(Long photoId) {
        return "/api/photos/" + photoId;
    }

    static String thumbUrl(Long photoId) {
        return photoId == null ? null : "/api/photos/" + photoId + "/thumb";
    }

    public record AlbumMemberVo(Long userId, String userName, String userAvatar, String memberRole) {

        static AlbumMemberVo of(AlbumMember member) {
            return new AlbumMemberVo(member.getUserId(), member.getUserName(),
                    member.getUserAvatar(), member.getMemberRole().name().toLowerCase());
        }
    }

    public record AlbumPhotoVo(
            Long id, Long photoId, int version, int position,
            String photoUrl, String thumbUrl,
            Integer picWidth, Integer picHeight, Double picScale,
            OffsetDateTime takenTime,
            String caption, String place, String weather, String photoComment, String music,
            boolean hasOverlay, String overlayUserName, String compositeUrl) {

        static AlbumPhotoVo of(AlbumPhoto ap) {
            return new AlbumPhotoVo(
                    ap.getId(), ap.getPhotoId(), ap.getVersion(), ap.getPosition(),
                    // The stored columns hold disk paths. What goes over the
                    // wire is a URL the browser can put in an <img src>; the
                    // storage layout is nobody else's business.
                    //
                    // Qualified because the record's own accessors are also
                    // called photoUrl() and thumbUrl(), and they win otherwise.
                    AlbumVos.photoUrl(ap.getPhotoId()), AlbumVos.thumbUrl(ap.getPhotoId()),
                    ap.getPicWidth(), ap.getPicHeight(), ap.getPicScale(),
                    Times.toOffset(ap.getTakenTime()),
                    ap.getCaption(), ap.getPlace(), ap.getWeather(),
                    ap.getPhotoComment(), ap.getMusic(),
                    ap.getOverlayData() != null, ap.getOverlayUserName(),
                    compositeUrl(ap));
        }

        /**
         * Null until something has actually been drawn, so the client can fall
         * back to {@code photoUrl} rather than requesting an image that would
         * just be the original.
         *
         * <p>The {@code t} parameter busts the cache. There is no revision
         * counter on the row, so the last edit's timestamp stands in.
         */
        private static String compositeUrl(AlbumPhoto ap) {
            if (ap.getCompositePath() == null) {
                return null;
            }
            long stamp = ap.getOverlayUpdateTime() == null
                    ? 0
                    : ap.getOverlayUpdateTime().atZone(ZoneId.systemDefault()).toEpochSecond();
            return "/api/albums/%d/photos/%d/composite?t=%d".formatted(
                    ap.getAlbumId(), ap.getId(), stamp);
        }
    }

    public record AlbumVo(
            Long id, int version, String title, String summary,
            LocalDate albumDate, String place,
            /** 1 or 0, not true/false: the frontend types it as `0 | 1`. */
            int aiGenerated, String aiModel,
            Long coverPhotoId, String coverThumbUrl,
            Long userId, String userName,
            int photoNum, int memberNum, int viewNum,
            String shareToken, String myRole,
            List<AlbumMemberVo> members, List<AlbumPhotoVo> photos) {

        public static AlbumVo of(Album album, String myRole,
                                 List<AlbumMember> members, List<AlbumPhoto> photos) {
            return new AlbumVo(
                    album.getId(), album.getVersion(), album.getTitle(), album.getSummary(),
                    album.getAlbumDate(), album.getPlace(),
                    album.isAiGenerated() ? 1 : 0, album.getAiModel(),
                    album.getCoverPhotoId(), thumbUrl(album.getCoverPhotoId()),
                    album.getUserId(), album.getUserName(),
                    album.getPhotoNum(), album.getMemberNum(), album.getViewNum(),
                    album.getShareToken(), myRole,
                    members.stream().map(AlbumMemberVo::of).toList(),
                    photos.stream().map(AlbumPhotoVo::of).toList());
        }
    }
}
