package com.marketscout.service;

import com.marketscout.model.Commodity;
import com.marketscout.model.CommodityPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CommodityDataServiceTest {

    @Mock
    private AlertEngineService alertEngineService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private CommodityDataService dataService;

    @BeforeEach
    void setUp() {
        dataService = new CommodityDataService(alertEngineService, messagingTemplate);
    }

    @Test
    void testInitPopulatesAllCommodities() {
        for (Commodity commodity : Commodity.values()) {
            CommodityPrice price = dataService.getLatestPrice(commodity);
            assertNotNull(price);
            assertEquals(commodity, price.getCommodity());
            assertEquals(commodity.getBasePrice(), price.getCurrentPrice());

            List<CommodityPrice> history = dataService.getPriceHistory(commodity);
            assertEquals(1, history.size());
            assertEquals(commodity.getBasePrice(), history.get(0).getCurrentPrice());
        }
    }

    @Test
    void testSimulatePriceMovementUpdatesPricesAndEmits() {
        dataService.simulatePriceMovement();

        for (Commodity commodity : Commodity.values()) {
            CommodityPrice updated = dataService.getLatestPrice(commodity);
            assertNotNull(updated);
            assertTrue(updated.getCurrentPrice() > 0);

            List<CommodityPrice> history = dataService.getPriceHistory(commodity);
            assertEquals(2, history.size());
        }

        verify(alertEngineService, times(Commodity.values().length)).evaluatePrice(any(CommodityPrice.class));
        verify(messagingTemplate, times(Commodity.values().length)).convertAndSend(eq("/topic/prices"), any(CommodityPrice.class));
    }

    @Test
    void testRollingHistoryCapsAt50() {
        // Run 55 iterations
        for (int i = 0; i < 55; i++) {
            dataService.simulatePriceMovement();
        }

        for (Commodity commodity : Commodity.values()) {
            List<CommodityPrice> history = dataService.getPriceHistory(commodity);
            assertEquals(50, history.size());
        }
    }
}
