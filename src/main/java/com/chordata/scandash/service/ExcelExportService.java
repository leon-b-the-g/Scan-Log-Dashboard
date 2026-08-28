package com.chordata.scandash.service;

import com.chordata.scandash.model.AggregationResult;
import com.chordata.scandash.model.DayPerformanceSummary;
import com.chordata.scandash.model.SchoolDayRecord;
import com.chordata.scandash.model.SchoolPerformanceSummary;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Writes an analysis run to a three-sheet Excel workbook:
 * school overview, day performance, and the full per-school/per-day details.
 */
public class ExcelExportService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void export(File file, AggregationResult result, LocalDate from, LocalDate to) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream out = new FileOutputStream(file)) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            createSchoolOverviewSheet(workbook, headerStyle, result.schoolSummaries(), from, to);
            createDayPerformanceSheet(workbook, headerStyle, result.daySummaries(), from, to);
            createFullDetailsSheet(workbook, headerStyle, result.rawRecords(), from, to);
            workbook.write(out);
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void createSchoolOverviewSheet(XSSFWorkbook workbook, CellStyle headerStyle,
                                           List<SchoolPerformanceSummary> summaries,
                                           LocalDate from, LocalDate to) {
        XSSFSheet sheet = workbook.createSheet("School Overview");
        int rowNum = 0;

        sheet.createRow(rowNum++).createCell(0)
                .setCellValue("School Overview — " + rangeLabel(from, to));
        rowNum++;

        Row header = sheet.createRow(rowNum++);
        String[] columns = {"School", "Site ID", "Order Rate (%)", "Scan Rate (%)",
                "Total Scans", "Total Orders", "Students", "Days Analyzed"};
        for (int i = 0; i < columns.length; i++) {
            var cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        for (SchoolPerformanceSummary school : summaries) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(school.getSchoolName());
            row.createCell(1).setCellValue(school.getSchoolId());
            row.createCell(2).setCellValue(round1(school.getOrderRate()));
            row.createCell(3).setCellValue(round1(school.getScanRate()));
            row.createCell(4).setCellValue(school.getTotalScanned());
            row.createCell(5).setCellValue(school.getTotalOrdered());
            row.createCell(6).setCellValue(school.getTotalStudents());
            row.createCell(7).setCellValue(school.getDaysAnalyzed());
        }
        autoSize(sheet, columns.length);
    }

    private void createDayPerformanceSheet(XSSFWorkbook workbook, CellStyle headerStyle,
                                           List<DayPerformanceSummary> summaries,
                                           LocalDate from, LocalDate to) {
        XSSFSheet sheet = workbook.createSheet("Day Performance");
        int rowNum = 0;

        sheet.createRow(rowNum++).createCell(0)
                .setCellValue("Day Performance — " + rangeLabel(from, to));
        rowNum++;

        Row header = sheet.createRow(rowNum++);
        String[] columns = {"Date", "Order Rate (%)", "Scan Rate (%)",
                "Total Scans", "Total Orders", "Students", "Schools With Data"};
        for (int i = 0; i < columns.length; i++) {
            var cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        for (DayPerformanceSummary day : summaries) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(day.getDate().format(DISPLAY_DATE));
            row.createCell(1).setCellValue(round1(day.getOrderRate()));
            row.createCell(2).setCellValue(round1(day.getScanRate()));
            row.createCell(3).setCellValue(day.getTotalScanned());
            row.createCell(4).setCellValue(day.getTotalOrdered());
            row.createCell(5).setCellValue(day.getTotalStudents());
            row.createCell(6).setCellValue(day.getSchoolsWithData());
        }
        autoSize(sheet, columns.length);
    }

    private void createFullDetailsSheet(XSSFWorkbook workbook, CellStyle headerStyle,
                                        Map<String, Map<LocalDate, SchoolDayRecord>> rawRecords,
                                        LocalDate from, LocalDate to) {
        XSSFSheet sheet = workbook.createSheet("Full Details");
        int rowNum = 0;

        sheet.createRow(rowNum++).createCell(0)
                .setCellValue("Full Details — " + rangeLabel(from, to));
        rowNum++;

        Row header = sheet.createRow(rowNum++);
        String[] columns = {"School", "Date",
                "M1 Ordered", "M2 Ordered", "M3 Ordered",
                "M1 Scanned", "M2 Scanned", "M3 Scanned",
                "Total Ordered", "Total Scanned", "Students",
                "Order Rate (%)", "Scan Rate (%)"};
        for (int i = 0; i < columns.length; i++) {
            var cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        for (Map<LocalDate, SchoolDayRecord> dateRecords : rawRecords.values()) {
            List<LocalDate> sortedDates = new ArrayList<>(dateRecords.keySet());
            Collections.sort(sortedDates);
            for (LocalDate date : sortedDates) {
                SchoolDayRecord record = dateRecords.get(date);
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(record.schoolName());
                row.createCell(1).setCellValue(date.format(DISPLAY_DATE));
                row.createCell(2).setCellValue(record.m1Ordered());
                row.createCell(3).setCellValue(record.m2Ordered());
                row.createCell(4).setCellValue(record.m3Ordered());
                row.createCell(5).setCellValue(record.m1Scanned());
                row.createCell(6).setCellValue(record.m2Scanned());
                row.createCell(7).setCellValue(record.m3Scanned());
                row.createCell(8).setCellValue(record.totalOrdered());
                row.createCell(9).setCellValue(record.totalScanned());
                row.createCell(10).setCellValue(record.studentCount());

                double orderRate = record.studentCount() > 0
                        ? (record.totalOrdered() * 100.0) / record.studentCount() : 0.0;
                double scanRate = record.totalOrdered() > 0
                        ? (record.totalScanned() * 100.0) / record.totalOrdered() : 0.0;
                row.createCell(11).setCellValue(round1(orderRate));
                row.createCell(12).setCellValue(round1(scanRate));
            }
        }
        autoSize(sheet, columns.length);
    }

    private static String rangeLabel(LocalDate from, LocalDate to) {
        return (from != null && to != null)
                ? from.format(DISPLAY_DATE) + " to " + to.format(DISPLAY_DATE)
                : "";
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static void autoSize(XSSFSheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
