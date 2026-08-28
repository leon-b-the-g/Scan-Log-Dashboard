package com.chordata.scandash.service;

import com.chordata.scandash.model.ScanEvent;
import com.chordata.scandash.model.School;
import com.chordata.scandash.model.SchoolDayRecord;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Deterministic synthetic data source standing in for the ERP backend
 * (database, order API and scan log API of the original system).
 *
 * <p>Every value is derived from seeded {@link Random} instances keyed by
 * school and date, so repeated analyses over the same range always return
 * identical numbers — just like a real datastore would.</p>
 *
 * <p>The generator models realistic behavior: per-school participation and
 * compliance baselines, weekday effects (weak Fridays), occasional scanner
 * outages, shared holidays, and a noisy raw scan event stream containing
 * duplicate scans, free-pass handouts and inconsistent menu tokens.</p>
 */
public class SyntheticDataService {

    /** Simulated latency per school when reading pre-aggregated records (ms). */
    private static final long STORED_FETCH_LATENCY_MS = 25;
    /** Simulated latency per school when fetching from the "live" APIs (ms). */
    private static final long LIVE_FETCH_LATENCY_MS = 110;

    private static final String[] SCHOOL_NAMES = {
            "Riverside Primary", "Northgate Elementary", "Oakfield School",
            "Maple Grove Academy", "Lakeview Primary", "Birchwood School",
            "Elm Street Elementary", "Hillcrest Academy", "Meadowbrook Primary",
            "Cedar Park School", "Stonebridge Elementary", "Willow Lane Primary",
            "Foxglove Academy", "Harborview School", "Kingsfield Primary",
            "Larchmont Elementary", "Aspen Ridge School", "Brookside Primary",
            "Chestnut Hill Academy", "Silverbirch School", "Rosewood Primary",
            "Falcon Heights Elementary", "Greenfield Academy", "Ivy Court School",
            "Juniper Vale Primary", "Kestrel Park School", "Linden Grove Elementary",
            "Mulberry Lane Academy", "Nightingale Primary", "Orchard Way School",
            "Pinecrest Elementary", "Quarry Bank Academy"
    };

    /** Global weekday demand factors, Monday..Friday. Fridays are structurally weak. */
    private static final double[] WEEKDAY_FACTOR = {1.00, 1.03, 0.99, 0.96, 0.84};

    /** Menu token spellings as they actually arrive from the scan devices. */
    private static final String[][] MENU_TOKEN_VARIANTS = {
            {"M1", "Menu 1", "menu1", "Menü 1"},
            {"M2", "Menu 2", "menu2", "Menü 2", "Special Veggie"},
            {"M3", "Menu 3", "menu3", "Menü 3"}
    };

    private record SchoolProfile(int studentCount, double baseParticipation,
                                 double baseCompliance, double m1Share, double m2Share) {
    }

    private final Map<String, SchoolProfile> profiles = new LinkedHashMap<>();
    private final List<School> schools = new ArrayList<>();

    public SyntheticDataService() {
        Random seed = new Random(424242L);
        for (int i = 0; i < SCHOOL_NAMES.length; i++) {
            String id = String.format("SITE-%04d", 1000 + i * 7 + seed.nextInt(5));
            schools.add(new School(id, SCHOOL_NAMES[i]));
            profiles.put(id, new SchoolProfile(
                    80 + seed.nextInt(570),
                    0.35 + seed.nextDouble() * 0.50,
                    0.72 + seed.nextDouble() * 0.26,
                    0.45 + seed.nextDouble() * 0.20,
                    0.20 + seed.nextDouble() * 0.15));
        }
        schools.sort(Comparator.comparing(School::name));
    }

    /** All active sites, alphabetically. */
    public List<School> loadSchools() {
        return List.copyOf(schools);
    }

    /** Number of students with a valid meal contract at this site. */
    public int fetchStudentCount(String schoolId) {
        SchoolProfile p = profiles.get(schoolId);
        return p != null ? p.studentCount() : 0;
    }

