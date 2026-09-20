package com.marketscout.controller;

import com.marketscout.service.DatabaseService;
import com.marketscout.service.ExternalMarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST Endpoint demonstrating networking & data parsing results.
 */
@RestController
@RequestMapping("/api/external")
@CrossOrigin(origins = "*")
public class ExternalMarketController {

    private final ExternalMarketDataService externalMarketDataService;
    private final DatabaseService databaseService;

    public ExternalMarketController(ExternalMarketDataService externalMarketDataService, DatabaseService databaseService) {
        this.externalMarketDataService = externalMarketDataService;
        this.databaseService = databaseService;
    }

    @GetMapping("/forex")
    public ResponseEntity<Map<String, Object>> getLiveForex() {
        return ResponseEntity.ok(externalMarketDataService.getLatestRates());
    }

    @GetMapping("/snapshot")
    public ResponseEntity<Map<String, Object>> getDatabaseSnapshot() {
        return ResponseEntity.ok(databaseService.getLatestExternalSnapshot());
    }
}
