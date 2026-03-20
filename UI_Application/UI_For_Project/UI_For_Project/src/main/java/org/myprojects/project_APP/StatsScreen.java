package org.myprojects.project_APP;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import static org.myprojects.project_APP.Project_APP_1.*;

/**
 * Statistics screen — shows:
 *  • A bar chart of ml dispensed per day for the last 7 days
 *  • A configurable daily goal with a progress indicator for today
 *  • A paginated table of all refill history (15 rows per page)
 */
public class StatsScreen {

    // ── Pagination ─────────────────────────────────────────────────────────────
    private static final int ROWS_PER_PAGE = 15;

    // ── State ──────────────────────────────────────────────────────────────────
    private final Stage  primaryStage;
    private final Scene  backScene;        // main scene to return to
    private final kul_db_conn kul_db;

    private double       dailyGoalMl   = 500.0; // default, user can change
    private int          currentPage   = 0;
    private List<JSONObject> allRows   = new ArrayList<>();

    // ── Dynamic UI nodes ──────────────────────────────────────────────────────
    private VBox         chartContainer;
    private Label        todayMlLabel;
    private Label        goalProgressLabel;
    private Rectangle    goalBarFill;
    private VBox         tableBody;
    private Label        pageLabel;
    private Button       prevBtn;
    private Button       nextBtn;
    private TextField    goalField;
    private Label        statusLabel;

