package com.marketscout.model;

import lombok.Getter;

/**
 * Advanced OOP Concept: Abstract class demonstrating abstraction, inheritance,
 * and template method pattern for simulated commodity price generation.
 */
@Getter
public abstract class AbstractCommodity implements FinancialInstrument {

    protected final String code;
    protected final String displayName;
    protected final String unit;
    protected final double basePrice;
    protected final double volatility;

    public AbstractCommodity(String code, String displayName, String unit, double basePrice, double volatility) {
        this.code = code;
        this.displayName = displayName;
        this.unit = unit;
        this.basePrice = basePrice;
        this.volatility = volatility;
    }

    /**
     * Abstract method implemented by concrete commodity classes based on commodity type.
     */
    public abstract double simulateNextPrice(double currentPrice);

    /**
     * Concrete method shared by all commodities.
     */
    public double calculateSpread(double price) {
        return price * 0.0005; // 5 bps standard liquidity spread
    }

    @Override
    public String formatPrice(double price) {
        return String.format("$%,.2f %s", price, unit);
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - Base: $%.2f", displayName, code, basePrice);
    }
}
