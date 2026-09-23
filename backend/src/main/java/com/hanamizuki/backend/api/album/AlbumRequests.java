package com.hanamizuki.backend.api.album;

/**
 * Write bodies for /api/albums, matching {@code frontend/src/api/types.ts}.
 *
 * <p>Every field is a partial: null means "leave it alone", not "clear it".
 * The two are indistinguishable in JSON without a wrapper type, and at this
 * size an explicit clear is not a feature anyone has asked for — the
 * alternative would let a client that omits a field wipe an AI-written caption.
 */
public final class AlbumRequests {

    private AlbumRequests() {
    }

    /** {@code AlbumPatch}: what the detail screen lets you edit on the album. */
    public record AlbumPatchRequest(String title, Long coverPhotoId, String summary) {
    }

    /** {@code AlbumPhotoPatch}: the per-photo caption card. */
    public record AlbumPhotoPatchRequest(String caption, String place, String weather,
                                         String photoComment, String music) {
    }

    /** Invited by user id — the picker resolves a name to an id first. */
    public record AddMemberRequest(Long userId) {
    }
}
