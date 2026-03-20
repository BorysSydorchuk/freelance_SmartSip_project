package org.myprojects.project_APP;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONObject;


public class Project_APP_1 extends Application {

    // ── Bottle config ──────────────────────────────────────────────────────────
    static final String BASE_URL       = "https://studev.groept.be/api/a25EE2team203";
    private static final double FULL_VOLUME_ML = 100.0; // ← replace with measured bottle volume in ml
    private static final int    MAX_LEVEL      = 7;

    // ── Tank mode constants ────────────────────────────────────────────────────
    private static final String MODE_COLD = "cold";
    private static final String MODE_HOT  = "hot";
    private static final String MODE_MIX  = "mix";

    // ── Colours (package-visible so StatsScreen can reuse them) ───────────────
    static final String BG          = "#0d1117";
    static final String CARD        = "#161b22";
    static final String BORDER      = "#30363d";
    static final String ACCENT_BLUE = "#58a6ff";
    static final String ACCENT_COLD = "#79c0ff";
    static final String ACCENT_WARM = "#f0883e";
    static final String ACCENT_MIX  = "#bc8cff";
    static final String TEXT        = "#e6edf3";
    static final String MUTED       = "#8b949e";
    static final String SUCCESS     = "#3fb950";
    static final String WARNING     = "#d29922";
    static final String DANGER      = "#f85149";

    // ── State ─────────────────────────────────────────────────────────────────
    private kul_db_conn kul_db;
    private int         currentLevel = 0;
    private double      bottleTemp   = 0.0;
    private String      tankMode     = MODE_MIX;

    // ── Dynamic UI nodes ──────────────────────────────────────────────────────
    private Label     levelValueLabel;
    private Label     tempValueLabel;
    private Label     mlToAddLabel;
    private Label     statusLabel;
    private VBox      levelBarContainer;
    private Button    refillButton;
    private TextField targetTempField;
    private VBox      targetTempCard;

    // ── Scenes (swapped on the same Stage) ────────────────────────────────────
    private Scene mainScene;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        kul_db = new kul_db_conn();
        stage.setTitle("SmartSip");

        mainScene = buildMainScene();
        stage.setScene(mainScene);
        stage.setOnCloseRequest(e -> System.exit(0));
        stage.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAIN SCENE
    // ══════════════════════════════════════════════════════════════════════════

