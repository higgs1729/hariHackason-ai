package com.hanamizuki.backend.api.capsule;

import java.time.OffsetDateTime;

import com.hanamizuki.backend.api.album.AlbumVos.AlbumVo;

/**
 * Sealed and opened are separate types, not one type with nullable fields.
 *
 * <p>The rule is that a sealed capsule must not disclose its message or its
 * album. Expressed as {@code capsuleMsg = null} on a shared type, that rule
 * holds only as long as every code path remembers it. Expressed as a type with
 * nowhere to put the message, it cannot be broken by accident.
 */
public final class CapsuleVos {

    private CapsuleVos() {
    }

    public sealed interface CapsuleVo permits CapsuleSealedVo, CapsuleOpenedVo {
    }

    /** Everything a countdown needs, and nothing more. */
    public record CapsuleSealedVo(Long id, String status, OffsetDateTime openTime,
                                  long daysRemaining) implements CapsuleVo {
    }

    public record CapsuleOpenedVo(Long id, String status, OffsetDateTime openTime,
                                  OffsetDateTime openedTime, String capsuleMsg,
                                  AlbumVo album) implements CapsuleVo {
    }

    public record CreateCapsuleRequest(Long albumId, OffsetDateTime openTime, String capsuleMsg) {
    }
}
