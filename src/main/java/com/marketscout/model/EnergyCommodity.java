package com.marketscout.model;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Concrete implementation of AbstractCommodity for energy markets (Crude Oil).
 */
public class EnergyCommodity extends AbstractCommodity {

    public EnergyCommodity(String code, String displayName, String unit, double basePrice, double volatility) {
        super(code, displayName, unit, basePrice, volatility);
    }

    @Override
    public double simulateNextPrice(double currentPrice) {
        // High volatility momentum drift
        double momentum = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.2 * volatility * currentPrice;
        return Math.max(currentPrice + momentum, basePrice * 0.3);
    }
}
