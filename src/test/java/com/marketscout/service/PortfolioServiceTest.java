package com.marketscout.service;

import com.marketscout.model.Commodity;
import com.marketscout.model.CommodityPrice;
import com.marketscout.model.PortfolioSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class PortfolioServiceTest {

    @Mock
    private DatabaseService databaseService;

    @Mock
    private CommodityDataService commodityDataService;

    private PortfolioService portfolioService;

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioService(databaseService, commodityDataService);
    }

    @Test
    void testGetSummaryWithEmptyHoldings() {
        when(databaseService.getCashBalance(1L)).thenReturn(100000.00);
        when(databaseService.getRawHoldings(1L)).thenReturn(Collections.emptyList());

        PortfolioSummary summary = portfolioService.getSummary(1L);

        assertNotNull(summary);
        assertEquals(100000.00, summary.getCashBalance());
        assertEquals(0.0, summary.getTotalHoldingValue());
        assertEquals(100000.00, summary.getTotalNetWorth());
        assertEquals(0.0, summary.getTotalUnrealizedPnl());
        assertTrue(summary.getHoldings().isEmpty());
    }

    @Test
    void testBuySuccessUpdatesCashAndHolding() {
        when(databaseService.getCashBalance(1L)).thenReturn(100000.00);
        when(databaseService.getRawHoldings(1L)).thenReturn(Collections.emptyList());
        when(commodityDataService.getLatestPrice(Commodity.BITCOIN)).thenReturn(
                CommodityPrice.builder().commodity(Commodity.BITCOIN).currentPrice(60000.00).build()
        );

        portfolioService.buy(1L, Commodity.BITCOIN, 0.5);

        // Required cost: 0.5 * 60,000 = 30,000. Remaining cash: 70,000
        verify(databaseService).updateCashBalance(1L, 70000.00);
        verify(databaseService).saveOrUpdateHolding(1L, Commodity.BITCOIN, 0.5, 60000.00);
        verify(databaseService).saveTransaction(1L, Commodity.BITCOIN, "BUY", 0.5, 60000.00, 30000.00);
    }

    @Test
    void testBuyFailsWhenInsufficientCash() {
        when(databaseService.getCashBalance(1L)).thenReturn(5000.00);
        when(commodityDataService.getLatestPrice(Commodity.BITCOIN)).thenReturn(
                CommodityPrice.builder().commodity(Commodity.BITCOIN).currentPrice(60000.00).build()
        );

        // Required cost: 1.0 * 60,000 = 60,000 > 5,000
        assertThrows(IllegalArgumentException.class, () -> {
            portfolioService.buy(1L, Commodity.BITCOIN, 1.0);
        });
    }

    @Test
    void testSellSuccessCreditsCash() {
        Map<String, Object> existingHolding = Map.of(
                "id", 1L,
                "commodity", "BITCOIN",
                "quantity", 1.0,
                "avgBuyPrice", 50000.00,
                "updatedAt", "2026-09-28T12:00:00Z"
        );
        when(databaseService.getCashBalance(1L)).thenReturn(50000.00);
        when(databaseService.getRawHoldings(1L)).thenReturn(List.of(existingHolding));
        when(commodityDataService.getLatestPrice(Commodity.BITCOIN)).thenReturn(
                CommodityPrice.builder().commodity(Commodity.BITCOIN).currentPrice(65000.00).build()
        );

        portfolioService.sell(1L, Commodity.BITCOIN, 0.5);

        // Proceeds: 0.5 * 65,000 = 32,500. Cash: 50,000 + 32,500 = 82,500
        verify(databaseService).updateCashBalance(1L, 82500.00);
        verify(databaseService).saveOrUpdateHolding(1L, Commodity.BITCOIN, 0.5, 50000.00);
        verify(databaseService).saveTransaction(1L, Commodity.BITCOIN, "SELL", 0.5, 65000.00, 32500.00);
    }
}
