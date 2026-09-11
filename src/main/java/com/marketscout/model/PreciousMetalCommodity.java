package com.marketscout.model;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Concrete implementation of AbstractCommodity for precious metals (Gold, Silver).
 * Features mean-reversion stochastic movement.
 */
public class PreciousMetalCommodity extends AbstractCommodity {

    public PreciousMetalCommodity(String code, String displayName, String unit, double basePrice, double volatility) {
        super(code, displayName, unit, basePrice, volatility);
    }

    @Override
    public double simulateNextPrice(double currentPrice) {
        // Mean reversion pull towards basePrice + brownian noise
        double meanPull = (basePrice - currentPrice) * 0.05;
        double noise = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0 * volatility * currentPrice;
        double next = currentPrice + meanPull + noise;
        return Math.max(next, basePrice * 0.5);
    }
}
