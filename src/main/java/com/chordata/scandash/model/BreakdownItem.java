package com.chordata.scandash.model;

/**
 * A single row in the drill-down panel: either one day of a selected school,
 * or one school on a selected day.
 */
public class BreakdownItem {

    private final String label;
    private final int ordered;
    private final int scanned;
    private final int students;
    private final double orderRate;
    private final double scanRate;

    public BreakdownItem(String label, int ordered, int scanned, int students) {
        this.label = label;
        this.ordered = ordered;
        this.scanned = scanned;
        this.students = students;
        this.orderRate = students > 0 ? (ordered * 100.0) / students : 0.0;
        this.scanRate = ordered > 0 ? (scanned * 100.0) / ordered : 0.0;
    }

    public String getLabel() { return label; }
    public int getOrdered() { return ordered; }
    public int getScanned() { return scanned; }
    public int getStudents() { return students; }
    public double getOrderRate() { return orderRate; }
    public double getScanRate() { return scanRate; }
}