    public StatsScreen(Stage primaryStage, Scene backScene, kul_db_conn kul_db) {
        this.primaryStage = primaryStage;
        this.backScene    = backScene;
        this.kul_db       = kul_db;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BUILD SCENE
    // ══════════════════════════════════════════════════════════════════════════

    public Scene buildScene() {

        VBox root = new VBox(24);
        root.setStyle("-fx-background-color: " + BG + ";");
        root.setPadding(new Insets(32));
        root.setAlignment(Pos.TOP_CENTER);
        root.setPrefWidth(500);

        // ── Header: back button + title ───────────────────────────────────────
        Button backBtn = new Button("← Back");
        backBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: " + ACCENT_BLUE + "; " +
                        "-fx-font-size: 13px; -fx-padding: 6 0; -fx-cursor: hand; -fx-border-color: transparent;");
        backBtn.setOnMouseEntered(e -> backBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #79c0ff; " +
                        "-fx-font-size: 13px; -fx-padding: 6 0; -fx-cursor: hand; -fx-border-color: transparent;"));
        backBtn.setOnMouseExited(e -> backBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: " + ACCENT_BLUE + "; " +
                        "-fx-font-size: 13px; -fx-padding: 6 0; -fx-cursor: hand; -fx-border-color: transparent;"));
        backBtn.setOnAction(e -> primaryStage.setScene(backScene));

        Label pageTitle = new Label("Statistics");
        pageTitle.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 20px; -fx-font-weight: bold;");

        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);
        HBox header = new HBox(backBtn, hSpacer, pageTitle);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPrefWidth(440);

        // ── Daily goal card ───────────────────────────────────────────────────
        VBox goalCard = makeWideCard();
        Label goalCardTitle = new Label("Daily Goal");
        goalCardTitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        // Today's total + goal progress bar
        todayMlLabel = new Label("— ml today");
        todayMlLabel.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 22px; -fx-font-weight: bold;");

        goalProgressLabel = new Label("Set your goal below");
        goalProgressLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px;");

        // Progress bar — background track + fill rectangle
        Rectangle goalBarTrack = new Rectangle(440 - 40, 10);
        goalBarTrack.setArcWidth(6); goalBarTrack.setArcHeight(6);
        goalBarTrack.setFill(javafx.scene.paint.Color.web(BORDER));

        goalBarFill = new Rectangle(0, 10);
        goalBarFill.setArcWidth(6); goalBarFill.setArcHeight(6);
        goalBarFill.setFill(javafx.scene.paint.Color.web(ACCENT_BLUE));

        javafx.scene.layout.StackPane goalBarPane = new javafx.scene.layout.StackPane();
        goalBarPane.setAlignment(Pos.CENTER_LEFT);
        goalBarPane.getChildren().addAll(goalBarTrack, goalBarFill);

        // Goal input row
        goalField = new TextField(String.valueOf((int) dailyGoalMl));
        goalField.setPrefWidth(90);
        goalField.setMaxWidth(90);
        String gfStyle = "-fx-background-color: " + BG + "; -fx-text-fill: " + TEXT +
                "; -fx-prompt-text-fill: " + MUTED + "; -fx-border-color: " + BORDER +
                "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 6 10;";
        String gfFocus = "-fx-background-color: " + BG + "; -fx-text-fill: " + TEXT +
                "; -fx-prompt-text-fill: " + MUTED + "; -fx-border-color: " + ACCENT_BLUE +
                "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 6 10;";
        goalField.setStyle(gfStyle);
        goalField.focusedProperty().addListener((o, old, f) -> goalField.setStyle(f ? gfFocus : gfStyle));

        Label mlUnitLbl = new Label("ml / day");
        mlUnitLbl.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 13px;");

        Button setGoalBtn = makeSmallButton("Set Goal");
        setGoalBtn.setOnAction(e -> applyGoal());

        HBox goalInputRow = new HBox(10, goalField, mlUnitLbl, setGoalBtn);
        goalInputRow.setAlignment(Pos.CENTER_LEFT);

        goalCard.getChildren().addAll(goalCardTitle, todayMlLabel, goalBarPane, goalProgressLabel, goalInputRow);

        // ── Chart card ────────────────────────────────────────────────────────
        VBox chartCard = makeWideCard();
        Label chartTitle = new Label("Last 7 Days  (ml dispensed per day)");
        chartTitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        chartContainer = new VBox();
        chartContainer.setAlignment(Pos.CENTER);
        chartContainer.setPrefHeight(220);

        Button refreshChartBtn = makeSmallButton("↻  Refresh");
        refreshChartBtn.setOnAction(e -> loadData());

        chartCard.getChildren().addAll(chartTitle, chartContainer, refreshChartBtn);

        // ── Table card ────────────────────────────────────────────────────────
        VBox tableCard = makeWideCard();
        Label tableTitle = new Label("Refill History");
        tableTitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        // Column headers
        GridPane tableHeader = makeTableRow("TIME", "ML DISPENSED", true);

        // Table body (rows swapped on pagination)
        tableBody = new VBox(2);

        // Pagination controls
        prevBtn   = makeSmallButton("← Prev");
        nextBtn   = makeSmallButton("Next →");
        pageLabel = new Label("Page 1");
        pageLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");

        prevBtn.setOnAction(e -> { if (currentPage > 0) { currentPage--; renderTablePage(); } });
        nextBtn.setOnAction(e -> {
            int maxPage = (int) Math.ceil((double) allRows.size() / ROWS_PER_PAGE) - 1;
            if (currentPage < maxPage) { currentPage++; renderTablePage(); }
        });

        HBox paginationRow = new HBox(12, prevBtn, pageLabel, nextBtn);
        paginationRow.setAlignment(Pos.CENTER);

        tableCard.getChildren().addAll(tableTitle, tableHeader, tableBody, paginationRow);

        // ── Status label ──────────────────────────────────────────────────────
        statusLabel = new Label("Loading...");
        statusLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");

        // ── Assemble ──────────────────────────────────────────────────────────
        root.getChildren().addAll(header, goalCard, chartCard, tableCard, statusLabel);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + ";");

        Scene scene = new Scene(scroll, 540, 700);

        // Load data in background once scene is built
        loadData();

        return scene;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DATA LOADING
    // ══════════════════════════════════════════════════════════════════════════

    private void loadData() {
        setStatus("Loading data...", MUTED);
        Thread t = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();

                // ── Fetch last 7 days of history ──────────────────────────────
                // Endpoint should return all rows from RefillHistory ordered by TimeOfRefill DESC
                // Replace "getRefillHistory" with your actual endpoint name
                HttpResponse<String> resp = kul_db.makeGETRequest(client,
                        BASE_URL + "/getRefillHistory");

                JSONArray rows = new JSONArray(resp.body());

                // Build full row list for the table (all history, newest first)
                List<JSONObject> rowList = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    rowList.add(rows.getJSONObject(i));
                }

                // Build day-grouped map for the chart (last 7 days)
                Map<String, Double> dayTotals = buildDayTotals(rowList);

                // Today's total for goal progress
                String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd"));
                double todayMl = dayTotals.getOrDefault(today, 0.0);

                javafx.application.Platform.runLater(() -> {
                    allRows = rowList;
                    currentPage = 0;
                    renderChart(dayTotals);
                    renderTablePage();
                    updateGoalProgress(todayMl);
                    setStatus("", MUTED);
                });

            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() ->
                        setStatus("Failed to load history data.", DANGER));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ── Group rows by date (MM-dd), summing MlDispensed, for last 7 days ──────
    private Map<String, Double> buildDayTotals(List<JSONObject> rows) {
        // Prepare all 7 days so empty days show as 0 on the chart
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MM-dd");
        Map<String, Double> totals = new LinkedHashMap<>();
        for (int d = 6; d >= 0; d--) {
            totals.put(LocalDate.now().minusDays(d).format(labelFmt), 0.0);
        }

        for (JSONObject row : rows) {
            try {
                // TimeOfRefill format expected: "YYYY-MM-DD HH:mm:ss"
                String timeStr = row.getString("TimeOfRefill");
                String dayKey  = timeStr.substring(5, 10); // "MM-DD"
                double ml      = row.getDouble("MlDispensed");

                if (totals.containsKey(dayKey)) {
                    totals.put(dayKey, totals.get(dayKey) + ml);
                }
            } catch (Exception ignored) {}
        }
        return totals;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHART RENDERING
    // ══════════════════════════════════════════════════════════════════════════

    private void renderChart(Map<String, Double> dayTotals) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        xAxis.setLabel("Date");
        yAxis.setLabel("ml");

        // Style axes to match dark theme
        String axisStyle = "-fx-tick-label-fill: " + MUTED + "; -fx-text-fill: " + MUTED + ";";
        xAxis.setStyle(axisStyle);
        yAxis.setStyle(axisStyle);

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(null);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(200);
        chart.setPrefWidth(460);
        chart.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-plot-background-color: " + BG + "; " +
                        "-fx-horizontal-grid-lines-visible: true; " +
                        "-fx-vertical-grid-lines-visible: false;");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<String, Double> entry : dayTotals.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        chart.getData().add(series);

        // Color the bars after the chart is added to the scene
        chart.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                chart.applyCss();
                chart.layout();
                for (XYChart.Data<String, Number> d : series.getData()) {
                    if (d.getNode() != null) {
                        d.getNode().setStyle(
                                "-fx-bar-fill: " + ACCENT_BLUE + "; " +
                                        "-fx-background-radius: 4 4 0 0;");
                    }
                }
            }
        });

        // Also apply after layout in case scene is already set
        javafx.application.Platform.runLater(() -> {
            chart.applyCss();
            chart.layout();
            for (XYChart.Data<String, Number> d : series.getData()) {
                if (d.getNode() != null) {
                    d.getNode().setStyle(
                            "-fx-bar-fill: " + ACCENT_BLUE + "; " +
                                    "-fx-background-radius: 4 4 0 0;");
                }
            }
        });

        chartContainer.getChildren().setAll(chart);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GOAL PROGRESS
    // ══════════════════════════════════════════════════════════════════════════

    private void updateGoalProgress(double todayMl) {
        todayMlLabel.setText(String.format("%.0f ml today", todayMl));

        double ratio    = Math.min(todayMl / dailyGoalMl, 1.0);
        double barWidth = (440 - 40) * ratio;
        goalBarFill.setWidth(barWidth);

        // Colour: green if at/over goal, blue otherwise
        boolean reached = todayMl >= dailyGoalMl;
        goalBarFill.setFill(javafx.scene.paint.Color.web(reached ? SUCCESS : ACCENT_BLUE));
        todayMlLabel.setStyle("-fx-text-fill: " + (reached ? SUCCESS : TEXT) + "; -fx-font-size: 22px; -fx-font-weight: bold;");

        goalProgressLabel.setText(String.format("%.0f / %.0f ml  (%.0f%%)",
                todayMl, dailyGoalMl, ratio * 100));
        goalProgressLabel.setStyle("-fx-text-fill: " + (reached ? SUCCESS : MUTED) + "; -fx-font-size: 11px;");
    }

    private void applyGoal() {
        String raw = goalField.getText().trim();
        try {
            double val = Double.parseDouble(raw);
            if (val <= 0) throw new NumberFormatException();
            dailyGoalMl = val;
            // Re-evaluate today's total against new goal
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd"));
            double todayMl = allRows.stream()
                    .filter(r -> {
                        try { return r.getString("TimeOfRefill").substring(5, 10).equals(today); }
                        catch (Exception ex) { return false; }
                    })
                    .mapToDouble(r -> { try { return r.getDouble("MlDispensed"); } catch (Exception ex) { return 0; } })
                    .sum();
            updateGoalProgress(todayMl);
            setStatus("Daily goal updated to " + (int) dailyGoalMl + " ml.", SUCCESS);
        } catch (NumberFormatException ex) {
            setStatus("Goal must be a positive number.", DANGER);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TABLE RENDERING
    // ══════════════════════════════════════════════════════════════════════════

    private void renderTablePage() {
        tableBody.getChildren().clear();

        int start = currentPage * ROWS_PER_PAGE;
        int end   = Math.min(start + ROWS_PER_PAGE, allRows.size());

        if (allRows.isEmpty()) {
            Label empty = new Label("No refill history found.");
            empty.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px; -fx-padding: 12 0;");
            tableBody.getChildren().add(empty);
        } else {
            for (int i = start; i < end; i++) {
                JSONObject row  = allRows.get(i);
                boolean isEven  = (i % 2 == 0);
                try {
                    String time = row.getString("TimeOfRefill");
                    // Trim seconds: "2025-04-12 14:35:00" → "2025-04-12 14:35"
                    if (time.length() >= 16) time = time.substring(0, 16);
                    String ml = String.format("%.0f ml", row.getDouble("MlDispensed"));
                    tableBody.getChildren().add(makeTableRow(time, ml, false, isEven));
                } catch (Exception ex) {
                    tableBody.getChildren().add(makeTableRow("—", "—", false, isEven));
                }
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) allRows.size() / ROWS_PER_PAGE));
        pageLabel.setText("Page " + (currentPage + 1) + " / " + totalPages);
        prevBtn.setDisable(currentPage == 0);
        nextBtn.setDisable(currentPage >= totalPages - 1);
    }

    // ── Build a table header row ──────────────────────────────────────────────
    private GridPane makeTableRow(String col1, String col2, boolean isHeader) {
        return makeTableRow(col1, col2, isHeader, false);
    }

    private GridPane makeTableRow(String col1, String col2, boolean isHeader, boolean shaded) {
        GridPane grid = new GridPane();
        grid.setPrefWidth(440);
        grid.setHgap(0);

        String rowBg    = isHeader ? BORDER : (shaded ? "#1c2128" : CARD);
        String textCol  = isHeader ? MUTED  : TEXT;
        String fontSize = isHeader ? "10px" : "12px";
        String weight   = isHeader ? "bold" : "normal";

        grid.setStyle("-fx-background-color: " + rowBg + "; -fx-background-radius: 4; -fx-padding: 8 12;");

        Label lbl1 = new Label(col1);
        lbl1.setStyle("-fx-text-fill: " + textCol + "; -fx-font-size: " + fontSize + "; -fx-font-weight: " + weight + ";");
        lbl1.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(lbl1, Priority.ALWAYS);

        Label lbl2 = new Label(col2);
        lbl2.setStyle("-fx-text-fill: " + textCol + "; -fx-font-size: " + fontSize + "; -fx-font-weight: " + weight + "; -fx-text-alignment: right;");
        lbl2.setMinWidth(100);

        grid.addRow(0, lbl1, lbl2);
        return grid;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
    }

    private VBox makeWideCard() {
        VBox c = new VBox(14);
        c.setStyle("-fx-background-color: " + CARD + "; -fx-border-color: " + BORDER +
                "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 20;");
        c.setPrefWidth(440);
        return c;
    }

    private Button makeSmallButton(String text) {
        Button b = new Button(text);
        String s  = "-fx-background-color: " + CARD + "; -fx-text-fill: " + TEXT +
                "; -fx-font-size: 12px; -fx-padding: 7 14; -fx-border-color: " + BORDER +
                "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;";
        String sh = "-fx-background-color: #21262d; -fx-text-fill: " + TEXT +
                "; -fx-font-size: 12px; -fx-padding: 7 14; -fx-border-color: " + ACCENT_BLUE +
                "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;";
        b.setStyle(s);
        b.setOnMouseEntered(e -> b.setStyle(sh));
        b.setOnMouseExited (e -> b.setStyle(s));
        return b;
    }
}
