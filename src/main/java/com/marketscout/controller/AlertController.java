package com.marketscout.controller;

import com.marketscout.model.AlertRule;
import com.marketscout.service.AlertEngineService;
import com.marketscout.service.DatabaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
public class AlertController {

    private final AlertEngineService alertEngineService;
    private final DatabaseService databaseService;

    public AlertController(AlertEngineService alertEngineService, DatabaseService databaseService) {
        this.alertEngineService = alertEngineService;
        this.databaseService = databaseService;
    }

    // READ
    @GetMapping
    public ResponseEntity<List<AlertRule>> getActiveAlerts() {
        List<AlertRule> activeRules = alertEngineService.getActiveRules();
        return ResponseEntity.ok(activeRules);
    }

    // CREATE
    @PostMapping
    public ResponseEntity<AlertRule> createAlert(@RequestBody AlertRule rule) {
        if (rule.getCommodity() == null || rule.getCondition() == null) {
            return ResponseEntity.badRequest().build();
        }
        AlertRule createdRule = alertEngineService.createRule(
                rule.getCommodity(),
                rule.getCondition(),
                rule.getTargetPrice()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRule);
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<AlertRule> updateAlert(@PathVariable String id, @RequestBody AlertRule updateData) {
        AlertRule updated = alertEngineService.updateRule(id, updateData.getTargetPrice(), updateData.getCondition());
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable String id) {
        boolean deleted = alertEngineService.deleteRule(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ───────────────────────────────────────────────────────────
    //  CRUD: Watchlist Endpoints (Relational SQLite)
    // ───────────────────────────────────────────────────────────

    @GetMapping("/watchlist")
    public ResponseEntity<List<Map<String, Object>>> getWatchlist(@RequestParam(defaultValue = "1") int userId) {
        return ResponseEntity.ok(databaseService.getWatchlist(userId));
    }

    @PostMapping("/watchlist")
    public ResponseEntity<Map<String, Object>> addToWatchlist(@RequestBody Map<String, String> body) {
        int userId = Integer.parseInt(body.getOrDefault("userId", "1"));
        String code = body.get("commodityCode");
        String notes = body.getOrDefault("notes", "");
        int id = databaseService.addToWatchlist(userId, code, notes);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "status", "created"));
    }

    @PutMapping("/watchlist/{id}")
    public ResponseEntity<Map<String, String>> updateWatchlistNote(@PathVariable int id, @RequestBody Map<String, String> body) {
        String newNote = body.getOrDefault("notes", "");
        boolean updated = databaseService.updateWatchlistNote(id, newNote);
        if (updated) {
            return ResponseEntity.ok(Map.of("status", "updated"));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/watchlist/{id}")
    public ResponseEntity<Void> deleteWatchlistItem(@PathVariable int id) {
        boolean deleted = databaseService.deleteFromWatchlist(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
