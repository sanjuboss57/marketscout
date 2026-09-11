package com.marketscout.model;

import lombok.Getter;

@Getter
public enum Commodity {
    GOLD("XAU", "USD/oz", 2350.00),
    SILVER("XAG", "USD/oz", 28.50),
    COFFEE("KC", "USD/lb", 2.20),
    CRUDE_OIL("CL", "USD/bbl", 82.00);

    private final String code;
    private final String unit;
    private final double basePrice;

    Commodity(String code, String unit, double basePrice) {
        this.code = code;
        this.unit = unit;
        this.basePrice = basePrice;
    }

    public String getDisplayName() {
        return name().replace("_", " ");
    }
}
