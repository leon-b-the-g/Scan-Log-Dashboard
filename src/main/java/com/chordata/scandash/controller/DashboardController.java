package com.chordata.scandash.controller;

import com.chordata.scandash.model.AggregationResult;
import com.chordata.scandash.model.BreakdownItem;
import com.chordata.scandash.model.DayPerformanceSummary;
import com.chordata.scandash.model.School;
import com.chordata.scandash.model.SchoolDayRecord;
import com.chordata.scandash.model.SchoolPerformanceSummary;
import com.chordata.scandash.model.WeekdayAggregation;
import com.chordata.scandash.service.AggregationService;
import com.chordata.scandash.service.ExcelExportService;
import com.chordata.scandash.service.SyntheticDataService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Multi-site scan analytics dashboard: aggregated metrics, sortable
 * performance lists, drill-down breakdowns and weekday charts.
 */
public class DashboardController {

    // Header / toolbar
    @FXML private Label labelTitle;
    @FXML private Button buttonMonthBack;
    @FXML private Button buttonWeekBack;
    @FXML private DatePicker datePickerFrom;
    @FXML private DatePicker datePickerTo;
    @FXML private Button buttonWeekForward;
    @FXML private Button buttonMonthForward;
    @FXML private Button buttonAnalyze;
    @FXML private Button buttonExport;
    @FXML private CheckBox checkBoxLiveFetch;
    @FXML private ProgressIndicator progressSpinner;
    @FXML private ProgressBar progressBar;
    @FXML private Label labelProgress;

    // Sidebar (site selection)
    @FXML private TextField textFieldSchoolSearch;
    @FXML private Button buttonSelectAll;
    @FXML private Button buttonSelectNone;
    @FXML private ListView<SchoolCheckItem> listViewSchools;

    // School performance panel
    @FXML private ListView<SchoolPerformanceSummary> listViewSchoolPerformance;
    @FXML private ToggleButton toggleSchoolSortDirection;
    @FXML private ComboBox<String> comboSchoolSortBy;
    @FXML private Slider sliderDisplayLimit;
    @FXML private Label labelDisplayLimit;

    // Day performance panel
    @FXML private ListView<DayPerformanceSummary> listViewDayPerformance;
    @FXML private ToggleButton toggleDaySortDirection;
    @FXML private ComboBox<String> comboDaySortBy;

    // Drill-down panel
    @FXML private Label labelBreakdownTitle;
    @FXML private ListView<BreakdownItem> listViewBreakdown;

    // Weekday chart
    @FXML private VBox chartContainer;
    @FXML private ToggleButton toggleChartScans;
    @FXML private ToggleButton toggleChartOrders;
    @FXML private ToggleButton toggleChartScanRate;
    @FXML private ToggleButton toggleChartOrderRate;

    private static final String SORT_ALPHABETICAL = "Alphabetical";
    private static final String SORT_ORDER_RATE = "Order rate";
    private static final String SORT_SCAN_RATE = "Scan rate";
    private static final String SORT_DATE = "Date";

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy (EEE)");
    private static final DateTimeFormatter RANGE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final SyntheticDataService dataService = new SyntheticDataService();
    private final AggregationService aggregationService = new AggregationService(dataService);
    private final ExcelExportService exportService = new ExcelExportService();

    private final ObservableList<SchoolCheckItem> allSchools = FXCollections.observableArrayList();
    private final ObservableList<SchoolCheckItem> filteredSchools = FXCollections.observableArrayList();
    private final ObservableList<SchoolPerformanceSummary> displayedSchools = FXCollections.observableArrayList();
    private final ObservableList<DayPerformanceSummary> displayedDays = FXCollections.observableArrayList();
    private final ObservableList<BreakdownItem> breakdownItems = FXCollections.observableArrayList();

    private List<SchoolPerformanceSummary> allSchoolSummaries = new ArrayList<>();
    private List<DayPerformanceSummary> allDaySummaries = new ArrayList<>();
    private Map<String, Map<LocalDate, SchoolDayRecord>> rawRecords = Map.of();
    private WeekdayAggregation weekdayAggregation;
    private AggregationResult lastResult;

    private boolean schoolSortAscending = false;
    private boolean daySortAscending = false;
    private LocalDate currentAnalysisFrom;
    private LocalDate currentAnalysisTo;

