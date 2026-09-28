package com.marketscout.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioHolding {
    private Long id;
    private Long userId;
    private Commodity commodity;
    private double quantity;
    private double avgBuyPrice;
    private double currentPrice;
    private double currentValue;
    private double totalCost;
    private double unrealizedPnl;
    private double unrealizedPnlPercent;
    private Instant updatedAt;
}
