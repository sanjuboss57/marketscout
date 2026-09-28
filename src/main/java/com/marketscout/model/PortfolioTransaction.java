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
public class PortfolioTransaction {
    private Long id;
    private Long userId;
    private Commodity commodity;
    private String action; // BUY or SELL
    private double quantity;
    private double executedPrice;
    private double totalAmount;
    private Instant timestamp;
}
