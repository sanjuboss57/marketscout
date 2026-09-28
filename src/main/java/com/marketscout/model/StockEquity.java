package com.marketscout.model;

import lombok.Getter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Advanced OOP Concept: Concrete class for Tech Equities & Stocks (Apple, Nvidia, Tesla).
 * Extends AbstractCommodity and models corporate valuation drift and equity market sentiment.
 */
@Getter
public class StockEquity extends AbstractCommodity {

    private final String sector;
    private final double peRatio;
    private final double dividendYield;

    public StockEquity(String code, String displayName, String unit, double basePrice, double volatility,
                       String sector, double peRatio, double dividendYield) {
        super(code, displayName, unit, basePrice, volatility);
        this.sector = sector;
        this.peRatio = peRatio;
        this.dividendYield = dividendYield;
    }

    @Override
    public double simulateNextPrice(double currentPrice) {
        // Stock equity simulation with secular growth bias and equity volatility
        double secularDrift = 0.0003 * currentPrice;
        double noise = (ThreadLocalRandom.current().nextDouble() - 0.495) * 1.9 * volatility * currentPrice;
        double next = currentPrice + secularDrift + noise;
        return Math.max(next, basePrice * 0.35);
    }
}
