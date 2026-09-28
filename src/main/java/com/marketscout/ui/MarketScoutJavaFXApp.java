package com.marketscout.ui;

import com.marketscout.model.AlertCondition;
import com.marketscout.model.AlertRule;
import com.marketscout.model.Commodity;
import com.marketscout.service.AlertEngineService;
import com.marketscout.service.CommodityDataService;
import com.marketscout.service.DatabaseService;
import com.marketscout.service.ExternalMarketDataService;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JavaFX UI Design, Layout Responsiveness & Concurrency Implementation.
 * 
 * Demonstrates:
 * 1. Panes: BorderPane, StackPane, GridPane, HBox, VBox
 * 2. UI Controls: PasswordField, TextField, TableView, TableColumn, ComboBox,
 * Button, Label, ProgressBar
 * 3. Layout Responsiveness: Width & Height Property Binding
 * 4. Concurrency: Multi-threading background task + Platform.runLater()
 * 5. Complete CRUD Operations wired to SQLite database
 */
@Slf4j
public class MarketScoutJavaFXApp {

    private final CommodityDataService commodityDataService;
    private final AlertEngineService alertEngineService;
    private final DatabaseService databaseService;
    private final ExternalMarketDataService externalMarketDataService;

    // Observable data lists for JavaFX TableViews
    private final ObservableList<CommodityRow> commodityData = FXCollections.observableArrayList();
    private final ObservableList<AlertRuleRow> alertData = FXCollections.observableArrayList();

    private ScheduledExecutorService backgroundExecutor;
    private Label statusLabel;
    private Label forexRatesLabel;

    public MarketScoutJavaFXApp(CommodityDataService commodityDataService,
            AlertEngineService alertEngineService,
            DatabaseService databaseService,
            ExternalMarketDataService externalMarketDataService) {
        this.commodityDataService = commodityDataService;
        this.alertEngineService = alertEngineService;
        this.databaseService = databaseService;
        this.externalMarketDataService = externalMarketDataService;
    }

