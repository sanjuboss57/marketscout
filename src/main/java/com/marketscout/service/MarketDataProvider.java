package com.marketscout.service;

import com.marketscout.model.Commodity;
import com.marketscout.model.CommodityPrice;

import java.util.List;

/**
 * Interface contract for market pricing providers, demonstrating Interface Abstraction.
 */
public interface MarketDataProvider {
    CommodityPrice getLatestPrice(Commodity commodity);
    List<CommodityPrice> getPriceHistory(Commodity commodity);
    void updatePrice(Commodity commodity, double newPrice);
}
