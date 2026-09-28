package com.marketscout.service;

import com.marketscout.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PortfolioService {

    private final DatabaseService databaseService;
    private final CommodityDataService commodityDataService;

    public PortfolioService(DatabaseService databaseService, CommodityDataService commodityDataService) {
        this.databaseService = databaseService;
        this.commodityDataService = commodityDataService;
    }

    public synchronized PortfolioSummary getSummary(long userId) {
        double cash = databaseService.getCashBalance(userId);
        List<Map<String, Object>> rawHoldings = databaseService.getRawHoldings(userId);
        List<PortfolioHolding> holdings = new ArrayList<>();

        double totalInvested = 0.0;
        double totalHoldingValue = 0.0;

        for (Map<String, Object> raw : rawHoldings) {
            String commName = (String) raw.get("commodity");
            try {
                Commodity commodity = Commodity.valueOf(commName);
                double qty = (double) raw.get("quantity");
                double avgBuyPrice = (double) raw.get("avgBuyPrice");
                String updateStr = (String) raw.get("updatedAt");
                Instant updatedAt = updateStr != null ? Instant.parse(updateStr) : Instant.now();

                CommodityPrice latest = commodityDataService.getLatestPrice(commodity);
                double currentPrice = latest != null ? latest.getCurrentPrice() : commodity.getBasePrice();

                double totalCost = qty * avgBuyPrice;
                double currentValue = qty * currentPrice;
                double pnl = currentValue - totalCost;
                double pnlPercent = totalCost > 0 ? (pnl / totalCost) * 100.0 : 0.0;

                totalInvested += totalCost;
                totalHoldingValue += currentValue;

                holdings.add(PortfolioHolding.builder()
                        .id((Long) raw.get("id"))
                        .userId(userId)
                        .commodity(commodity)
                        .quantity(round4(qty))
                        .avgBuyPrice(round2(avgBuyPrice))
                        .currentPrice(round2(currentPrice))
                        .currentValue(round2(currentValue))
                        .totalCost(round2(totalCost))
                        .unrealizedPnl(round2(pnl))
                        .unrealizedPnlPercent(round2(pnlPercent))
                        .updatedAt(updatedAt)
                        .build());
            } catch (Exception e) {
                log.warn("Skipping unknown holding commodity: {}", commName);
            }
        }

        double totalNetWorth = cash + totalHoldingValue;
        double totalPnl = totalHoldingValue - totalInvested;
        double totalPnlPercent = totalInvested > 0 ? (totalPnl / totalInvested) * 100.0 : 0.0;

        return PortfolioSummary.builder()
                .userId(userId)
                .cashBalance(round2(cash))
                .totalInvested(round2(totalInvested))
                .totalHoldingValue(round2(totalHoldingValue))
                .totalNetWorth(round2(totalNetWorth))
                .totalUnrealizedPnl(round2(totalPnl))
                .totalUnrealizedPnlPercent(round2(totalPnlPercent))
                .holdings(holdings)
                .build();
    }

    public synchronized PortfolioSummary buy(long userId, Commodity commodity, double quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        CommodityPrice latest = commodityDataService.getLatestPrice(commodity);
        double price = latest != null ? latest.getCurrentPrice() : commodity.getBasePrice();
        double totalCost = quantity * price;

        double currentCash = databaseService.getCashBalance(userId);
        if (currentCash < totalCost) {
            throw new IllegalArgumentException(String.format("Insufficient cash ($%,.2f). Required: $%,.2f", currentCash, totalCost));
        }

        // Deduct cash
        databaseService.updateCashBalance(userId, currentCash - totalCost);

        // Find existing holding
        List<Map<String, Object>> rawHoldings = databaseService.getRawHoldings(userId);
        double existingQty = 0.0;
        double existingAvg = 0.0;

        for (Map<String, Object> r : rawHoldings) {
            if (commodity.name().equals(r.get("commodity"))) {
                existingQty = (double) r.get("quantity");
                existingAvg = (double) r.get("avgBuyPrice");
                break;
            }
        }

        double newQty = existingQty + quantity;
        double newAvg = ((existingQty * existingAvg) + totalCost) / newQty;

        databaseService.saveOrUpdateHolding(userId, commodity, newQty, newAvg);
        databaseService.saveTransaction(userId, commodity, "BUY", quantity, price, totalCost);
        log.info("User {} bought {} units of {} at ${}", userId, quantity, commodity, price);

        return getSummary(userId);
    }

    public synchronized PortfolioSummary sell(long userId, Commodity commodity, double quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        List<Map<String, Object>> rawHoldings = databaseService.getRawHoldings(userId);
        double existingQty = 0.0;
        double existingAvg = 0.0;
        boolean found = false;

        for (Map<String, Object> r : rawHoldings) {
            if (commodity.name().equals(r.get("commodity"))) {
                existingQty = (double) r.get("quantity");
                existingAvg = (double) r.get("avgBuyPrice");
                found = true;
                break;
            }
        }

        if (!found || existingQty < quantity) {
            throw new IllegalArgumentException(String.format("Insufficient holdings to sell. You currently own %,.4f %s.", existingQty, commodity.getUnit()));
        }

        CommodityPrice latest = commodityDataService.getLatestPrice(commodity);
        double price = latest != null ? latest.getCurrentPrice() : commodity.getBasePrice();
        double proceeds = quantity * price;

        // Credit cash
        double currentCash = databaseService.getCashBalance(userId);
        databaseService.updateCashBalance(userId, currentCash + proceeds);

        double remaining = existingQty - quantity;
        if (remaining <= 0.00001) {
            databaseService.deleteHolding(userId, commodity);
        } else {
            databaseService.saveOrUpdateHolding(userId, commodity, remaining, existingAvg);
        }

        databaseService.saveTransaction(userId, commodity, "SELL", quantity, price, proceeds);
        log.info("User {} sold {} units of {} at ${}", userId, quantity, commodity, price);

        return getSummary(userId);
    }

    public List<Map<String, Object>> getTransactions(long userId) {
        return databaseService.getTransactions(userId);
    }

    private double round2(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double round4(double val) {
        return BigDecimal.valueOf(val).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