    public void start(Stage primaryStage) {
        primaryStage.setTitle("MarketScout | JavaFX Desktop Trading Terminal");

        // ───────────────────────────────────────────────────────────
        // StackPane (Layer 1: Dashboard, Layer 2: Login Overlay)
        // ───────────────────────────────────────────────────────────
        StackPane rootStackPane = new StackPane();
        rootStackPane.setStyle("-fx-background-color: #080c14;");

        // Main Application Layout: BorderPane
        BorderPane mainBorderPane = createMainDashboard(primaryStage);

        // Security Authentication Overlay using PasswordField & GridPane
        VBox loginOverlay = createLoginOverlay(rootStackPane);

        rootStackPane.getChildren().addAll(mainBorderPane, loginOverlay);

        Scene scene = new Scene(rootStackPane, 1200, 750);

        // ───────────────────────────────────────────────────────────
        // Layout Responsiveness: Dynamic Property Constraints
        // ───────────────────────────────────────────────────────────
        mainBorderPane.prefWidthProperty().bind(scene.widthProperty());
        mainBorderPane.prefHeightProperty().bind(scene.heightProperty());

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
        primaryStage.toFront();
        primaryStage.requestFocus();

        // ───────────────────────────────────────────────────────────
        // Concurrency: Background Worker Pool for Live Polling
        // ───────────────────────────────────────────────────────────
        startBackgroundPolling();

        primaryStage.setOnCloseRequest(e -> {
            if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
                backgroundExecutor.shutdownNow();
            }
        });
    }

    /**
     * Create Login Overlay with PasswordField and GridPane
     */
    private VBox createLoginOverlay(StackPane parentStack) {
        VBox overlay = new VBox(15);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(8, 12, 20, 0.94);");
        overlay.setPadding(new Insets(30));

        Label title = new Label("MARKETSCOUT TERMINAL AUTHENTICATION");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#10b981"));

        Label subtitle = new Label(
                "Please enter credentials to unlock live commodity terminal and SQLite CRUD access.");
        subtitle.setTextFill(Color.web("#94a3b8"));
        subtitle.setFont(Font.font("Segoe UI", 12));

        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(12);
        formGrid.setAlignment(Pos.CENTER);
        formGrid.setPadding(new Insets(15));
        formGrid.setStyle(
                "-fx-background-color: #0d131f; -fx-border-color: #1e293b; -fx-border-radius: 8; -fx-background-radius: 8;");

        Label userLabel = new Label("Username:");
        userLabel.setTextFill(Color.web("#e2e8f0"));
        TextField userField = new TextField("sanjuboss57");
        userField.setStyle("-fx-background-color: #1a2436; -fx-text-fill: white;");

        Label passLabel = new Label("Password:");
        passLabel.setTextFill(Color.web("#e2e8f0"));
        // JavaFX UI Control: PasswordField
        PasswordField passwordField = new PasswordField();
        passwordField.setText("admin123");
        passwordField.setStyle("-fx-background-color: #1a2436; -fx-text-fill: white;");

        formGrid.add(userLabel, 0, 0);
        formGrid.add(userField, 1, 0);
        formGrid.add(passLabel, 0, 1);
        formGrid.add(passwordField, 1, 1);

        Label errorLabel = new Label("");
        errorLabel.setTextFill(Color.web("#f43f5e"));

        Button loginBtn = new Button("Unlock Terminal");
        loginBtn.setStyle(
                "-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-cursor: hand;");

        loginBtn.setOnAction(e -> {
            String u = userField.getText().trim();
            String p = passwordField.getText().trim();
            boolean valid = databaseService.validateUser(u, p);
            if (valid) {
                // Fade out login overlay
                parentStack.getChildren().remove(overlay);
                log.info("User {} authenticated successfully into JavaFX Terminal.", u);
            } else {
                errorLabel.setText("Invalid credentials. Try: sanjuboss57 / admin123");
            }
        });

        overlay.getChildren().addAll(title, subtitle, formGrid, errorLabel, loginBtn);
        return overlay;
    }

    /**
     * Build Main Dashboard using BorderPane
     */
    private BorderPane createMainDashboard(Stage stage) {
        BorderPane borderPane = new BorderPane();

        // 1. TOP: Header Bar & Live Forex Ticker (HBox)
        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 12, 20));
        topBar.setStyle("-fx-background-color: #0d131f; -fx-border-color: #1e293b; -fx-border-width: 0 0 1 0;");

        Label brand = new Label("MARKETSCOUT");
        brand.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        brand.setTextFill(Color.web("#10b981"));

        Label badge = new Label("JAVAFX 21 NATIVE CLIENT");
        badge.setStyle(
                "-fx-background-color: #064e3b; -fx-text-fill: #34d399; -fx-font-size: 10; -fx-padding: 2 6; -fx-font-weight: bold;");

        forexRatesLabel = new Label("Connecting to External Market API (open.er-api.com)...");
        forexRatesLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-family: 'Consolas'; -fx-font-size: 11;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshForexBtn = new Button("↻ Fetch Live FX");
        refreshForexBtn.setStyle(
                "-fx-background-color: #1e293b; -fx-text-fill: #cbd5e1; -fx-font-size: 11; -fx-cursor: hand;");
        refreshForexBtn.setOnAction(e -> {
            new Thread(() -> {
                externalMarketDataService.fetchAndParseExternalMarketData();
                Platform.runLater(this::updateForexLabel);
            }).start();
        });

        topBar.getChildren().addAll(brand, badge, forexRatesLabel, spacer, refreshForexBtn);
        borderPane.setTop(topBar);

        // 2. CENTER: Split Tables (Commodity TableView + Alert CRUD Form & Table)
        SplitPane centerSplit = new SplitPane();
        centerSplit.setStyle("-fx-background-color: #080c14;");

        // Left Pane: Live Commodity TableView
        VBox leftPane = createCommodityTablePane();

        // Right Pane: Alerts TableView & CRUD Controls
        VBox rightPane = createAlertsCrudPane();

        centerSplit.getItems().addAll(leftPane, rightPane);
        centerSplit.setDividerPositions(0.48);
        borderPane.setCenter(centerSplit);

        // 3. BOTTOM: Status Bar
        HBox bottomBar = new HBox(20);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(6, 15, 6, 15));
        bottomBar.setStyle("-fx-background-color: #0b101b; -fx-border-color: #1a2436; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label(
                "System Initialized • SQLite DB Active (marketscout.db) • ThreadPool: 2 Active Threads");
        statusLabel.setStyle("-fx-text-fill: #64748b; -fx-font-family: 'Consolas'; -fx-font-size: 11;");

        ProgressBar progressBar = new ProgressBar();
        progressBar.setProgress(-1); // indeterminate pulse
        progressBar.setPrefWidth(80);
        progressBar.setPrefHeight(12);

        bottomBar.getChildren().addAll(statusLabel, progressBar);
        borderPane.setBottom(bottomBar);

        return borderPane;
    }

    /**
     * Create Commodity TableView Pane
     */
    private VBox createCommodityTablePane() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: #080c14;");

        Label heading = new Label("REAL-TIME COMMODITY FEED");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        heading.setTextFill(Color.web("#e2e8f0"));

        TableView<CommodityRow> table = new TableView<>(commodityData);
        table.setStyle("-fx-background-color: #0d131f; -fx-border-color: #1e293b;");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<CommodityRow, String> colCode = new TableColumn<>("Symbol");
        colCode.setCellValueFactory(c -> c.getValue().codeProperty);
        colCode.setPrefWidth(70);

        TableColumn<CommodityRow, String> colName = new TableColumn<>("Commodity");
        colName.setCellValueFactory(c -> c.getValue().nameProperty);
        colName.setPrefWidth(100);

        TableColumn<CommodityRow, Number> colPrice = new TableColumn<>("Price (USD)");
        colPrice.setCellValueFactory(c -> c.getValue().priceProperty);
        colPrice.setPrefWidth(100);

        TableColumn<CommodityRow, Number> colChange = new TableColumn<>("Change %");
        colChange.setCellValueFactory(c -> c.getValue().changeProperty);
        colChange.setPrefWidth(90);

        table.getColumns().add(colCode);
        table.getColumns().add(colName);
        table.getColumns().add(colPrice);
        table.getColumns().add(colChange);
        VBox.setVgrow(table, Priority.ALWAYS);

        box.getChildren().addAll(heading, table);
        return box;
    }

    /**
     * Create Alert CRUD Form & TableView Pane
     */
    private VBox createAlertsCrudPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: #080c14;");

        Label heading = new Label("ALERT THRESHOLD ENGINE & SQLITE CRUD");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        heading.setTextFill(Color.web("#e2e8f0"));

        // CRUD Form (GridPane)
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(10));
        form.setStyle("-fx-background-color: #0d131f; -fx-border-color: #1e293b; -fx-border-radius: 6;");

        ComboBox<String> commodityCombo = new ComboBox<>();
        commodityCombo.getItems().addAll("GOLD", "SILVER", "COFFEE", "CRUDE_OIL");
        commodityCombo.setValue("GOLD");
        commodityCombo.setStyle("-fx-background-color: #1a2436; -fx-text-fill: white;");

        ComboBox<String> conditionCombo = new ComboBox<>();
        conditionCombo.getItems().addAll("GREATER_THAN_OR_EQUAL", "LESS_THAN_OR_EQUAL");
        conditionCombo.setValue("GREATER_THAN_OR_EQUAL");
        conditionCombo.setStyle("-fx-background-color: #1a2436; -fx-text-fill: white;");

        TextField targetField = new TextField("2400.00");
        targetField.setPromptText("Target Price");
        targetField.setPrefWidth(90);
        targetField.setStyle("-fx-background-color: #1a2436; -fx-text-fill: white;");

        Button createBtn = new Button("➕ Create (C)");
        createBtn.setStyle(
                "-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        Button updateBtn = new Button("✏️ Update (U)");
        updateBtn.setStyle(
                "-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        Button deleteBtn = new Button("✖ Delete (D)");
        deleteBtn.setStyle(
                "-fx-background-color: #e11d48; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        form.add(new Label("Asset:"), 0, 0);
        form.add(commodityCombo, 1, 0);
        form.add(new Label("Condition:"), 2, 0);
        form.add(conditionCombo, 3, 0);
        form.add(new Label("Target:"), 0, 1);
        form.add(targetField, 1, 1);

        HBox btnBox = new HBox(8, createBtn, updateBtn, deleteBtn);
        form.add(btnBox, 2, 1, 2, 1);

        // TableView for Alerts
        TableView<AlertRuleRow> alertTable = new TableView<>(alertData);
        alertTable.setStyle("-fx-background-color: #0d131f; -fx-border-color: #1e293b;");
        alertTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<AlertRuleRow, String> colAsset = new TableColumn<>("Asset");
        colAsset.setCellValueFactory(a -> a.getValue().commodityProperty);

        TableColumn<AlertRuleRow, String> colCond = new TableColumn<>("Condition");
        colCond.setCellValueFactory(a -> a.getValue().conditionProperty);

        TableColumn<AlertRuleRow, Number> colTarget = new TableColumn<>("Target ($)");
        colTarget.setCellValueFactory(a -> a.getValue().targetProperty);

        TableColumn<AlertRuleRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(a -> a.getValue().statusProperty);

        alertTable.getColumns().add(colAsset);
        alertTable.getColumns().add(colCond);
        alertTable.getColumns().add(colTarget);
        alertTable.getColumns().add(colStatus);
        VBox.setVgrow(alertTable, Priority.ALWAYS);

        // Wire CRUD Actions:
        // CREATE:
        createBtn.setOnAction(e -> {
            try {
                Commodity c = Commodity.valueOf(commodityCombo.getValue());
                AlertCondition cond = AlertCondition.valueOf(conditionCombo.getValue());
                double target = Double.parseDouble(targetField.getText().trim());
                alertEngineService.createRule(c, cond, target);
                refreshAlertTable();
            } catch (Exception ex) {
                log.error("Create Alert failed: {}", ex.getMessage());
            }
        });

        // UPDATE:
        updateBtn.setOnAction(e -> {
            AlertRuleRow selected = alertTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    AlertCondition cond = AlertCondition.valueOf(conditionCombo.getValue());
                    double target = Double.parseDouble(targetField.getText().trim());
                    alertEngineService.updateRule(selected.id, target, cond);
                    refreshAlertTable();
                } catch (Exception ex) {
                    log.error("Update Alert failed: {}", ex.getMessage());
                }
            }
        });

        // DELETE:
        deleteBtn.setOnAction(e -> {
            AlertRuleRow selected = alertTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                alertEngineService.deleteRule(selected.id);
                refreshAlertTable();
            }
        });

        alertTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                commodityCombo.setValue(newVal.commodityProperty.get());
                conditionCombo.setValue(newVal.conditionProperty.get());
                targetField.setText(String.valueOf(newVal.targetProperty.get()));
            }
        });

        box.getChildren().addAll(heading, form, alertTable);
        return box;
    }

    /**
     * Multi-threaded Background Polling Engine
     */
    private void startBackgroundPolling() {
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "javafx-worker-pool");
            t.setDaemon(true);
            return t;
        });

        backgroundExecutor.scheduleAtFixedRate(() -> {
            try {
                // Read latest commodity ticks from service
                Platform.runLater(() -> {
                    refreshCommodityTable();
                    refreshAlertTable();
                    updateForexLabel();
                });
            } catch (Exception e) {
                log.error("Background polling error: {}", e.getMessage());
            }
        }, 500, 1500, TimeUnit.MILLISECONDS);
    }

    private void refreshCommodityTable() {
        commodityData.clear();
        for (Commodity c : Commodity.values()) {
            var price = commodityDataService.getLatestPrice(c);
            if (price != null) {
                commodityData.add(new CommodityRow(
                        c.getCode(),
                        c.getDisplayName(),
                        price.getCurrentPrice(),
                        price.getChangePercent()));
            }
        }
    }

    private void refreshAlertTable() {
        alertData.clear();
        for (AlertRule r : alertEngineService.getAllRules()) {
            alertData.add(new AlertRuleRow(
                    r.getId(),
                    r.getCommodity().name(),
                    r.getCondition().name(),
                    r.getTargetPrice(),
                    r.isTriggered() ? "TRIGGERED" : "ACTIVE"));
        }
    }

    private void updateForexLabel() {
        Map<String, Object> rates = externalMarketDataService.getLatestRates();
        if (rates != null && rates.containsKey("EUR")) {
            forexRatesLabel.setText(String.format("LIVE FOREX JSON: EUR: %.4f | GBP: %.4f | JPY: %.2f | CAD: %.4f [%s]",
                    rates.get("EUR"), rates.get("GBP"), rates.get("JPY"), rates.get("CAD"), rates.get("status")));
        }
    }

    // Table Row Data Models
    public static class CommodityRow {
        public final SimpleStringProperty codeProperty;
        public final SimpleStringProperty nameProperty;
        public final SimpleDoubleProperty priceProperty;
        public final SimpleDoubleProperty changeProperty;

        public CommodityRow(String code, String name, double price, double change) {
            this.codeProperty = new SimpleStringProperty(code);
            this.nameProperty = new SimpleStringProperty(name);
            this.priceProperty = new SimpleDoubleProperty(price);
            this.changeProperty = new SimpleDoubleProperty(change);
        }
    }

    public static class AlertRuleRow {
        public final String id;
        public final SimpleStringProperty commodityProperty;
        public final SimpleStringProperty conditionProperty;
        public final SimpleDoubleProperty targetProperty;
        public final SimpleStringProperty statusProperty;

        public AlertRuleRow(String id, String commodity, String condition, double target, String status) {
            this.id = id;
            this.commodityProperty = new SimpleStringProperty(commodity);
            this.conditionProperty = new SimpleStringProperty(condition);
            this.targetProperty = new SimpleDoubleProperty(target);
            this.statusProperty = new SimpleStringProperty(status);
        }
    }
}
