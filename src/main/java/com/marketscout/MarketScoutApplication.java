package com.marketscout;

import com.marketscout.service.AlertEngineService;
import com.marketscout.service.CommodityDataService;
import com.marketscout.service.DatabaseService;
import com.marketscout.service.ExternalMarketDataService;
import com.marketscout.ui.MarketScoutJavaFXApp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

@SpringBootApplication
@EnableScheduling
public class MarketScoutApplication {

    private static final Logger log = Logger.getLogger(MarketScoutApplication.class.getName());
    private static final String APP_URL = "http://localhost:18080";
    private static final String APP_NAME = "MarketScout";
    private static final AtomicReference<ApplicationContext> appContext = new AtomicReference<>();
    private static volatile javafx.stage.Stage fxPrimaryStage;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        ApplicationContext ctx = null;
        try {
            ctx = SpringApplication.run(MarketScoutApplication.class, args);
            appContext.set(ctx);
        } catch (Throwable t) {
            log.severe("Failed to start Spring Boot engine: " + t.getMessage());
            t.printStackTrace();
            return;
        }

        // Launch Standalone Desktop Application Window & System Tray
        try {
            EventQueue.invokeLater(() -> {
                try {
                    launchDesktopWindow();
                } catch (Throwable t) {
                    log.warning("Could not launch desktop window: " + t.getMessage());
                }
                try {
                    installSystemTray();
                } catch (Throwable t) {
                    log.warning("Could not install system tray: " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            log.warning("Could not schedule AWT tasks: " + t.getMessage());
        }

        // Keep main thread alive for the lifetime of the desktop process
        try {
            while (true) {
                Thread.sleep(10000);
            }
        } catch (InterruptedException ignored) {}
    }

    // ───────────────────────────────────────────────────────────
    //  Launch JavaFX Native Desktop UI (BorderPane, StackPane, etc.)
    // ───────────────────────────────────────────────────────────
    public static void launchJavaFX(ApplicationContext context) {
        if (context == null) return;
        if (fxPrimaryStage != null) {
            javafx.application.Platform.runLater(() -> {
                fxPrimaryStage.show();
                fxPrimaryStage.toFront();
                fxPrimaryStage.requestFocus();
            });
            return;
        }

        try {
            javafx.application.Platform.startup(() -> {
                try {
                    javafx.application.Platform.setImplicitExit(false);
                    var commodityService = context.getBean(CommodityDataService.class);
                    var alertService = context.getBean(AlertEngineService.class);
                    var dbService = context.getBean(DatabaseService.class);
                    var externalService = context.getBean(ExternalMarketDataService.class);

                    fxPrimaryStage = new javafx.stage.Stage();
                    new MarketScoutJavaFXApp(commodityService, alertService, dbService, externalService).start(fxPrimaryStage);
                    log.info("JavaFX Native Desktop Terminal UI started successfully.");
                } catch (Exception e) {
                    log.warning("Could not launch JavaFX stage: " + e.getMessage());
                }
            });
        } catch (IllegalStateException alreadyStarted) {
            javafx.application.Platform.runLater(() -> {
                try {
                    var commodityService = context.getBean(CommodityDataService.class);
                    var alertService = context.getBean(AlertEngineService.class);
                    var dbService = context.getBean(DatabaseService.class);
                    var externalService = context.getBean(ExternalMarketDataService.class);

                    fxPrimaryStage = new javafx.stage.Stage();
                    new MarketScoutJavaFXApp(commodityService, alertService, dbService, externalService).start(fxPrimaryStage);
                } catch (Exception ex) {
                    log.warning("Could not launch JavaFX stage: " + ex.getMessage());
                }
            });
        }
    }

    // ───────────────────────────────────────────────────────────
    //  Launch standalone native desktop application window
    // ───────────────────────────────────────────────────────────
    private static void launchDesktopWindow() {
        String[] edgeCandidates = {
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"
        };
        for (String path : edgeCandidates) {
            if (new java.io.File(path).exists()) {
                try {
                    new ProcessBuilder(path, "--app=" + APP_URL).start();
                    log.info("Launched MarketScout standalone desktop application window via Edge at: " + path);
                    return;
                } catch (Exception e) {
                    log.warning("Could not launch via " + path + ": " + e.getMessage());
                }
            }
        }

        // Fallback: system browse
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI.create(APP_URL));
                log.info("Fallback: Opened default browser at " + APP_URL);
                return;
            } catch (IOException e) {
                log.severe("Failed to open browser: " + e.getMessage());
            }
        }

        try {
            new ProcessBuilder("cmd", "/c", "start", APP_URL).start();
        } catch (Exception ignored) {}
    }

    // ───────────────────────────────────────────────────────────
    //  Install a system-tray icon with a context menu
    // ───────────────────────────────────────────────────────────
    private static void installSystemTray() {
        try {
            if (!SystemTray.isSupported()) {
                log.warning("System tray is not supported on this platform.");
                return;
            }

            Image trayImage = loadTrayIcon();
            var tray = SystemTray.getSystemTray();
            var popup = new PopupMenu();

            // "Open JavaFX Native UI" item
            var openJavaFXItem = new MenuItem("🖥️  Open JavaFX Native UI");
            openJavaFXItem.addActionListener((ActionEvent e) -> launchJavaFX(appContext.get()));
            popup.add(openJavaFXItem);

            // "Open Web Dashboard" item
            var openItem = new MenuItem("📈  Open Web Terminal Window");
            openItem.addActionListener((ActionEvent e) -> launchDesktopWindow());
            popup.add(openItem);

            popup.addSeparator();

            // "Quit" item
            var quitItem = new MenuItem("✖  Quit MarketScout");
            quitItem.addActionListener((ActionEvent e) -> {
                try {
                    tray.remove(tray.getTrayIcons().length > 0 ? tray.getTrayIcons()[0] : null);
                } catch (Exception ignored) {}
                System.exit(0);
            });
            popup.add(quitItem);

            var trayIcon = new TrayIcon(trayImage, APP_NAME, popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener((ActionEvent e) -> launchJavaFX(appContext.get()));

            tray.add(trayIcon);
            log.info("System tray icon installed.");
            try {
                trayIcon.displayMessage(
                        APP_NAME + " Desktop Terminal",
                        "JavaFX Client & Spring Server active at " + APP_URL,
                        TrayIcon.MessageType.INFO
                );
            } catch (Exception ignored) {}
        } catch (Throwable t) {
            log.warning("Could not setup system tray: " + t.getMessage());
        }
    }

    private static Image loadTrayIcon() {
        try (var is = MarketScoutApplication.class.getResourceAsStream("/static/images/icon.png")) {
            if (is != null) {
                return Toolkit.getDefaultToolkit().createImage(is.readAllBytes());
            }
        } catch (IOException e) {
            log.fine("icon.png not found, using default");
        }
        var img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setColor(new Color(0x00d4a0));
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        return img;
    }
}