    private BarChart<String, Number> weekdayChart;
    private Label chartSubtitle;

    /** A site plus its checkbox state in the selection sidebar. */
    public static class SchoolCheckItem {
        private final School school;
        private boolean selected = true;

        SchoolCheckItem(School school) {
            this.school = school;
        }

        public School getSchool() { return school; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
    }

    @FXML
    void initialize() {
        LocalDate today = LocalDate.now();
        applyIsoDateFormat(datePickerFrom);
        applyIsoDateFormat(datePickerTo);
        datePickerFrom.setValue(today.minusWeeks(4));
        datePickerTo.setValue(today);

        initializeSchoolSidebar();
        initializeSortControls();
        initializeCellFactories();
        initializeWeekdayChart();
        wireActions();

        progressSpinner.setVisible(false);
        progressBar.setVisible(false);
        labelProgress.setVisible(false);

        listViewSchoolPerformance.setItems(displayedSchools);
        listViewDayPerformance.setItems(displayedDays);
        listViewBreakdown.setItems(breakdownItems);
        clearBreakdown();

        // All sites are pre-selected: run a first analysis so the app opens populated.
        Platform.runLater(this::runAnalysis);
    }

    // ------------------------------------------------------------------
    // Initialization
    // ------------------------------------------------------------------

    private void initializeSchoolSidebar() {
        for (School school : dataService.loadSchools()) {
            allSchools.add(new SchoolCheckItem(school));
        }
        filteredSchools.setAll(allSchools);
        listViewSchools.setItems(filteredSchools);
        listViewSchools.setCellFactory(lv -> new SchoolCheckCell());

        textFieldSchoolSearch.textProperty().addListener((obs, oldVal, newVal) -> filterSchoolList(newVal));
    }

    private void initializeSortControls() {
        comboSchoolSortBy.getItems().addAll(SORT_SCAN_RATE, SORT_ORDER_RATE, SORT_ALPHABETICAL);
        comboSchoolSortBy.setValue(SORT_SCAN_RATE);
        comboSchoolSortBy.setOnAction(e -> updateSchoolListDisplay());

        toggleSchoolSortDirection.setOnAction(e -> {
            schoolSortAscending = toggleSchoolSortDirection.isSelected();
            toggleSchoolSortDirection.setText(schoolSortAscending ? "Ascending" : "Descending");
            updateSchoolListDisplay();
        });

        sliderDisplayLimit.valueProperty().addListener((obs, oldVal, newVal) -> {
            int limit = newVal.intValue();
            labelDisplayLimit.setText(limit >= 100 ? "All" : String.valueOf(limit));
            updateSchoolListDisplay();
        });

        comboDaySortBy.getItems().addAll(SORT_SCAN_RATE, SORT_ORDER_RATE, SORT_DATE);
        comboDaySortBy.setValue(SORT_SCAN_RATE);
        comboDaySortBy.setOnAction(e -> updateDayPerformanceDisplay());

        toggleDaySortDirection.setOnAction(e -> {
            daySortAscending = toggleDaySortDirection.isSelected();
            toggleDaySortDirection.setText(daySortAscending ? "Ascending" : "Descending");
            updateDayPerformanceDisplay();
        });
    }

    private void initializeCellFactories() {
        listViewSchoolPerformance.setCellFactory(lv -> new PerformanceCell<>(item -> new RowContent(
                item.getSchoolName(), item.getOrderRate(), item.getScanRate(),
                String.format(Locale.US, "avg scans %.1f  ·  avg orders %.1f  ·  %d students  ·  %d days",
                        item.getAverageScanned(), item.getAverageOrdered(),
                        item.getTotalStudents(), item.getDaysAnalyzed()))));

        listViewDayPerformance.setCellFactory(lv -> new PerformanceCell<>(item -> new RowContent(
                item.getDate().format(DAY_FORMAT), item.getOrderRate(), item.getScanRate(),
                String.format(Locale.US, "%,d scans  ·  %,d orders  ·  %,d students  ·  %d sites",
                        item.getTotalScanned(), item.getTotalOrdered(),
                        item.getTotalStudents(), item.getSchoolsWithData()))));

        listViewBreakdown.setCellFactory(lv -> new PerformanceCell<>(item -> new RowContent(
                item.getLabel(), item.getOrderRate(), item.getScanRate(),
                String.format(Locale.US, "%,d scans  ·  %,d orders  ·  %,d students",
                        item.getScanned(), item.getOrdered(), item.getStudents()))));
    }

