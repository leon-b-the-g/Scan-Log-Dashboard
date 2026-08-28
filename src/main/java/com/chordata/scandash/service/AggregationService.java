package com.chordata.scandash.service;

import com.chordata.scandash.model.AggregationResult;
import com.chordata.scandash.model.DayPerformanceSummary;
import com.chordata.scandash.model.ScanEvent;
import com.chordata.scandash.model.School;
import com.chordata.scandash.model.SchoolDayRecord;
import com.chordata.scandash.model.SchoolPerformanceSummary;
import com.chordata.scandash.model.WeekdayAggregation;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Aggregates per-school day data over a date range into school summaries,
 * day summaries and weekday totals.
 *
 * <p>Two paths mirror the original module:</p>
 * <ul>
 *   <li>{@link #aggregateStored} — reads pre-aggregated day records
 *       (the fast "database" path).</li>
 *   <li>{@link #aggregateLive} — pulls order counts and the raw scan event
 *       stream from the backend APIs, de-duplicates scans per student and day,
 *       separates free-pass handouts, and normalizes inconsistent menu tokens
 *       before counting (the slow "live" path).</li>
 * </ul>
 */
public class AggregationService {

    /** Callback for per-school progress updates while an analysis runs. */
    @FunctionalInterface
    public interface ProgressListener {
        void update(int current, int total, String message);
    }

    private final SyntheticDataService dataService;

    public AggregationService(SyntheticDataService dataService) {
        this.dataService = dataService;
    }

    /** Fast path: aggregate from stored (pre-aggregated) day records. */
    public AggregationResult aggregateStored(List<School> schools, LocalDate from, LocalDate to,
                                             ProgressListener progress) {
        Map<LocalDate, DayAccumulator> dayAccumulators = initWeekdayAccumulators(from, to);
        WeekdayAggregation weekdayAgg = new WeekdayAggregation();
        List<SchoolPerformanceSummary> schoolSummaries = new ArrayList<>();
        Map<String, Map<LocalDate, SchoolDayRecord>> rawRecords = new LinkedHashMap<>();

        int processed = 0;
        for (School school : schools) {
            progress.update(++processed, schools.size(), school.name());

            Map<LocalDate, SchoolDayRecord> records = dataService.fetchStoredDayRecords(school, from, to);
            accumulateSchool(school, records, dayAccumulators, weekdayAgg, rawRecords, schoolSummaries);
        }

        return new AggregationResult(schoolSummaries, buildDaySummaries(dayAccumulators), weekdayAgg, rawRecords);
    }

    /** Live path: rebuild day records from the order API and the raw scan log. */
    public AggregationResult aggregateLive(List<School> schools, LocalDate from, LocalDate to,
                                           ProgressListener progress) {
        Map<LocalDate, DayAccumulator> dayAccumulators = initWeekdayAccumulators(from, to);
        WeekdayAggregation weekdayAgg = new WeekdayAggregation();
        List<SchoolPerformanceSummary> schoolSummaries = new ArrayList<>();
        Map<String, Map<LocalDate, SchoolDayRecord>> rawRecords = new LinkedHashMap<>();

        int processed = 0;
        for (School school : schools) {
            progress.update(++processed, schools.size(), school.name());

            Map<LocalDate, int[]> orderedByDate = dataService.fetchOrderCounts(school, from, to);
            Map<LocalDate, ScanTally> scannedByDate =
                    tallyScanEvents(dataService.fetchScanEvents(school, from, to));

            Set<LocalDate> allDates = new TreeSet<>();
            allDates.addAll(orderedByDate.keySet());
            allDates.addAll(scannedByDate.keySet());

            int studentCount = dataService.fetchStudentCount(school.id());
            Map<LocalDate, SchoolDayRecord> records = new TreeMap<>();
            for (LocalDate date : allDates) {
                DayOfWeek dow = date.getDayOfWeek();
                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    continue;
                }
                int[] ordered = orderedByDate.getOrDefault(date, new int[3]);
                ScanTally tally = scannedByDate.getOrDefault(date, new ScanTally());
                records.put(date, new SchoolDayRecord(school.id(), school.name(), date,
                        ordered[0], ordered[1], ordered[2],
                        tally.m1, tally.m2, tally.m3,
                        studentCount));
            }

            accumulateSchool(school, records, dayAccumulators, weekdayAgg, rawRecords, schoolSummaries);
        }

        return new AggregationResult(schoolSummaries, buildDaySummaries(dayAccumulators), weekdayAgg, rawRecords);
    }

    /**
     * Reduces the raw scan event stream to per-day counts. Duplicate scans of
     * the same student on the same day are dropped, free-pass handouts are
     * counted separately, and menu tokens are normalized before counting.
     */
    Map<LocalDate, ScanTally> tallyScanEvents(List<ScanEvent> events) {
        Map<LocalDate, ScanTally> result = new TreeMap<>();
        Map<LocalDate, Set<String>> seenPerDate = new HashMap<>();

        for (ScanEvent event : events) {
            if (event.scanTimestamp() == null) {
                continue;
            }
            LocalDate day = event.scanTimestamp().toLocalDate();
            DayOfWeek dow = day.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                continue;
            }

            ScanTally tally = result.computeIfAbsent(day, d -> new ScanTally());
            Set<String> seen = seenPerDate.computeIfAbsent(day, d -> new HashSet<>());
            String studentId = event.studentId() != null ? event.studentId().trim() : "";

            if (ScanEvent.FREE_PASS_ID.equalsIgnoreCase(studentId)) {
                tally.handouts++;
                continue;
            }
            if (!studentId.isBlank() && !seen.add(studentId)) {
                tally.duplicates++;
                continue;
            }
            if (event.scannedWithCard()) {
                tally.byCard++;
            }
            switch (normalizeMenuToken(event.menuToken())) {
                case "M1" -> tally.m1++;
                case "M2" -> tally.m2++;
                case "M3" -> tally.m3++;
                default -> { /* unknown token: not counted */ }
            }
        }
        return result;
    }

    /**
     * Normalizes the many spellings the scan devices deliver ("Menu 1",
     * "menü1", "M2", "Special Veggie", ...) to a canonical M1/M2/M3 token.
     * Returns an empty string for unrecognizable tokens.
     */
    static String normalizeMenuToken(String token) {
        if (token == null) {
            return "";
        }
        String norm = Normalizer.normalize(token, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replace(" ", "")
                .trim();
        return switch (norm) {
            case "m1", "menu1", "meal1", "special", "specialmeat" -> "M1";
            case "m2", "menu2", "meal2", "specialveggie" -> "M2";
            case "m3", "menu3", "meal3" -> "M3";
            default -> "";
        };
    }

    // ---------------------------------------------------------------------
    // Shared accumulation
    // ---------------------------------------------------------------------

    private void accumulateSchool(School school,
                                  Map<LocalDate, SchoolDayRecord> records,
                                  Map<LocalDate, DayAccumulator> dayAccumulators,
                                  WeekdayAggregation weekdayAgg,
                                  Map<String, Map<LocalDate, SchoolDayRecord>> rawRecords,
                                  List<SchoolPerformanceSummary> schoolSummaries) {
        int studentCount = dataService.fetchStudentCount(school.id());
        int totalOrdered = 0;
        int totalScanned = 0;
        int daysWithData = 0;
        Map<LocalDate, SchoolDayRecord> keptRecords = new TreeMap<>();

        for (Map.Entry<LocalDate, SchoolDayRecord> entry : records.entrySet()) {
            LocalDate date = entry.getKey();
            SchoolDayRecord record = entry.getValue();
            int ordered = record.totalOrdered();
            int scanned = record.totalScanned();

            if (ordered > 0 || scanned > 0) {
                keptRecords.put(date, record);
                weekdayAgg.addData(date.getDayOfWeek(), scanned, ordered, studentCount);
                daysWithData++;
            }
            totalOrdered += ordered;
            totalScanned += scanned;

            DayAccumulator acc = dayAccumulators.get(date);
            if (acc != null) {
                acc.add(ordered, scanned, studentCount);
            }
        }

        if (!keptRecords.isEmpty()) {
            rawRecords.put(school.id(), keptRecords);
        }
        if (daysWithData > 0) {
            schoolSummaries.add(new SchoolPerformanceSummary(school.id(), school.name(),
                    totalOrdered, totalScanned, studentCount, daysWithData));
        }
    }

    private static Map<LocalDate, DayAccumulator> initWeekdayAccumulators(LocalDate from, LocalDate to) {
        Map<LocalDate, DayAccumulator> accumulators = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                accumulators.put(date, new DayAccumulator());
            }
        }
        return accumulators;
    }

    private static List<DayPerformanceSummary> buildDaySummaries(Map<LocalDate, DayAccumulator> accumulators) {
        List<DayPerformanceSummary> summaries = new ArrayList<>();
        for (Map.Entry<LocalDate, DayAccumulator> entry : accumulators.entrySet()) {
            DayAccumulator acc = entry.getValue();
            if (acc.schoolCount > 0) {
                summaries.add(new DayPerformanceSummary(entry.getKey(),
                        acc.totalOrdered, acc.totalScanned, acc.totalStudents, acc.schoolCount));
            }
        }
        return summaries;
    }

    private static class DayAccumulator {
        int totalOrdered;
        int totalScanned;
        int totalStudents;
        int schoolCount;

        void add(int ordered, int scanned, int students) {
            totalOrdered += ordered;
            totalScanned += scanned;
            totalStudents += students;
            if (ordered > 0 || scanned > 0) {
                schoolCount++;
            }
        }
    }

    /** Per-day scan counts extracted from the raw event stream. */
    static class ScanTally {
        int m1;
        int m2;
        int m3;
        int handouts;
        int duplicates;
        int byCard;
    }
}
