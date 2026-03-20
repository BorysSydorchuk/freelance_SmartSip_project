package org.myprojects.project_APP;

//import java.net.http.HttpClient;
//import java.net.http.HttpResponse;
//
//import org.json.JSONArray;
//import org.json.JSONObject;
//
//import javafx.application.Application;
//import javafx.scene.Scene;
//import javafx.scene.control.Button;
//import javafx.scene.control.Label;
//import javafx.scene.control.TextField;
//import javafx.scene.layout.HBox;
//import javafx.scene.layout.VBox;
//import javafx.stage.Stage;
//import org.myprojects.DB_task.kul_db_conn;
//
//public class Project_App extends Application {
//
//    // Variables:
//    private HBox mainSceneBox = new HBox(20);
//    private Scene mainScene = new Scene(mainSceneBox);
//
//    @Override
//    public void start(Stage primaryStage){
//
//
//        //Main Scene Element Initialization:
//        mainSceneBox.getChildren().addAll(
//
//        );
//
//        //Scene setup:
//        primaryStage.setScene(mainScene);
//        primaryStage.sizeToScene();
//        primaryStage.setOnCloseRequest(ev -> System.exit(0));
//        primaryStage.show();
//    }
//}

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONObject;


public class Project_App extends Application {

    // ── Replace these with your actual team info ───────────────────────────────
    private static final String BASE_URL    = "https://studev.groept.be/api/a25EE2team203";
    private static final double FULL_VOLUME_ML = 100; // replace with real bottle volume at level 7
    private static final int    MAX_LEVEL      = 7;

    // ── Colours ────────────────────────────────────────────────────────────────
    private static final String BG           = "#0d1117";
    private static final String CARD         = "#161b22";
    private static final String BORDER       = "#30363d";
    private static final String ACCENT_BLUE  = "#58a6ff";
    private static final String ACCENT_COLD  = "#79c0ff";
    private static final String ACCENT_WARM  = "#f0883e";
    private static final String TEXT         = "#e6edf3";
    private static final String MUTED        = "#8b949e";
    private static final String SUCCESS      = "#3fb950";
    private static final String WARNING      = "#d29922";
    private static final String DANGER       = "#f85149";

    // ── State ──────────────────────────────────────────────────────────────────
    private kul_db_conn kul_db;
    private int         currentLevel   = 0;   // 0–7 from bottle sensor
    private double      bottleTemp     = 0.0; // °C from bottle sensor
    private boolean     isColdSelected = true;

    // ── UI nodes that are updated dynamically ─────────────────────────────────
    private Label   levelValueLabel;
    private Label   tempValueLabel;
    private Label   mlToAddLabel;
    private Label   statusLabel;
    private VBox    levelBarContainer;
    private Button  refillButton;

