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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

@SpringBootApplication
@EnableScheduling
public class MarketScoutApplication {

    private static final Logger log = Logger.getLogger(MarketScoutApplication.class.getName());
    private static final String APP_URL = "http://localhost:18080";
    private static final String APP_NAME = "MarketScout";
    private static final AtomicReference<ApplicationContext> appContext = new AtomicReference<>();

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");

        var executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "spring-main");
            t.setDaemon(false);
            return t;
        });

        executor.submit(() -> {
            ApplicationContext ctx = SpringApplication.run(MarketScoutApplication.class, args);
            appContext.set(ctx);
        });

        waitForServerThenLaunchDesktop();
    }

    // ───────────────────────────────────────────────────────────
    //  Server readiness poll → open JavaFX + browser + system tray
    // ───────────────────────────────────────────────────────────
    private static void waitForServerThenLaunchDesktop() {
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "startup-watchdog");
            t.setDaemon(true);
            return t;
        });

        var httpClient = HttpClient.newHttpClient();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                var req = HttpRequest.newBuilder(URI.create(APP_URL)).GET().build();
                var resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() < 500 && appContext.get() != null) {
                    scheduler.shutdown();

                    // Launch Native JavaFX Desktop UI
                    launchJavaFX(appContext.get());

                    // Transition to AWT event thread for System Tray and Desktop Mode
                    EventQueue.invokeLater(() -> {
                        launchDesktopWindow();
                        installSystemTray();
                    });
                }
            } catch (IOException | InterruptedException ignored) {
                // Server not yet ready
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    // ───────────────────────────────────────────────────────────
    //  Launch JavaFX Native Desktop UI (BorderPane, StackPane, etc.)
    // ───────────────────────────────────────────────────────────
    public static void launchJavaFX(ApplicationContext context) {
        if (context == null) return;
        try {
            javafx.application.Platform.startup(() -> {
                try {
                    var commodityService = context.getBean(CommodityDataService.class);
                    var alertService = context.getBean(AlertEngineService.class);
                    var dbService = context.getBean(DatabaseService.class);
                    var externalService = context.getBean(ExternalMarketDataService.class);

                    var stage = new javafx.stage.Stage();
                    new MarketScoutJavaFXApp(commodityService, alertService, dbService, externalService).start(stage);
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

                    var stage = new javafx.stage.Stage();
                    new MarketScoutJavaFXApp(commodityService, alertService, dbService, externalService).start(stage);
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
        String[] candidatePaths = new String[] {
            System.getenv("ProgramFiles(x86)") != null ? System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe" : null,
            System.getenv("ProgramFiles") != null ? System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe" : null,
            System.getenv("ProgramFiles") != null ? System.getenv("ProgramFiles") + "\\Google\\Chrome\\Application\\chrome.exe" : null,
            System.getenv("ProgramFiles(x86)") != null ? System.getenv("ProgramFiles(x86)") + "\\Google\\Chrome\\Application\\chrome.exe" : null,
            System.getenv("LOCALAPPDATA") != null ? System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe" : null,
            System.getenv("LOCALAPPDATA") != null ? System.getenv("LOCALAPPDATA") + "\\Microsoft\\Edge\\Application\\msedge.exe" : null,
            System.getenv("ProgramFiles") != null ? System.getenv("ProgramFiles") + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe" : null
        };

        String browserExe = null;
        for (String p : candidatePaths) {
            if (p != null) {
                java.io.File file = new java.io.File(p);
                if (file.exists() && file.canExecute()) {
                    browserExe = p;
                    break;
                }
            }
        }

        if (browserExe != null) {
            try {
                String userHome = System.getProperty("user.home", ".");
                java.io.File profileDir = new java.io.File(userHome, ".marketscout/desktop-profile");
                if (!profileDir.exists()) {
                    profileDir.mkdirs();
                }

                ProcessBuilder pb = new ProcessBuilder(
                    browserExe,
                    "--app=" + APP_URL,
                    "--window-size=1440,900",
                    "--user-data-dir=" + profileDir.getAbsolutePath(),
                    "--app-id=MarketScoutDesktop",
                    "--disable-features=Translate,ChromeWhatsNewUI",
                    "--no-first-run",
                    "--no-default-browser-check"
                );
                pb.start();
                log.info("Launched MarketScout standalone desktop application window via: " + browserExe);
                return;
            } catch (Exception ex) {
                log.warning("Failed to launch standalone app window: " + ex.getMessage());
            }
        }

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI.create(APP_URL));
                log.info("Fallback: Opened browser at " + APP_URL);
            } catch (IOException e) {
                log.severe("Failed to open browser: " + e.getMessage());
            }
        } else {
            log.warning("Desktop browse not supported; navigate manually to " + APP_URL);
        }
    }

    // ───────────────────────────────────────────────────────────
    //  Install a system-tray icon with a context menu
    // ───────────────────────────────────────────────────────────
    private static void installSystemTray() {
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
            tray.remove(tray.getTrayIcons().length > 0 ? tray.getTrayIcons()[0] : null);
            System.exit(0);
        });
        popup.add(quitItem);

        var trayIcon = new TrayIcon(trayImage, APP_NAME, popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener((ActionEvent e) -> launchJavaFX(appContext.get())); // double-click opens JavaFX

        try {
            tray.add(trayIcon);
            trayIcon.displayMessage(
                    APP_NAME + " Desktop Terminal",
                    "JavaFX Client & Spring Server active at " + APP_URL,
                    TrayIcon.MessageType.INFO
            );
            log.info("System tray icon installed.");
        } catch (AWTException e) {
            log.severe("Could not add tray icon: " + e.getMessage());
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
