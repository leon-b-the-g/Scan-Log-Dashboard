package com.chordata.scandash.model;

/**
 * Summary of a school's performance over an analyzed date range.
 *
 * <p>Rates:</p>
 * <ul>
 *   <li><b>Order rate</b> — average daily participation:
 *       {@code (totalOrdered / daysAnalyzed) / totalStudents * 100}</li>
 *   <li><b>Scan rate</b> — scan compliance:
 *       {@code totalScanned / totalOrdered * 100}</li>
 * </ul>
 */
public class SchoolPerformanceSummary {

    private final String schoolId;
    private final String schoolName;
    private final int totalOrdered;
    private final int totalScanned;
    private final int totalStudents;
    private final int daysAnalyzed;
    private final double orderRate;
    private final double scanRate;

    public SchoolPerformanceSummary(String schoolId, String schoolName,
                                    int totalOrdered, int totalScanned,
                                    int totalStudents, int daysAnalyzed) {
        this.schoolId = schoolId;
        this.schoolName = schoolName;
        this.totalOrdered = totalOrdered;
        this.totalScanned = totalScanned;
        this.totalStudents = totalStudents;
        this.daysAnalyzed = daysAnalyzed;

        this.orderRate = (totalStudents > 0 && daysAnalyzed > 0)
                ? (totalOrdered * 100.0) / ((long) totalStudents * daysAnalyzed)
                : 0.0;
        this.scanRate = totalOrdered > 0
                ? (totalScanned * 100.0) / totalOrdered
                : 0.0;
    }

    public String getSchoolId() { return schoolId; }
    public String getSchoolName() { return schoolName; }
    public int getTotalOrdered() { return totalOrdered; }
    public int getTotalScanned() { return totalScanned; }
    public int getTotalStudents() { return totalStudents; }
    public int getDaysAnalyzed() { return daysAnalyzed; }
    public double getOrderRate() { return orderRate; }
    public double getScanRate() { return scanRate; }

    /** Average scans per analyzed day. */
    public double getAverageScanned() {
        return daysAnalyzed > 0 ? (double) totalScanned / daysAnalyzed : 0.0;
    }

    /** Average orders per analyzed day. */
    public double getAverageOrdered() {
        return daysAnalyzed > 0 ? (double) totalOrdered / daysAnalyzed : 0.0;
    }
}
