package com.marketscout.model;

/**
 * Advanced OOP Concept: Interface defining contract for all tradable market instruments.
 */
public interface FinancialInstrument {
    String getCode();
    String getDisplayName();
    String getUnit();
    double getBasePrice();
    String formatPrice(double price);
}
