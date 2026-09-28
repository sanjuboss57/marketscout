package com.marketscout.model;

import lombok.Getter;

@Getter
public enum Commodity {
    // ── Traditional Commodities & Metals ──
    GOLD("XAU", "USD/oz", 2350.00, AssetCategory.COMMODITY),
    SILVER("XAG", "USD/oz", 28.50, AssetCategory.COMMODITY),
    COFFEE("KC", "USD/lb", 2.20, AssetCategory.COMMODITY),
    CRUDE_OIL("CL", "USD/bbl", 82.00, AssetCategory.COMMODITY),

    // ── Cryptocurrencies & Digital Assets ──
    BITCOIN("BTC", "USD", 64500.00, AssetCategory.CRYPTO),
    ETHEREUM("ETH", "USD", 3450.00, AssetCategory.CRYPTO),
    SOLANA("SOL", "USD", 145.00, AssetCategory.CRYPTO),

    // ── Tech Equities & Stocks ──
    APPLE("AAPL", "USD/share", 225.00, AssetCategory.STOCK),
    NVIDIA("NVDA", "USD/share", 125.00, AssetCategory.STOCK),
    TESLA("TSLA", "USD/share", 245.00, AssetCategory.STOCK);

    private final String code;
    private final String unit;
    private final double basePrice;
    private final AssetCategory category;

    Commodity(String code, String unit, double basePrice, AssetCategory category) {
        this.code = code;
        this.unit = unit;
        this.basePrice = basePrice;
        this.category = category;
    }

    public String getDisplayName() {
        return name().replace("_", " ");
    }
}
