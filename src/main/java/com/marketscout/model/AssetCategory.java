package com.marketscout.model;

import lombok.Getter;

/**
 * Asset categorization for Commodities, Cryptocurrencies, and Tech Equities.
 */
@Getter
public enum AssetCategory {
    COMMODITY("Commodities & Metals", "🛢️"),
    CRYPTO("Cryptocurrency & Digital Assets", "⚡"),
    STOCK("Tech Equities & Stocks", "📈");

    private final String title;
    private final String icon;

    AssetCategory(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }
}