    @Override
    public void start(Stage primaryStage) {
        kul_db = new kul_db_conn();
        primaryStage.setTitle("SmartSip");

        // ── Root layout ───────────────────────────────────────────────────────
        VBox root = new VBox(24);
        root.setStyle("-fx-background-color: " + BG + ";");
        root.setPadding(new Insets(32));
        root.setAlignment(Pos.TOP_CENTER);
        root.setPrefWidth(420);

        // ── Title ─────────────────────────────────────────────────────────────
        Label title = new Label("SmartSip");
        title.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label subtitle = new Label("Refill Station");
        subtitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 13px;");

        VBox titleBox = new VBox(4);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.getChildren().addAll(title, subtitle);

        // ── Bottle status card ────────────────────────────────────────────────
        VBox statusCard = makeCard();

        Label statusCardTitle = new Label("Bottle Status");
        statusCardTitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        // Water level visual bar --- ???
        levelBarContainer = new VBox(3);
        levelBarContainer.setAlignment(Pos.BOTTOM_CENTER);
        levelBarContainer.setPrefHeight(80);

        // Level and temperature info row
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

        // Amount to add
        mlToAddLabel = new Label("Press refresh to read bottle");
        mlToAddLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");
        mlToAddLabel.setWrapText(true);

        // Refresh button
        Button refreshButton = makeSecondaryButton("↻  Refresh Bottle Data");
        refreshButton.setOnAction(e -> fetchBottleData());

        statusCard.getChildren().addAll(statusCardTitle, levelBarContainer, statsRow, mlToAddLabel, refreshButton);

        // ── Temperature choice card ───────────────────────────────────────────
        VBox tempChoiceCard = makeCard();

        Label tempChoiceTitle = new Label("Water Temperature");
        tempChoiceTitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        ToggleGroup tempGroup = new ToggleGroup();

        ToggleButton coldBtn = new ToggleButton("❄  Cold");
        ToggleButton warmBtn = new ToggleButton("☀  Warm");
        coldBtn.setToggleGroup(tempGroup);
        warmBtn.setToggleGroup(tempGroup);
        coldBtn.setSelected(true);

        String toggleBase =
                "-fx-font-size: 13px; -fx-padding: 10 28; -fx-cursor: hand; " +
                        "-fx-border-radius: 6; -fx-background-radius: 6;";
        coldBtn.setStyle(toggleBase +
                "-fx-background-color: " + ACCENT_COLD + "; -fx-text-fill: " + BG + "; -fx-font-weight: bold;");
        warmBtn.setStyle(toggleBase +
                "-fx-background-color: " + CARD + "; -fx-text-fill: " + MUTED + "; -fx-border-color: " + BORDER + ";");

        coldBtn.setOnAction(e -> {
            isColdSelected = true;
            coldBtn.setStyle(toggleBase +
                    "-fx-background-color: " + ACCENT_COLD + "; -fx-text-fill: " + BG + "; -fx-font-weight: bold;");
            warmBtn.setStyle(toggleBase +
                    "-fx-background-color: " + CARD + "; -fx-text-fill: " + MUTED + "; -fx-border-color: " + BORDER + ";");
        });
        warmBtn.setOnAction(e -> {
            isColdSelected = false;
            warmBtn.setStyle(toggleBase +
                    "-fx-background-color: " + ACCENT_WARM + "; -fx-text-fill: " + BG + "; -fx-font-weight: bold;");
            coldBtn.setStyle(toggleBase +
                    "-fx-background-color: " + CARD + "; -fx-text-fill: " + MUTED + "; -fx-border-color: " + BORDER + ";");
        });

        HBox toggleRow = new HBox(12);
        toggleRow.setAlignment(Pos.CENTER);
        toggleRow.getChildren().addAll(coldBtn, warmBtn);

        tempChoiceCard.getChildren().addAll(tempChoiceTitle, toggleRow);

        // ── Refill button ─────────────────────────────────────────────────────
        refillButton = new Button("Request Refill");
        refillButton.setStyle(
                "-fx-background-color: " + ACCENT_BLUE + "; -fx-text-fill: " + BG + "; " +
                        "-fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 14 40; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; -fx-pref-width: 340px;");
        refillButton.setOnMouseEntered(e -> refillButton.setStyle(
                "-fx-background-color: #79c0ff; -fx-text-fill: " + BG + "; " +
                        "-fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 14 40; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; -fx-pref-width: 340px;"));
        refillButton.setOnMouseExited(e -> refillButton.setStyle(
                "-fx-background-color: " + ACCENT_BLUE + "; -fx-text-fill: " + BG + "; " +
                        "-fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 14 40; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; -fx-pref-width: 340px;"));
        refillButton.setOnAction(e -> requestRefill());

        // ── Status message ────────────────────────────────────────────────────
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");
        statusLabel.setWrapText(true);

        // ── Assemble ──────────────────────────────────────────────────────────
        root.getChildren().addAll(titleBox, statusCard, tempChoiceCard, refillButton, statusLabel);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        primaryStage.setOnCloseRequest(e -> System.exit(0));
        primaryStage.show();
    }

    // ── Fetch current bottle level and temperature from the database ───────────
    // The bottle Pi writes its sensor readings to the DB; we read the latest row.
    private void fetchBottleData() {
        try {
            HttpClient client = HttpClient.newHttpClient();

            // Replace "getBottleStatus" with your actual endpoint that returns
            // the latest row from the bottle sensor table.
            // Expected JSON: [{"level": 3, "temperature": 21.5}]
            String url = BASE_URL + "/getBottleStatus"; // replace endpoint
            HttpResponse<String> response = kul_db.makeGETRequest(client, url);


            JSONArray array = new JSONArray(response.body());
            if (array.length() == 0) {
                setStatus("No bottle data found.", WARNING);
                return;
            }

            JSONObject latest = array.getJSONObject(0);
            currentLevel = latest.getInt("level");
            bottleTemp   = latest.getDouble("temperature");  // replace key if different

            updateBottleDisplay();
            setStatus("Bottle data refreshed.", SUCCESS);

        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Failed to fetch bottle data.", DANGER);
        }
    }

