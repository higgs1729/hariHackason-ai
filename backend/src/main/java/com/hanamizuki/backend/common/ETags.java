package com.hanamizuki.backend.common;

/**
 * Optimistic-lock version numbers on the wire.
 *
 * <p>ETag と If-Match の変換。/ 乐观锁版本号与 ETag 的互转。
 *
 * <p>Extracted once there was a second route doing this. Both albums and
 * decorations put the row's {@code @Version} in the ETag and expect it back as
 * {@code If-Match}, and two copies of the parsing would be two chances for one
 * of them to accept a header the other rejects.
 */
public final class ETags {

    private ETags() {
    }

    /** Quoted, weak, or bare — all three arrive in practice. */
    public static String of(int version) {
        return "\"" + version + "\"";
    }

    /**
     * @return null when the header is absent or unparseable, which the caller
     *         reports as 428 rather than treating as "no opinion"
     */
    public static Integer parse(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(header.replace("W/", "").replace("\"", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
