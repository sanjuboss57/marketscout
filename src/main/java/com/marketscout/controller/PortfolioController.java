package com.marketscout.controller;

import com.marketscout.model.Commodity;
import com.marketscout.model.PortfolioSummary;
import com.marketscout.service.PortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
@CrossOrigin(origins = "*")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private static final long DEFAULT_USER_ID = 1L;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ResponseEntity<PortfolioSummary> getPortfolioSummary() {
        return ResponseEntity.ok(portfolioService.getSummary(DEFAULT_USER_ID));
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buyAsset(@RequestBody Map<String, Object> req) {
        try {
            String commStr = (String) req.get("commodity");
            double quantity = Double.parseDouble(req.get("quantity").toString());
            Commodity commodity = Commodity.valueOf(commStr);
            PortfolioSummary summary = portfolioService.buy(DEFAULT_USER_ID, commodity, quantity);
            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to execute buy order: " + e.getMessage()));
        }
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sellAsset(@RequestBody Map<String, Object> req) {
        try {
            String commStr = (String) req.get("commodity");
            double quantity = Double.parseDouble(req.get("quantity").toString());
            Commodity commodity = Commodity.valueOf(commStr);
            PortfolioSummary summary = portfolioService.sell(DEFAULT_USER_ID, commodity, quantity);
            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to execute sell order: " + e.getMessage()));
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Map<String, Object>>> getTransactions() {
        return ResponseEntity.ok(portfolioService.getTransactions(DEFAULT_USER_ID));
    }
}
