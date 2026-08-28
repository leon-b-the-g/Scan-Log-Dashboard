package com.chordata.scandash.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything one analysis run produces: per-school summaries, per-day summaries,
 * weekday totals, and the raw per-school/per-day records backing the drill-down.
 */
public record AggregationResult(
        List<SchoolPerformanceSummary> schoolSummaries,
        List<DayPerformanceSummary> daySummaries,
        WeekdayAggregation weekdayAggregation,
        Map<String, Map<LocalDate, SchoolDayRecord>> rawRecords) {
}
