package com.marketscout.service;

import com.marketscout.model.Commodity;
import com.marketscout.model.CommodityPrice;
import com.marketscout.model.TechnicalAnalysis;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TechnicalAnalysisService {

    private final CommodityDataService commodityDataService;

    public TechnicalAnalysisService(CommodityDataService commodityDataService) {
        this.commodityDataService = commodityDataService;
    }

    public TechnicalAnalysis analyze(Commodity commodity) {
        CommodityPrice latest = commodityDataService.getLatestPrice(commodity);
        if (latest == null) {
            return null;
        }

        List<CommodityPrice> history = commodityDataService.getPriceHistory(commodity);
        double currentPrice = latest.getCurrentPrice();

        double sma7 = calculateSMA(history, 7, currentPrice);
        double sma14 = calculateSMA(history, 14, currentPrice);
        double rsi = calculateRSI(history, 14);

        // Determine signal and badge color
        String signal;
        String color;
        String summary;

        if (rsi <= 32.0) {
            signal = "STRONG BUY (OVERSOLD)";
            color = "#10b981";
            summary = String.format("RSI is oversold at %.1f. High probability mean-reversion rebound detected.", rsi);
        } else if (rsi >= 68.0) {
            signal = "STRONG SELL (OVERBOUGHT)";
            color = "#ef4444";
            summary = String.format("RSI is overbought at %.1f. Short-term exhaustion risk detected.", rsi);
        } else if (sma7 > sma14 && currentPrice >= sma7) {
            signal = "BULLISH MOMENTUM";
            color = "#00d4a0";
            summary = String.format("SMA-7 ($%.2f) crosses above SMA-14 ($%.2f). Upward trend confirmed.", sma7, sma14);
        } else if (sma7 < sma14 && currentPrice <= sma7) {
            signal = "BEARISH TREND";
            color = "#f43f5e";
            summary = String.format("SMA-7 ($%.2f) is below SMA-14 ($%.2f). Downward price pressure active.", sma7, sma14);
        } else {
            signal = "NEUTRAL ACCUMULATION";
            color = "#38bdf8";
            summary = "Consolidating near fair value band. Watch for volume breakout.";
        }

        double volatilityScore = Math.abs(latest.getChangePercent());

        return TechnicalAnalysis.builder()
                .commodity(commodity)
                .currentPrice(currentPrice)
                .sma7(round2(sma7))
                .sma14(round2(sma14))
                .rsi14(round2(rsi))
                .signal(signal)
                .signalColor(color)
                .volatilityScore(round2(volatilityScore))
                .summary(summary)
                .calculatedAt(Instant.now())
                .build();
    }

    public List<TechnicalAnalysis> analyzeAll() {
        List<TechnicalAnalysis> list = new ArrayList<>();
        for (Commodity c : Commodity.values()) {
            TechnicalAnalysis ta = analyze(c);
            if (ta != null) {
                list.add(ta);
            }
        }
        return list;
    }

    private double calculateSMA(List<CommodityPrice> history, int period, double fallback) {
        if (history == null || history.isEmpty()) {
            return fallback;
        }
        int count = Math.min(history.size(), period);
        double sum = 0.0;
        int startIndex = history.size() - count;
        for (int i = startIndex; i < history.size(); i++) {
            sum += history.get(i).getCurrentPrice();
        }
        return sum / count;
    }

    private double calculateRSI(List<CommodityPrice> history, int period) {
        if (history == null || history.size() < 2) {
            return 50.0; // Default neutral RSI
        }

        int count = Math.min(history.size() - 1, period);
        double totalGain = 0.0;
        double totalLoss = 0.0;
        int startIndex = history.size() - 1 - count;

        for (int i = startIndex; i < history.size() - 1; i++) {
            double change = history.get(i + 1).getCurrentPrice() - history.get(i).getCurrentPrice();
            if (change > 0) {
                totalGain += change;
            } else {
                totalLoss += Math.abs(change);
            }
        }

        if (totalLoss == 0) {
            return 100.0;
        }
        if (totalGain == 0) {
            return 0.0;
        }

        double avgGain = totalGain / count;
        double avgLoss = totalLoss / count;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private double round2(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