    private Scene buildMainScene() {

        VBox root = new VBox(24);
        root.setStyle("-fx-background-color: " + BG + ";");
        root.setPadding(new Insets(32));
        root.setAlignment(Pos.TOP_CENTER);
        root.setPrefWidth(420);

        // ── Header row: title + stats button ─────────────────────────────────
        Label title    = new Label("SmartSip");
        title.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 22px; -fx-font-weight: bold;");
        Label subtitle = new Label("Refill Station");
        subtitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 13px;");
        VBox titleBox  = new VBox(4);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.getChildren().addAll(title, subtitle);

        Button statsBtn = new Button("📊  Statistics");
        statsBtn.setStyle(
                "-fx-background-color: " + CARD + "; -fx-text-fill: " + ACCENT_BLUE + "; " +
                        "-fx-font-size: 12px; -fx-padding: 8 14; " +
                        "-fx-border-color: " + ACCENT_BLUE + "; -fx-border-radius: 6; -fx-background-radius: 6; " +
                        "-fx-cursor: hand;");
        statsBtn.setOnMouseEntered(e -> statsBtn.setStyle(
                "-fx-background-color: " + ACCENT_BLUE + "; -fx-text-fill: " + BG + "; " +
                        "-fx-font-size: 12px; -fx-padding: 8 14; " +
                        "-fx-border-color: " + ACCENT_BLUE + "; -fx-border-radius: 6; -fx-background-radius: 6; " +
                        "-fx-cursor: hand;"));
        statsBtn.setOnMouseExited(e -> statsBtn.setStyle(
                "-fx-background-color: " + CARD + "; -fx-text-fill: " + ACCENT_BLUE + "; " +
                        "-fx-font-size: 12px; -fx-padding: 8 14; " +
                        "-fx-border-color: " + ACCENT_BLUE + "; -fx-border-radius: 6; -fx-background-radius: 6; " +
                        "-fx-cursor: hand;"));
        statsBtn.setOnAction(e -> openStatsScreen());

        // Push stats button to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headerRow = new HBox();
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPrefWidth(356);
        headerRow.getChildren().addAll(titleBox, spacer, statsBtn);

        // ── Bottle status card ────────────────────────────────────────────────
        VBox statusCard = makeCard();
        Label statusCardTitle = new Label("Bottle Status");
        statusCardTitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        levelBarContainer = new VBox(3);
        levelBarContainer.setAlignment(Pos.BOTTOM_CENTER);
        levelBarContainer.setPrefHeight(80);

        HBox statsRow = new HBox(30);
        statsRow.setAlignment(Pos.CENTER);

        VBox levelBox = new VBox(4);
        levelBox.setAlignment(Pos.CENTER);
        Label levelTitleLbl = new Label("WATER LEVEL");
        levelTitleLbl.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 10px;");
        levelValueLabel = new Label("— / 7");
        levelValueLabel.setStyle("-fx-text-fill: " + ACCENT_BLUE + "; -fx-font-size: 24px; -fx-font-weight: bold;");
        levelBox.getChildren().addAll(levelTitleLbl, levelValueLabel);

        VBox tempBox = new VBox(4);
        tempBox.setAlignment(Pos.CENTER);
        Label tempTitleLbl = new Label("TEMPERATURE");
        tempTitleLbl.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 10px;");
        tempValueLabel = new Label("— °C");
        tempValueLabel.setStyle("-fx-text-fill: " + ACCENT_WARM + "; -fx-font-size: 24px; -fx-font-weight: bold;");
        tempBox.getChildren().addAll(tempTitleLbl, tempValueLabel);

        statsRow.getChildren().addAll(levelBox, makeDivider(), tempBox);

        mlToAddLabel = new Label("Press refresh to read bottle");
        mlToAddLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");
        mlToAddLabel.setWrapText(true);

        Button refreshButton = makeSecondaryButton("↻  Refresh Bottle Data");
        refreshButton.setOnAction(e -> fetchBottleData());

        statusCard.getChildren().addAll(statusCardTitle, levelBarContainer, statsRow, mlToAddLabel, refreshButton);

        // ── Fill mode card ────────────────────────────────────────────────────
        VBox modeCard = makeCard();
        Label modeTitle = new Label("Fill Mode");
        modeTitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        ToggleGroup modeGroup  = new ToggleGroup();
        ToggleButton coldBtn   = new ToggleButton("❄  Cold");
        ToggleButton mixBtn    = new ToggleButton("〜  Mix");
        ToggleButton hotBtn    = new ToggleButton("☀  Hot");
        coldBtn.setToggleGroup(modeGroup);
        mixBtn .setToggleGroup(modeGroup);
        hotBtn .setToggleGroup(modeGroup);
        mixBtn.setSelected(true);

        String tBase = "-fx-font-size: 13px; -fx-padding: 10 22; -fx-cursor: hand; -fx-border-radius: 6; -fx-background-radius: 6;";
        String tOff  = tBase + "-fx-background-color: " + CARD + "; -fx-text-fill: " + MUTED + "; -fx-border-color: " + BORDER + ";";

        coldBtn.setStyle(tOff);
        mixBtn .setStyle(tBase + "-fx-background-color: " + ACCENT_MIX  + "; -fx-text-fill: " + BG + "; -fx-font-weight: bold;");
        hotBtn .setStyle(tOff);

        coldBtn.setOnAction(e -> {
            tankMode = MODE_COLD;
            coldBtn.setStyle(tBase + "-fx-background-color: " + ACCENT_COLD + "; -fx-text-fill: " + BG + "; -fx-font-weight: bold;");
            mixBtn .setStyle(tOff);
            hotBtn .setStyle(tOff);
            targetTempCard.setVisible(false);
            targetTempCard.setManaged(false);
        });
        mixBtn.setOnAction(e -> {
            tankMode = MODE_MIX;
            coldBtn.setStyle(tOff);
            mixBtn .setStyle(tBase + "-fx-background-color: " + ACCENT_MIX  + "; -fx-text-fill: " + BG + "; -fx-font-weight: bold;");
            hotBtn .setStyle(tOff);
            targetTempCard.setVisible(true);
            targetTempCard.setManaged(true);
        });
        hotBtn.setOnAction(e -> {
            tankMode = MODE_HOT;
            coldBtn.setStyle(tOff);
            mixBtn .setStyle(tOff);
            hotBtn .setStyle(tBase + "-fx-background-color: " + ACCENT_WARM + "; -fx-text-fill: " + BG + "; -fx-font-weight: bold;");
            targetTempCard.setVisible(false);
            targetTempCard.setManaged(false);
        });

        HBox toggleRow = new HBox(10);
        toggleRow.setAlignment(Pos.CENTER);
        toggleRow.getChildren().addAll(coldBtn, mixBtn, hotBtn);

        Label modeHint = new Label("Cold / Hot fills entirely from one tank.  Mix blends both to reach your target temperature.");
        modeHint.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px;");
        modeHint.setWrapText(true);

        modeCard.getChildren().addAll(modeTitle, toggleRow, modeHint);

        // ── Target temperature card (mix only) ────────────────────────────────
        targetTempCard = makeCard();
        Label targetTempTitle = new Label("Target Temperature  (Mix mode)");
        targetTempTitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        targetTempField = new TextField();
        targetTempField.setPromptText("e.g. 22");
        targetTempField.setPrefWidth(120);
        targetTempField.setMaxWidth(120);

        String fStyle = "-fx-background-color: " + BG + "; -fx-text-fill: " + TEXT + "; -fx-prompt-text-fill: " + MUTED + "; -fx-border-color: " + BORDER    + "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 16px; -fx-padding: 8 12; -fx-alignment: center;";
        String fFocus = "-fx-background-color: " + BG + "; -fx-text-fill: " + TEXT + "; -fx-prompt-text-fill: " + MUTED + "; -fx-border-color: " + ACCENT_MIX + "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 16px; -fx-padding: 8 12; -fx-alignment: center;";
        targetTempField.setStyle(fStyle);
        targetTempField.focusedProperty().addListener((obs, old, focused) ->
                targetTempField.setStyle(focused ? fFocus : fStyle));

        Label unitLabel = new Label("°C");
        unitLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 20px; -fx-padding: 0 0 0 8;");

        HBox inputRow = new HBox(6);
        inputRow.setAlignment(Pos.CENTER);
        inputRow.getChildren().addAll(targetTempField, unitLabel);

        Label targetHint = new Label("The Pi reads tank temperatures and calculates the cold/hot split automatically.");
        targetHint.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px;");
        targetHint.setWrapText(true);

        targetTempCard.getChildren().addAll(targetTempTitle, inputRow, targetHint);

        // ── Refill button ─────────────────────────────────────────────────────
        refillButton = new Button("Request Refill");
        String btnS  = "-fx-background-color: " + ACCENT_BLUE + "; -fx-text-fill: " + BG + "; -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 14 40; -fx-background-radius: 8; -fx-cursor: hand; -fx-pref-width: 340px;";
        String btnSH = "-fx-background-color: #79c0ff; -fx-text-fill: "               + BG + "; -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 14 40; -fx-background-radius: 8; -fx-cursor: hand; -fx-pref-width: 340px;";
        refillButton.setStyle(btnS);
        refillButton.setOnMouseEntered(e -> refillButton.setStyle(btnSH));
        refillButton.setOnMouseExited (e -> refillButton.setStyle(btnS));
        refillButton.setOnAction(e -> requestRefill());

        // ── Status label ──────────────────────────────────────────────────────
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");
        statusLabel.setWrapText(true);

        // ── Assemble ──────────────────────────────────────────────────────────
        root.getChildren().addAll(headerRow, statusCard, modeCard, targetTempCard, refillButton, statusLabel);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: " + BG + "; -fx-background-color: " + BG + ";");

        return new Scene(scroll, 440, 650);
    }

