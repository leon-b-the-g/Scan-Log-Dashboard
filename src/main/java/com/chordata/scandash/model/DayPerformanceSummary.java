package com.chordata.scandash.model;

import java.time.LocalDate;

/**
 * Performance across all selected schools for one service day.
 *
 * <p>Rates:</p>
 * <ul>
 *   <li><b>Order rate</b> — participation: {@code totalOrdered / totalStudents * 100}</li>
 *   <li><b>Scan rate</b> — compliance: {@code totalScanned / totalOrdered * 100}</li>
 * </ul>
 */
public class DayPerformanceSummary {

    private final LocalDate date;
    private final int totalOrdered;
    private final int totalScanned;
    private final int totalStudents;
    private final int schoolsWithData;
    private final double orderRate;
    private final double scanRate;

    public DayPerformanceSummary(LocalDate date, int totalOrdered, int totalScanned,
                                 int totalStudents, int schoolsWithData) {
        this.date = date;
        this.totalOrdered = totalOrdered;
        this.totalScanned = totalScanned;
        this.totalStudents = totalStudents;
        this.schoolsWithData = schoolsWithData;

        this.orderRate = totalStudents > 0 ? (totalOrdered * 100.0) / totalStudents : 0.0;
        this.scanRate = totalOrdered > 0 ? (totalScanned * 100.0) / totalOrdered : 0.0;
    }

    public LocalDate getDate() { return date; }
    public int getTotalOrdered() { return totalOrdered; }
    public int getTotalScanned() { return totalScanned; }
    public int getTotalStudents() { return totalStudents; }
    public int getSchoolsWithData() { return schoolsWithData; }
    public double getOrderRate() { return orderRate; }
    public double getScanRate() { return scanRate; }
}
