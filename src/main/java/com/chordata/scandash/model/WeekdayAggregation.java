package com.chordata.scandash.model;

import java.time.DayOfWeek;

/**
 * Totals per weekday (Monday..Friday) across the analyzed range,
 * used to spot structural weekday effects (e.g. weak Fridays).
 */
public class WeekdayAggregation {

    public static final String[] WEEKDAY_LABELS = {"Mon", "Tue", "Wed", "Thu", "Fri"};

    // Index: 0=Mon .. 4=Fri
    private final int[] totalScans = new int[5];
    private final int[] totalOrders = new int[5];
    private final int[] totalStudents = new int[5];
    private final int[] dayCount = new int[5];

    public void addData(DayOfWeek dow, int scans, int orders, int students) {
        int idx = dow.getValue() - 1;
        if (idx >= 0 && idx < 5) {
            totalScans[idx] += scans;
            totalOrders[idx] += orders;
            totalStudents[idx] += students;
            dayCount[idx]++;
        }
    }

    public int getTotalScans(int idx) { return totalScans[idx]; }
    public int getTotalOrders(int idx) { return totalOrders[idx]; }
    public int getDayCount(int idx) { return dayCount[idx]; }

    public double getScanRate(int idx) {
        return totalOrders[idx] > 0 ? (totalScans[idx] * 100.0) / totalOrders[idx] : 0;
    }

    public double getOrderRate(int idx) {
        return totalStudents[idx] > 0 ? (totalOrders[idx] * 100.0) / totalStudents[idx] : 0;
    }

    /** Average scans per instance of this weekday. */
    public double getAverageScans(int idx) {
        return dayCount[idx] > 0 ? (double) totalScans[idx] / dayCount[idx] : 0;
    }

    /** Average orders per instance of this weekday. */
    public double getAverageOrders(int idx) {
        return dayCount[idx] > 0 ? (double) totalOrders[idx] / dayCount[idx] : 0;
    }
}