    // ── Navigate to stats screen ──────────────────────────────────────────────
    private void openStatsScreen() {
        StatsScreen stats = new StatsScreen(primaryStage, mainScene, kul_db);
        primaryStage.setScene(stats.buildScene());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BOTTLE DATA
    // ══════════════════════════════════════════════════════════════════════════

    private void fetchBottleData() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = kul_db.makeGETRequest(client, BASE_URL + "/getBottleStatus");

            JSONArray array = new JSONArray(response.body());
            if (array.length() == 0) { setStatus("No bottle data found.", WARNING); return; }

            JSONObject latest = array.getJSONObject(0);
            currentLevel = latest.getInt("level");
            bottleTemp   = latest.getDouble("temperature");

            updateBottleDisplay();
            setStatus("Bottle data refreshed.", SUCCESS);

        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Failed to fetch bottle data.", DANGER);
        }
    }

    private void updateBottleDisplay() {
        levelValueLabel.setText(currentLevel + " / " + MAX_LEVEL);
        tempValueLabel.setText(String.format("%.1f °C", bottleTemp));

        levelBarContainer.getChildren().clear();
        for (int i = MAX_LEVEL; i >= 1; i--) {
            Rectangle seg = new Rectangle(220, 8);
            seg.setArcWidth(4);
            seg.setArcHeight(4);
            if (i <= currentLevel) {
                double ratio = (double) i / MAX_LEVEL;
                seg.setFill(Color.web(ACCENT_BLUE).interpolate(Color.web("#1f6feb"), 1 - ratio));
            } else {
                seg.setFill(Color.web(BORDER));
            }
            levelBarContainer.getChildren().add(seg);
        }

        if (currentLevel >= MAX_LEVEL) {
            mlToAddLabel.setText("Bottle is already full!");
            mlToAddLabel.setStyle("-fx-text-fill: " + SUCCESS + "; -fx-font-size: 12px;");
            refillButton.setDisable(true);
        } else {
            mlToAddLabel.setText(String.format("%.0f ml needed to reach full", FULL_VOLUME_ML - levelToMl(currentLevel)));
            mlToAddLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");
            refillButton.setDisable(false);
        }
    }

    private double levelToMl(int level) {
        return level * (FULL_VOLUME_ML / MAX_LEVEL);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REFILL REQUEST
    // ══════════════════════════════════════════════════════════════════════════

    private void requestRefill() {
        if (currentLevel == 0 && bottleTemp == 0.0) {
            setStatus("Please refresh bottle data first.", WARNING);
            return;
        }

        double targetTemp = 0.0;
        if (tankMode.equals(MODE_MIX)) {
            String raw = targetTempField.getText().trim();
            if (raw.isEmpty()) { setStatus("Please enter a target temperature for mix mode.", WARNING); return; }
            try {
                targetTemp = Double.parseDouble(raw);
            } catch (NumberFormatException ex) {
                setStatus("Target temperature must be a number (e.g. 22 or 18.5).", DANGER);
                return;
            }
            if (targetTemp <= 0 || targetTemp > 100) {
                setStatus("Target temperature must be between 0 and 100 °C.", DANGER);
                return;
            }
        }

        double bottleVolumeCurrent = levelToMl(currentLevel);
        refillButton.setDisable(true);
        setStatus("Sending refill request...", MUTED);

        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = BASE_URL + "/insertRefillRequest"
                    + "/" + String.format("%.0f", bottleVolumeCurrent)
                    + "/" + String.format("%.1f", bottleTemp)
                    + "/" + String.format("%.1f", targetTemp)
                    + "/" + tankMode;

            System.out.println("Refill request → " + url);
            kul_db.makeGETRequest(client, url);
            setStatus("Request sent. Waiting for station...", ACCENT_BLUE);
            pollForCompletion(client);

        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Error sending refill request.", DANGER);
            refillButton.setDisable(false);
        }
    }

    private void pollForCompletion(HttpClient client) {
        Thread t = new Thread(() -> {
            int maxAttempts = 60;
            for (int i = 0; i < maxAttempts; i++) {
                try {
                    Thread.sleep(2000);
                    HttpResponse<String> resp = kul_db.makeGETRequest(client, BASE_URL + "/getRefillStatus");
                    JSONArray arr = new JSONArray(resp.body());
                    if (arr.length() > 0 && arr.getJSONObject(0).getString("RefillResponse").equals("done")) {
                        javafx.application.Platform.runLater(() -> {
                            setStatus("Refill complete! Refresh to see new level.", SUCCESS);
                            refillButton.setDisable(false);
                        });
                        return;
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            javafx.application.Platform.runLater(() -> {
                setStatus("Timed out waiting for station. Check manually.", WARNING);
                refillButton.setDisable(false);
            });
        });
        t.setDaemon(true);
        t.start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SHARED HELPERS (also used by StatsScreen via inheritance/static)
    // ══════════════════════════════════════════════════════════════════════════

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
    }

    static VBox makeCardStatic() {
        VBox c = new VBox(14);
        c.setStyle("-fx-background-color: " + CARD + "; -fx-border-color: " + BORDER + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 20;");
        return c;
    }

    private VBox makeCard() {
        VBox c = makeCardStatic();
        c.setPrefWidth(356);
        return c;
    }

    private Button makeSecondaryButton(String text) {
        Button b = new Button(text);
        String s  = "-fx-background-color: " + CARD + "; -fx-text-fill: " + TEXT + "; -fx-font-size: 12px; -fx-padding: 8 18; -fx-border-color: " + BORDER     + "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;";
        String sh = "-fx-background-color: #21262d; -fx-text-fill: "       + TEXT + "; -fx-font-size: 12px; -fx-padding: 8 18; -fx-border-color: " + ACCENT_BLUE + "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;";
        b.setStyle(s);
        b.setOnMouseEntered(e -> b.setStyle(sh));
        b.setOnMouseExited (e -> b.setStyle(s));
        return b;
    }

    private Label makeDivider() {
        Label d = new Label("|");
        d.setStyle("-fx-text-fill: " + BORDER + "; -fx-font-size: 20px;");
        return d;
    }

    public static void main(String[] args) { launch(args); }
}