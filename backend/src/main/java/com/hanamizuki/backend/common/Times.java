package com.hanamizuki.backend.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * MySQL {@code datetime} carries no zone, so entities hold {@link LocalDateTime}.
 * The wire format does carry one — the frontend types every timestamp as
 * ISO-8601 with an offset — so responses convert on the way out.
 *
 * <p>Truncated to seconds: the microseconds a database hands back are noise in
 * a payload, and they make two otherwise equal timestamps compare unequal.
 */
public final class Times {

    private Times() {
    }

    public static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null
                ? null
                : value.truncatedTo(ChronoUnit.SECONDS)
                       .atZone(ZoneId.systemDefault())
                       .toOffsetDateTime();
    }
}