    // ── Update all visual elements based on currentLevel and bottleTemp ────────
    private void updateBottleDisplay() {
        levelValueLabel.setText(currentLevel + " / " + MAX_LEVEL);
        tempValueLabel.setText(String.format("%.1f °C", bottleTemp));

        // Rebuild the level bar: 7 segments, filled ones are blue
        levelBarContainer.getChildren().clear();
        // Draw from top to bottom: segment 7 at top, segment 1 at bottom
        for (int i = MAX_LEVEL; i >= 1; i--) {
            Rectangle seg = new Rectangle(220, 8);
            seg.setArcWidth(4);
            seg.setArcHeight(4);
            if (i <= currentLevel) {
                // filled — colour shifts from light blue (low) to deep blue (full)
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
            double mlToAdd = calculateMlToAdd(currentLevel);
            mlToAddLabel.setText(String.format("%.0f ml needed to reach full (level 7)", mlToAdd));
            mlToAddLabel.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12px;");
            refillButton.setDisable(false);
        }
    }

    // ── Calculate how many ml are needed to go from currentLevel to 7 ──────────
    private double calculateMlToAdd(int level) {
        // Level is linear 0–7, volume at level 7 = FULL_VOLUME_ML
        double mlPerLevel = FULL_VOLUME_ML / MAX_LEVEL;
        return (MAX_LEVEL - level) * mlPerLevel;
    }

    // ── Write refill request to DB and wait for station Pi to confirm done ─────
    private void requestRefill() {
        // Guard: force user to refresh first so currentLevel is real
        if (currentLevel == 0) {
            setStatus("Please refresh bottle data first.", WARNING);
            return;
        }

        double mlToAddInColdTank = 0, mlToAddInHotTank = 0;
        if (isColdSelected) {
            mlToAddInColdTank = calculateMlToAdd(currentLevel);
        } else {
            mlToAddInHotTank = calculateMlToAdd(currentLevel);
        }

        // Print so you can see what is actually being sent
        System.out.println("Sending: cold=" + mlToAddInColdTank + " warm=" + mlToAddInHotTank);

        refillButton.setDisable(true);
        setStatus("Sending refill request...", MUTED);

        try {
            HttpClient client = HttpClient.newHttpClient();

            // FIX: format doubles to whole numbers so the URL is clean e.g. /357/0
            String insertUrl = BASE_URL + "/insertRefillRequest"
                    + "/" + String.format("%.0f", mlToAddInColdTank)
                    + "/" + String.format("%.0f", mlToAddInHotTank);

            System.out.println(insertUrl);
            System.out.println("URL: " + insertUrl); // so you can verify in the console
            kul_db.makeGETRequest(client, insertUrl);

            setStatus("Refill request sent. Waiting for station to complete...", ACCENT_BLUE);
            pollForCompletion(client);

        } catch (Exception e) {
            e.printStackTrace();
            setStatus("Error sending refill request.", DANGER);
            refillButton.setDisable(false);
        }
    }
//    private void requestRefill() {
////        if (currentLevel == 0) {                              --- if we have empty bottle and want to
////            setStatus("Refresh bottle data first.", WARNING); --- fill it in, then with this if statement
////            return;                                           --- it would crash
////        }
//
//        // I changed code over here a bit to make it do calculations using variables of both
//        // tanks for further tasks
//        double mlToAddInColdTank = 0, mlToAddInHotTank = 0;
//        if(isColdSelected){
//            mlToAddInColdTank = calculateMlToAdd(currentLevel);
//        }
//        else mlToAddInHotTank = calculateMlToAdd(currentLevel);
//
//        refillButton.setDisable(true); // --- not clear for me when do we enable it after disabling
//        setStatus("Sending refill request...", MUTED);
//
//        try {
//            HttpClient client = HttpClient.newHttpClient();
//
//            // ── Step 1: Write the refill request to the DB ────────────────────
//            // The station Pi polls this table and starts the pump when it sees a new row.
//            // Replace "insertRefillRequest" with your actual insert endpoint.
//            // Parameters: mlToAddInTank1, mlToAddInTank2
//            String insertUrl = BASE_URL + "/insertRefillRequest"  // replace endpoint
//                    + "/" + mlToAddInColdTank
//                    + "/" + mlToAddInHotTank;
//            kul_db.makeGETRequest(client, insertUrl);
//
//            // I don't really need this part as I will fill this database through MySQL directly
////            // ── Step 2: Log this refill in the refill history table ───────────
////            // Columns: timestamp (auto by DB), level_before, ml_added, tank_used
////            // Replace "insertRefillLog" with your actual log insert endpoint.
////            String logUrl = BASE_URL + "/insertRefillLog"         // replace endpoint
////                    + "/" + currentLevel
////                    + "/" + String.format("%.0f", mlToAdd)
////                    + "/" + tankChoice;
////            kul_db.makeGETRequest(client, logUrl);
//
//            setStatus("Refill request sent. Waiting for station to complete...", ACCENT_BLUE);
//
//            // ── Step 3: Poll the DB until the station Pi marks the refill done ─
//            // The station Pi sets a "status" column to "done" when the pump stops.
//            // Replace "getRefillStatus" with your actual endpoint.
//            // Expected JSON: [{"status": "done"}] or [{"status": "pending"}]
//            pollForCompletion(client);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            setStatus("Error sending refill request.", DANGER);
//            refillButton.setDisable(false);
//        }
//    }

    // ── Poll DB every 2 seconds until station Pi sets status to "done" ─────────
    private void pollForCompletion(HttpClient client) {
        // We use a background thread so the UI doesn't freeze during polling
        Thread pollThread = new Thread(() -> {
            int maxAttempts = 30; // give up after 60 seconds (30 × 2s)
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                try {
                    Thread.sleep(2000);

                    // Replace "getRefillStatus" with your actual endpoint
                    // Expected JSON: [{"RefillResponse": "done"}] or [{"RefillResponse": "pending"}]
                    String statusUrl = BASE_URL + "/getRefillStatus";
                    HttpResponse<String> resp = kul_db.makeGETRequest(client, statusUrl);

                    JSONArray arr = new JSONArray(resp.body());
                    if (arr.length() > 0) {
                        String status = arr.getJSONObject(0).getString("RefillResponse"); // replace key if different
                        if (status.equals("done")) {
                            // Back to UI thread to update labels
                            javafx.application.Platform.runLater(() -> {
                                setStatus("Refill complete! Refresh to see new level.", SUCCESS);
                                refillButton.setDisable(false);
                            });
                            return;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Timeout
            javafx.application.Platform.runLater(() -> {
                setStatus("Timed out waiting for station. Check manually.", WARNING);
                refillButton.setDisable(false);
            });
        });
        pollThread.setDaemon(true); // thread dies when app closes
        pollThread.start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void setStatus(String message, String color) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px;");
    }

    private VBox makeCard() {
        VBox card = new VBox(14);
        card.setStyle(
                "-fx-background-color: " + CARD + "; " +
                        "-fx-border-color: " + BORDER + "; " +
                        "-fx-border-radius: 10; -fx-background-radius: 10; " +
                        "-fx-padding: 20;");
        card.setPrefWidth(356);
        return card;
    }

    private Button makeSecondaryButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-background-color: " + CARD + "; -fx-text-fill: " + TEXT + "; " +
                        "-fx-font-size: 12px; -fx-padding: 8 18; " +
                        "-fx-border-color: " + BORDER + "; -fx-border-radius: 6; -fx-background-radius: 6; " +
                        "-fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: #21262d; -fx-text-fill: " + TEXT + "; " +
                        "-fx-font-size: 12px; -fx-padding: 8 18; " +
                        "-fx-border-color: " + ACCENT_BLUE + "; -fx-border-radius: 6; -fx-background-radius: 6; " +
                        "-fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: " + CARD + "; -fx-text-fill: " + TEXT + "; " +
                        "-fx-font-size: 12px; -fx-padding: 8 18; " +
                        "-fx-border-color: " + BORDER + "; -fx-border-radius: 6; -fx-background-radius: 6; " +
                        "-fx-cursor: hand;"));
        return btn;
    }

    private Label makeDivider() {
        Label d = new Label("|");
        d.setStyle("-fx-text-fill: " + BORDER + "; -fx-font-size: 20px;");
        return d;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
