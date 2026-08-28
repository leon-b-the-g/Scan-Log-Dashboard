package com.chordata.scandash.model;

import java.time.LocalDate;

/**
 * One school's aggregated order/scan counts for a single service day,
 * broken down by the three menu lines (M1, M2, M3).
 */
public record SchoolDayRecord(
        String schoolId,
        String schoolName,
        LocalDate date,
        int m1Ordered, int m2Ordered, int m3Ordered,
        int m1Scanned, int m2Scanned, int m3Scanned,
        int studentCount) {

    public int totalOrdered() {
        return m1Ordered + m2Ordered + m3Ordered;
    }

    public int totalScanned() {
        return m1Scanned + m2Scanned + m3Scanned;
    }
}
