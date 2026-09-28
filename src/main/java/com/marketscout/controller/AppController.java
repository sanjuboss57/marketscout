package com.marketscout.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/app")
@CrossOrigin(origins = "*")
public class AppController {

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getAppInfo() {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        return ResponseEntity.ok(Map.of(
                "name", "MarketScout Desktop",
                "version", "1.0.0",
                "mode", "Desktop Terminal Application",
                "uptimeSeconds", uptime / 1000,
                "usedMemoryMb", usedMemory,
                "totalMemoryMb", totalMemory,
                "os", System.getProperty("os.name")
        ));
    }

    @PostMapping("/shutdown")
    public ResponseEntity<Map<String, String>> shutdownApp() {
        log.info("Shutdown requested from desktop application client.");
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            log.info("Closing desktop server now. Goodbye!");
            System.exit(0);
        }, 500, TimeUnit.MILLISECONDS);

        return ResponseEntity.ok(Map.of(
                "status", "shutting_down",
                "message", "MarketScout Desktop Application is closing..."
        ));
    }
}
