package com.marketscout.model;

import lombok.Getter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Advanced OOP Concept: Concrete class for Cryptocurrency instruments (Bitcoin, Ethereum, Solana).
 * Extends AbstractCommodity and models 24/7 momentum drift with fat-tailed crypto volatility.
 */
@Getter
public class CryptoAsset extends AbstractCommodity {

    private final String blockchainNetwork;
    private final String marketCapRank;

    public CryptoAsset(String code, String displayName, String unit, double basePrice, double volatility,
                       String blockchainNetwork, String marketCapRank) {
        super(code, displayName, unit, basePrice, volatility);
        this.blockchainNetwork = blockchainNetwork;
        this.marketCapRank = marketCapRank;
    }

    @Override
    public double simulateNextPrice(double currentPrice) {
        // Crypto volatility features dynamic momentum swing and occasional whale volume shocks
        double noise = (ThreadLocalRandom.current().nextDouble() - 0.492) * 2.2 * volatility * currentPrice;
        if (ThreadLocalRandom.current().nextDouble() < 0.04) {
            noise *= 1.8; // Micro-whale liquidity shift
        }
        double next = currentPrice + noise;
        return Math.max(next, basePrice * 0.25);
    }
}