    private void initializeWeekdayChart() {
        chartSubtitle = new Label();
        chartSubtitle.getStyleClass().add("chart-subtitle");
        chartSubtitle.setWrapText(true);
        chartContainer.getChildren().add(chartSubtitle);

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Weekday");
        NumberAxis yAxis = new NumberAxis();

        weekdayChart = new BarChart<>(xAxis, yAxis);
        weekdayChart.setTitle("Weekday aggregation");
        weekdayChart.setAnimated(false);
        weekdayChart.setLegendVisible(false);
        weekdayChart.setCategoryGap(90);
        chartContainer.getChildren().add(weekdayChart);
        VBox.setVgrow(weekdayChart, Priority.ALWAYS);

        toggleChartScans.setSelected(true);
        toggleChartScans.setOnAction(e -> updateWeekdayChart());
        toggleChartOrders.setOnAction(e -> updateWeekdayChart());
        toggleChartScanRate.setOnAction(e -> updateWeekdayChart());
        toggleChartOrderRate.setOnAction(e -> updateWeekdayChart());
    }

    private void wireActions() {
        buttonWeekBack.setOnAction(e -> shiftDateRange(-7));
        buttonWeekForward.setOnAction(e -> shiftDateRange(7));
        buttonMonthBack.setOnAction(e -> shiftDateRange(-30));
        buttonMonthForward.setOnAction(e -> shiftDateRange(30));

        buttonSelectAll.setOnAction(e -> setAllSchoolsSelected(true));
        buttonSelectNone.setOnAction(e -> setAllSchoolsSelected(false));

        buttonAnalyze.setOnAction(e -> runAnalysis());
        buttonExport.setOnAction(e -> handleExcelExport());

        listViewSchoolPerformance.setOnMouseClicked(e -> {
            SchoolPerformanceSummary selected = listViewSchoolPerformance.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showSchoolBreakdown(selected);
            }
        });
        listViewDayPerformance.setOnMouseClicked(e -> {
            DayPerformanceSummary selected = listViewDayPerformance.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showDayBreakdown(selected);
            }
        });
    }

    // ------------------------------------------------------------------
    // Site selection
    // ------------------------------------------------------------------

    private void filterSchoolList(String searchText) {
        if (searchText == null || searchText.isBlank()) {
            filteredSchools.setAll(allSchools);
            return;
        }
        String needle = searchText.toLowerCase().trim();
        filteredSchools.setAll(allSchools.filtered(
                item -> item.getSchool().name().toLowerCase().contains(needle)));
    }

    private void setAllSchoolsSelected(boolean selected) {
        for (SchoolCheckItem item : allSchools) {
            item.setSelected(selected);
        }
        listViewSchools.refresh();
    }

    private List<School> getSelectedSchools() {
        return allSchools.stream()
                .filter(SchoolCheckItem::isSelected)
                .map(SchoolCheckItem::getSchool)
                .toList();
    }

    // ------------------------------------------------------------------
    // Analysis
    // ------------------------------------------------------------------

    private void runAnalysis() {
        List<School> selectedSchools = getSelectedSchools();
        if (selectedSchools.isEmpty()) {
            showWarning("No sites selected", "Please select at least one site.");
            return;
        }

        LocalDate from = datePickerFrom.getValue();
        LocalDate to = datePickerTo.getValue();
        if (from == null || to == null) {
            showWarning("Missing date range", "Please choose a valid date range.");
            return;
        }
        if (from.isAfter(to)) {
            showWarning("Invalid date range", "The start date must be before the end date.");
            return;
        }

        currentAnalysisFrom = from;
        currentAnalysisTo = to;
        boolean liveFetch = checkBoxLiveFetch.isSelected();

        setLoading(true);
        labelTitle.setText(liveFetch
                ? "Fetching live data from scan log API..."
                : "Reading stored records...");

        CompletableFuture
                .supplyAsync(() -> liveFetch
                        ? aggregationService.aggregateLive(selectedSchools, from, to, this::updateProgress)
                        : aggregationService.aggregateStored(selectedSchools, from, to, this::updateProgress))
                .thenAccept(result -> Platform.runLater(() -> {
                    populateResults(result);
                    setLoading(false);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        setLoading(false);
                        showWarning("Analysis failed", ex.getMessage());
                    });
                    return null;
                });
    }

    private void updateProgress(int current, int total, String message) {
        Platform.runLater(() -> {
            progressBar.setProgress(total > 0 ? (double) current / total : 0.0);
            labelProgress.setText(message != null ? message : current + " / " + total);
        });
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            progressSpinner.setVisible(loading);
            progressBar.setVisible(loading);
            labelProgress.setVisible(loading);
            if (loading) {
                progressBar.setProgress(0.0);
                labelProgress.setText("");
            }
            buttonAnalyze.setDisable(loading);
        });
    }

    private void populateResults(AggregationResult result) {
        clearBreakdown();
        lastResult = result;
        allSchoolSummaries = new ArrayList<>(result.schoolSummaries());
        allDaySummaries = new ArrayList<>(result.daySummaries());
        rawRecords = result.rawRecords();
        weekdayAggregation = result.weekdayAggregation();

        updateSchoolListDisplay();
        updateDayPerformanceDisplay();
        updateWeekdayChart();

        long goodSchools = result.schoolSummaries().stream()
                .filter(s -> s.getScanRate() >= 90)
                .count();
        labelTitle.setText(String.format("%d sites analyzed — %d above 90%% scan rate",
                result.schoolSummaries().size(), goodSchools));
    }

    // ------------------------------------------------------------------
    // Result list rendering
    // ------------------------------------------------------------------

    private void updateSchoolListDisplay() {
        if (allSchoolSummaries.isEmpty()) {
            displayedSchools.clear();
            return;
        }
        List<SchoolPerformanceSummary> sorted = new ArrayList<>(allSchoolSummaries);

        String sortBy = comboSchoolSortBy.getValue();
        Comparator<SchoolPerformanceSummary> comparator;
        if (SORT_ALPHABETICAL.equals(sortBy)) {
            comparator = Comparator.comparing(SchoolPerformanceSummary::getSchoolName,
                    String.CASE_INSENSITIVE_ORDER);
        } else if (SORT_ORDER_RATE.equals(sortBy)) {
            comparator = Comparator.comparingDouble(SchoolPerformanceSummary::getOrderRate);
        } else {
            comparator = Comparator.comparingDouble(SchoolPerformanceSummary::getScanRate);
        }
        if (!schoolSortAscending) {
            comparator = comparator.reversed();
        }
        sorted.sort(comparator);

        int limit = (int) sliderDisplayLimit.getValue();
        if (limit < 100 && limit < sorted.size()) {
            sorted = sorted.subList(0, limit);
        }
        displayedSchools.setAll(sorted);
    }

    private void updateDayPerformanceDisplay() {
        if (allDaySummaries.isEmpty()) {
            displayedDays.clear();
            return;
        }
        List<DayPerformanceSummary> sorted = new ArrayList<>(allDaySummaries);

        String sortBy = comboDaySortBy.getValue();
        Comparator<DayPerformanceSummary> comparator;
        if (SORT_DATE.equals(sortBy)) {
            comparator = Comparator.comparing(DayPerformanceSummary::getDate);
        } else if (SORT_ORDER_RATE.equals(sortBy)) {
            comparator = Comparator.comparingDouble(DayPerformanceSummary::getOrderRate);
        } else {
            comparator = Comparator.comparingDouble(DayPerformanceSummary::getScanRate);
        }
        if (!daySortAscending) {
            comparator = comparator.reversed();
        }
        sorted.sort(comparator);
        displayedDays.setAll(sorted);
    }

    // ------------------------------------------------------------------
    // Drill-down
    // ------------------------------------------------------------------

    private void showSchoolBreakdown(SchoolPerformanceSummary school) {
        Map<LocalDate, SchoolDayRecord> schoolData = rawRecords.get(school.getSchoolId());
        if (schoolData == null || schoolData.isEmpty()) {
            labelBreakdownTitle.setText("No data for " + school.getSchoolName());
            breakdownItems.clear();
            return;
        }
        labelBreakdownTitle.setText("Daily detail — " + school.getSchoolName());

        List<BreakdownItem> items = new ArrayList<>();
        for (SchoolDayRecord record : schoolData.values()) {
            items.add(new BreakdownItem(record.date().format(DAY_FORMAT),
                    record.totalOrdered(), record.totalScanned(), record.studentCount()));
        }
        breakdownItems.setAll(items);
    }

    private void showDayBreakdown(DayPerformanceSummary day) {
        LocalDate targetDate = day.getDate();
        labelBreakdownTitle.setText("Site detail — " + targetDate.format(DAY_FORMAT));

        List<BreakdownItem> items = new ArrayList<>();
        for (Map<LocalDate, SchoolDayRecord> dateMap : rawRecords.values()) {
            SchoolDayRecord record = dateMap.get(targetDate);
            if (record != null && (record.totalOrdered() > 0 || record.totalScanned() > 0)) {
                items.add(new BreakdownItem(record.schoolName(),
                        record.totalOrdered(), record.totalScanned(), record.studentCount()));
            }
        }
        items.sort(Comparator.comparingDouble(BreakdownItem::getScanRate));
        breakdownItems.setAll(items);
    }

    private void clearBreakdown() {
        breakdownItems.clear();
        labelBreakdownTitle.setText("Drill-down — select a site or a day above");
    }

    // ------------------------------------------------------------------
    // Weekday chart
    // ------------------------------------------------------------------

    private void updateWeekdayChart() {
        if (weekdayAggregation == null) {
            return;
        }
        weekdayChart.getData().clear();

        String rangeSuffix = "";
        if (currentAnalysisFrom != null && currentAnalysisTo != null) {
            rangeSuffix = "  (" + currentAnalysisFrom.format(RANGE_FORMAT)
                    + " – " + currentAnalysisTo.format(RANGE_FORMAT) + ")";
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        StringBuilder subtitle = new StringBuilder();
        String title;

        if (toggleChartOrders.isSelected()) {
            title = "Orders per weekday" + rangeSuffix;
            for (int i = 0; i < 5; i++) {
                series.getData().add(new XYChart.Data<>(WeekdayAggregation.WEEKDAY_LABELS[i],
                        weekdayAggregation.getTotalOrders(i)));
                appendAverage(subtitle, i, weekdayAggregation.getTotalOrders(i),
                        weekdayAggregation.getAverageOrders(i));
            }
        } else if (toggleChartScanRate.isSelected()) {
            title = "Scan rate per weekday (%)" + rangeSuffix;
            for (int i = 0; i < 5; i++) {
                series.getData().add(new XYChart.Data<>(WeekdayAggregation.WEEKDAY_LABELS[i],
                        weekdayAggregation.getScanRate(i)));
            }
        } else if (toggleChartOrderRate.isSelected()) {
            title = "Order rate per weekday (%)" + rangeSuffix;
            for (int i = 0; i < 5; i++) {
                series.getData().add(new XYChart.Data<>(WeekdayAggregation.WEEKDAY_LABELS[i],
                        weekdayAggregation.getOrderRate(i)));
            }
        } else {
            title = "Scans per weekday" + rangeSuffix;
            for (int i = 0; i < 5; i++) {
                series.getData().add(new XYChart.Data<>(WeekdayAggregation.WEEKDAY_LABELS[i],
                        weekdayAggregation.getTotalScans(i)));
                appendAverage(subtitle, i, weekdayAggregation.getTotalScans(i),
                        weekdayAggregation.getAverageScans(i));
            }
        }

        weekdayChart.setTitle(title);
        weekdayChart.getData().add(series);
        chartSubtitle.setText(subtitle.isEmpty() ? "" : "Averages per instance:  " + subtitle.toString().trim());
    }

    private void appendAverage(StringBuilder subtitle, int idx, int total, double average) {
        if (weekdayAggregation.getDayCount(idx) >= 2) {
            subtitle.append(String.format(Locale.US, "%s %,d / %d = %.1f    ",
                    WeekdayAggregation.WEEKDAY_LABELS[idx], total,
                    weekdayAggregation.getDayCount(idx), average));
        }
    }

    // ------------------------------------------------------------------
    // Excel export
    // ------------------------------------------------------------------

    private void handleExcelExport() {
        if (lastResult == null || allSchoolSummaries.isEmpty()) {
            showWarning("No data", "Run an analysis first.");
            return;
        }
        DateTimeFormatter fileFormat = DateTimeFormatter.ISO_LOCAL_DATE;
        String suggestedName = "scan-dashboard_"
                + currentAnalysisFrom.format(fileFormat) + "_"
                + currentAnalysisTo.format(fileFormat) + ".xlsx";

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export to Excel");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));
        fileChooser.setInitialFileName(suggestedName);

        File file = fileChooser.showSaveDialog(buttonExport.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            exportService.export(file, lastResult, currentAnalysisFrom, currentAnalysisTo);
            showInfo("Export complete", "Workbook written to:\n" + file.getAbsolutePath());
        } catch (Exception ex) {
            showWarning("Export failed", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void shiftDateRange(int days) {
        if (datePickerFrom.getValue() != null) {
            datePickerFrom.setValue(datePickerFrom.getValue().plusDays(days));
        }
        if (datePickerTo.getValue() != null) {
            datePickerTo.setValue(datePickerTo.getValue().plusDays(days));
        }
    }

    private static void applyIsoDateFormat(DatePicker picker) {
        DateTimeFormatter format = DateTimeFormatter.ISO_LOCAL_DATE;
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : format.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                try {
                    return (text == null || text.isBlank()) ? null : LocalDate.parse(text, format);
                } catch (Exception e) {
                    return null;
                }
            }
        });
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ------------------------------------------------------------------
    // Cells
    // ------------------------------------------------------------------

    /** The rendered parts of one performance row. */
    private record RowContent(String label, double orderRate, double scanRate, String detail) {
    }

    /**
     * Shared list cell for all three result lists: a bold label, order/scan
     * rate badges, and a right-aligned detail line. The scan rate drives a
     * status accent (good / warn / low) applied via CSS classes.
     */
    private static class PerformanceCell<T> extends ListCell<T> {
        private final java.util.function.Function<T, RowContent> extractor;
        private final HBox container = new HBox(12);
        private final Label nameLabel = new Label();
        private final Label orderBadge = new Label();
        private final Label scanBadge = new Label();
        private final Label detailLabel = new Label();

        PerformanceCell(java.util.function.Function<T, RowContent> extractor) {
            this.extractor = extractor;
            // Adopt the ListView's width instead of forcing a horizontal scrollbar;
            // the detail label ellipsizes when space runs out.
            setPrefWidth(0);
            container.setAlignment(Pos.CENTER_LEFT);
            container.getStyleClass().add("perf-row");

            nameLabel.getStyleClass().add("perf-name");
            nameLabel.setMinWidth(170);
            nameLabel.setMaxWidth(280);

            orderBadge.getStyleClass().add("badge-order");
            orderBadge.setMinWidth(72);
            scanBadge.getStyleClass().add("badge-scan");
            scanBadge.setMinWidth(72);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            detailLabel.getStyleClass().add("perf-detail");

            container.getChildren().addAll(nameLabel, orderBadge, scanBadge, spacer, detailLabel);
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("status-good", "status-warn", "status-low");
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            RowContent content = extractor.apply(item);

            String label = content.label();
            if (label != null && label.length() > 34) {
                label = label.substring(0, 31) + "...";
            }
            nameLabel.setText(label);
            orderBadge.setText(String.format("OR %.0f%%", content.orderRate()));
            scanBadge.setText(String.format("SR %.0f%%", content.scanRate()));
            detailLabel.setText(content.detail());

            if (content.scanRate() >= 90) {
                getStyleClass().add("status-good");
            } else if (content.scanRate() >= 85) {
                getStyleClass().add("status-warn");
            } else {
                getStyleClass().add("status-low");
            }
            setGraphic(container);
            setText(null);
        }
    }

    /** Checkbox cell for the site selection sidebar. */
    private static class SchoolCheckCell extends ListCell<SchoolCheckItem> {
        private final CheckBox checkBox = new CheckBox();

        SchoolCheckCell() {
            checkBox.setOnAction(e -> {
                SchoolCheckItem item = getItem();
                if (item != null) {
                    item.setSelected(checkBox.isSelected());
                }
            });
        }

        @Override
        protected void updateItem(SchoolCheckItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                checkBox.setSelected(item.isSelected());
                checkBox.setText(item.getSchool().name());
                setGraphic(checkBox);
                setText(null);
            }
        }
    }
}
