package com.hanamizuki.backend.api.share;

import java.time.LocalDate;
import java.util.List;

/**
 * Shapes for sharing.
 *
 * <p>{@link PublicAlbumVo} is deliberately its own type rather than a filtered
 * copy of the internal album view. Reusing the internal one and remembering to
 * strip member ids, account names, photo ids and version numbers works right
 * up until somebody adds a field.
 */
public final class ShareVos {

    private ShareVos() {
    }

    /** {@code shareUrl} is absolute and built from the request host. */
    public record ShareLinkVo(String shareToken, String shareUrl, int viewCount) {
    }

    public record PublicPhotoVo(String imageUrl, String caption) {
    }

    public record PublicAlbumVo(String title, String summary, LocalDate albumDate,
                                String place, String ownerName, int photoNum,
                                List<PublicPhotoVo> photos) {
    }
}
