package com.marketscout.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Model representing technical indicators (SMA, RSI, Trend Signal) for an asset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalAnalysis {
    private Commodity commodity;
    private double currentPrice;
    private double sma7;
    private double sma14;
    private double rsi14;
    private String signal;
    private String signalColor; // Hex color for UI badge: e.g. #10b981 (green), #ef4444 (red)
    private double volatilityScore;
    private String summary;
    private Instant calculatedAt;
}
