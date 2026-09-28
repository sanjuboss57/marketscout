package com.marketscout.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSummary {
    private Long userId;
    private double cashBalance;
    private double totalInvested;
    private double totalHoldingValue;
    private double totalNetWorth;
    private double totalUnrealizedPnl;
    private double totalUnrealizedPnlPercent;
    private List<PortfolioHolding> holdings;
}
