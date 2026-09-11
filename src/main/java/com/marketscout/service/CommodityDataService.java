package com.marketscout.service;

import com.marketscout.model.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class CommodityDataService implements MarketDataProvider {

    private static final int MAX_HISTORY_POINTS = 50;

    private final AlertEngineService alertEngineService;
    private final SimpMessagingTemplate messagingTemplate;

    private final ConcurrentHashMap<Commodity, CommodityPrice> latestPrices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Commodity, List<CommodityPrice>> priceHistories = new ConcurrentHashMap<>();
    private final Map<Commodity, AbstractCommodity> commodityModels = new EnumMap<>(Commodity.class);

    public CommodityDataService(AlertEngineService alertEngineService, SimpMessagingTemplate messagingTemplate) {
        this.alertEngineService = alertEngineService;
        this.messagingTemplate = messagingTemplate;
        initModels();
        init();
    }

    private void initModels() {
        commodityModels.put(Commodity.GOLD, new PreciousMetalCommodity("XAU", "Gold", "USD/oz", 2350.00, 0.008));
        commodityModels.put(Commodity.SILVER, new PreciousMetalCommodity("XAG", "Silver", "USD/oz", 28.50, 0.015));
        commodityModels.put(Commodity.COFFEE, new AgriculturalCommodity("KC", "Coffee", "USD/lb", 2.20, 0.020));
        commodityModels.put(Commodity.CRUDE_OIL, new EnergyCommodity("CL", "Crude Oil", "USD/bbl", 82.00, 0.018));
    }

    @PostConstruct
    public void init() {
        if (!latestPrices.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Commodity commodity : Commodity.values()) {
            CommodityPrice initialPrice = CommodityPrice.builder()
                    .commodity(commodity)
                    .currentPrice(commodity.getBasePrice())
                    .previousPrice(commodity.getBasePrice())
                    .changePercent(0.0)
                    .timestamp(now)
                    .build();

            latestPrices.put(commodity, initialPrice);
            List<CommodityPrice> history = new CopyOnWriteArrayList<>();
            history.add(initialPrice);
            priceHistories.put(commodity, history);
        }
        log.info("Initialized baseline prices and histories for {} commodities", Commodity.values().length);
    }

    @Scheduled(fixedRate = 2000)
    public void simulatePriceMovement() {
        for (Commodity commodity : Commodity.values()) {
            CommodityPrice current = latestPrices.get(commodity);
            if (current == null) {
                continue;
            }

            // Using polymorphic AbstractCommodity model for simulation
            AbstractCommodity model = commodityModels.get(commodity);
            double nextPriceRaw = model != null 
                    ? model.simulateNextPrice(current.getCurrentPrice())
                    : current.getCurrentPrice();

            BigDecimal bd = BigDecimal.valueOf(nextPriceRaw).setScale(2, RoundingMode.HALF_UP);
            double newPrice = bd.doubleValue();

            double changePercent = ((newPrice - commodity.getBasePrice()) / commodity.getBasePrice()) * 100.0;
            BigDecimal cpBd = BigDecimal.valueOf(changePercent).setScale(2, RoundingMode.HALF_UP);

            CommodityPrice updated = CommodityPrice.builder()
                    .commodity(commodity)
                    .currentPrice(newPrice)
                    .previousPrice(current.getCurrentPrice())
                    .changePercent(cpBd.doubleValue())
                    .timestamp(Instant.now())
                    .build();

            latestPrices.put(commodity, updated);

            List<CommodityPrice> history = priceHistories.get(commodity);
            if (history != null) {
                history.add(updated);
                while (history.size() > MAX_HISTORY_POINTS) {
                    history.remove(0);
                }
            }

            // Publish WebSocket tick
            messagingTemplate.convertAndSend("/topic/prices", updated);

            // Evaluate Alert Rules
            alertEngineService.evaluatePrice(updated);
        }
    }

    @Override
    public CommodityPrice getLatestPrice(Commodity commodity) {
        return latestPrices.get(commodity);
    }

    @Override
    public List<CommodityPrice> getPriceHistory(Commodity commodity) {
        List<CommodityPrice> list = priceHistories.get(commodity);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    @Override
    public void updatePrice(Commodity commodity, double newPrice) {
        CommodityPrice current = latestPrices.get(commodity);
        double prev = current != null ? current.getCurrentPrice() : commodity.getBasePrice();
        double changePercent = ((newPrice - commodity.getBasePrice()) / commodity.getBasePrice()) * 100.0;

        CommodityPrice price = CommodityPrice.builder()
                .commodity(commodity)
                .currentPrice(newPrice)
                .previousPrice(prev)
                .changePercent(BigDecimal.valueOf(changePercent).setScale(2, RoundingMode.HALF_UP).doubleValue())
                .timestamp(Instant.now())
                .build();

        latestPrices.put(commodity, price);
        messagingTemplate.convertAndSend("/topic/prices", price);
        alertEngineService.evaluatePrice(price);
    }
}
