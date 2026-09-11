package com.marketscout.controller;

import com.marketscout.model.Commodity;
import com.marketscout.model.CommodityPrice;
import com.marketscout.service.CommodityDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/commodities")
@CrossOrigin(origins = "*")
public class CommodityController {

    private final CommodityDataService commodityDataService;

    public CommodityController(CommodityDataService commodityDataService) {
        this.commodityDataService = commodityDataService;
    }

    @GetMapping
    public ResponseEntity<List<CommodityPrice>> getLatestPrices() {
        List<CommodityPrice> prices = Arrays.stream(Commodity.values())
                .map(commodityDataService::getLatestPrice)
                .filter(Objects::nonNull)
                .toList();
        return ResponseEntity.ok(prices);
    }

    @GetMapping("/{code}/history")
    public ResponseEntity<List<CommodityPrice>> getPriceHistory(@PathVariable String code) {
        Commodity commodity = resolveCommodity(code);
        if (commodity == null) {
            return ResponseEntity.notFound().build();
        }

        List<CommodityPrice> history = commodityDataService.getPriceHistory(commodity);
        return ResponseEntity.ok(history);
    }

    private Commodity resolveCommodity(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (Commodity c : Commodity.values()) {
            if (c.getCode().equalsIgnoreCase(code) || c.name().equalsIgnoreCase(code)) {
                return c;
            }
        }
        return null;
    }
}