    /**
     * Reads pre-aggregated day records for a school — the fast "database" path.
     * Weekend dates and holidays yield no records.
     */
    public Map<LocalDate, SchoolDayRecord> fetchStoredDayRecords(School school, LocalDate from, LocalDate to) {
        simulateLatency(STORED_FETCH_LATENCY_MS);
        Map<LocalDate, SchoolDayRecord> result = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            SchoolDayRecord record = computeDayRecord(school, date);
            if (record != null && (record.totalOrdered() > 0 || record.totalScanned() > 0)) {
                result.put(date, record);
            }
        }
        return result;
    }

    /**
     * Fetches daily order counts per menu line from the "order API" — the live path.
     * Returned as {m1, m2, m3} triples keyed by date.
     */
    public Map<LocalDate, int[]> fetchOrderCounts(School school, LocalDate from, LocalDate to) {
        simulateLatency(LIVE_FETCH_LATENCY_MS / 2);
        Map<LocalDate, int[]> result = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            SchoolDayRecord record = computeDayRecord(school, date);
            if (record != null && record.totalOrdered() > 0) {
                result.put(date, new int[]{record.m1Ordered(), record.m2Ordered(), record.m3Ordered()});
            }
        }
        return result;
    }

    /**
     * Fetches the raw scan event stream from the "scan log API" — the live path.
     * The stream is noisy on purpose: it contains duplicate scans (~2%), free-pass
     * handouts, mixed menu token spellings and both card and manual scans.
     */
    public List<ScanEvent> fetchScanEvents(School school, LocalDate from, LocalDate to) {
        simulateLatency(LIVE_FETCH_LATENCY_MS / 2);
        List<ScanEvent> events = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            SchoolDayRecord record = computeDayRecord(school, date);
            if (record == null || record.totalScanned() == 0) {
                continue;
            }
            Random rnd = dayRandom(school.id(), date, 7);
            int studentSeq = 0;
            int[] scannedPerMenu = {record.m1Scanned(), record.m2Scanned(), record.m3Scanned()};
            List<ScanEvent> dayEvents = new ArrayList<>();
            for (int menu = 0; menu < 3; menu++) {
                for (int i = 0; i < scannedPerMenu[menu]; i++) {
                    String studentId = String.format("%s-S%04d", school.id(), ++studentSeq);
                    String token = MENU_TOKEN_VARIANTS[menu][rnd.nextInt(MENU_TOKEN_VARIANTS[menu].length)];
                    LocalDateTime ts = date.atTime(lunchTime(rnd));
                    boolean withCard = rnd.nextDouble() < 0.7;
                    dayEvents.add(new ScanEvent(school.id(), ts, studentId, token, withCard));

                    // ~2% of students get scanned a second time by accident
                    if (rnd.nextDouble() < 0.02) {
                        dayEvents.add(new ScanEvent(school.id(), ts.plusSeconds(20 + rnd.nextInt(300)),
                                studentId, token, withCard));
                    }
                }
            }
            // Occasional free-pass handouts by kitchen staff
            int handouts = rnd.nextInt(4);
            for (int i = 0; i < handouts; i++) {
                int menu = rnd.nextInt(3);
                dayEvents.add(new ScanEvent(school.id(), date.atTime(lunchTime(rnd)),
                        ScanEvent.FREE_PASS_ID,
                        MENU_TOKEN_VARIANTS[menu][rnd.nextInt(MENU_TOKEN_VARIANTS[menu].length)], false));
            }
            dayEvents.sort(Comparator.comparing(ScanEvent::scanTimestamp));
            events.addAll(dayEvents);
        }
        return events;
    }

    /**
     * The deterministic core: computes one school's true counts for one date.
     * Returns {@code null} for weekends and holidays.
     */
    private SchoolDayRecord computeDayRecord(School school, LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return null;
        }
        if (isHoliday(date)) {
            return null;
        }
        SchoolProfile profile = profiles.get(school.id());
        if (profile == null) {
            return null;
        }

        Random rnd = dayRandom(school.id(), date, 1);
        double participation = clamp(
                profile.baseParticipation() * WEEKDAY_FACTOR[dow.getValue() - 1]
                        + rnd.nextGaussian() * 0.05,
                0.05, 0.98);
        int ordered = (int) Math.round(profile.studentCount() * participation);

        int m1o = (int) Math.round(ordered * profile.m1Share());
        int m2o = (int) Math.round(ordered * profile.m2Share());
        int m3o = Math.max(0, ordered - m1o - m2o);

        double compliance = clamp(profile.baseCompliance() + rnd.nextGaussian() * 0.04, 0.0, 1.0);
        // ~4% of school days the scanner acts up and compliance collapses
        if (rnd.nextDouble() < 0.04) {
            compliance *= 0.35 + rnd.nextDouble() * 0.35;
        }

        int m1s = (int) Math.round(m1o * clamp(compliance + rnd.nextGaussian() * 0.02, 0, 1));
        int m2s = (int) Math.round(m2o * clamp(compliance + rnd.nextGaussian() * 0.02, 0, 1));
        int m3s = (int) Math.round(m3o * clamp(compliance + rnd.nextGaussian() * 0.02, 0, 1));

        return new SchoolDayRecord(school.id(), school.name(), date,
                m1o, m2o, m3o,
                Math.min(m1s, m1o), Math.min(m2s, m2o), Math.min(m3s, m3o),
                profile.studentCount());
    }

    /** Shared holidays: roughly one weekday in 25 has no service anywhere. */
    private boolean isHoliday(LocalDate date) {
        return new Random(date.toEpochDay() * 31L + 17L).nextDouble() < 0.04;
    }

    private static Random dayRandom(String schoolId, LocalDate date, int stream) {
        return new Random(schoolId.hashCode() * 1_000_003L + date.toEpochDay() * 97L + stream);
    }

    private static LocalTime lunchTime(Random rnd) {
        return LocalTime.of(11, 15).plusSeconds(rnd.nextInt(2 * 60 * 60 + 30 * 60));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
