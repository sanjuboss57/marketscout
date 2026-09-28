package com.marketscout.controller;

import com.marketscout.model.Commodity;
import com.marketscout.model.TechnicalAnalysis;
import com.marketscout.service.TechnicalAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market/analysis")
@CrossOrigin(origins = "*")
public class TechnicalAnalysisController {

    private final TechnicalAnalysisService technicalAnalysisService;

    public TechnicalAnalysisController(TechnicalAnalysisService technicalAnalysisService) {
        this.technicalAnalysisService = technicalAnalysisService;
    }

    @GetMapping
    public ResponseEntity<List<TechnicalAnalysis>> getAllAnalyses() {
        return ResponseEntity.ok(technicalAnalysisService.analyzeAll());
    }

    @GetMapping("/{commodity}")
    public ResponseEntity<TechnicalAnalysis> getAnalysis(@PathVariable String commodity) {
        try {
            Commodity c = Commodity.valueOf(commodity.toUpperCase());
            TechnicalAnalysis ta = technicalAnalysisService.analyze(c);
            if (ta == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
