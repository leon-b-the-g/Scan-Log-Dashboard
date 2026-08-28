package com.chordata.scandash.model;

import java.time.LocalDateTime;

/**
 * A raw checkout scan event as delivered by the scan log backend.
 *
 * <p>The stream is intentionally noisy, mirroring real-world conditions:
 * students may be scanned twice (duplicates must be de-duplicated per day),
 * kitchen staff hand out free passes under the reserved id {@code FREE-0},
 * and the menu token arrives in inconsistent spellings ("Menu 1", "M2",
 * "menü 3", ...) that must be normalized before counting.</p>
 */
public record ScanEvent(
        String schoolId,
        LocalDateTime scanTimestamp,
        String studentId,
        String menuToken,
        boolean scannedWithCard) {

    /** Reserved student id used when staff hand out a meal without an order (free pass). */
    public static final String FREE_PASS_ID = "FREE-0";
}
