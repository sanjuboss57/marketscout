package com.marketscout.model;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Concrete implementation of AbstractCommodity for soft agricultural commodities (Coffee).
 */
public class AgriculturalCommodity extends AbstractCommodity {

    public AgriculturalCommodity(String code, String displayName, String unit, double basePrice, double volatility) {
        super(code, displayName, unit, basePrice, volatility);
    }

    @Override
    public double simulateNextPrice(double currentPrice) {
        // Agricultural jump diffusion
        double delta = (ThreadLocalRandom.current().nextDouble() - 0.49) * volatility * currentPrice;
        return Math.max(currentPrice + delta, basePrice * 0.4);
    }
}
