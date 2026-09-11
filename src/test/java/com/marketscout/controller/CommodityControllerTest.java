package com.marketscout.controller;

import com.marketscout.model.Commodity;
import com.marketscout.model.CommodityPrice;
import com.marketscout.service.CommodityDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommodityController.class)
class CommodityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommodityDataService commodityDataService;

    @Test
    void testGetLatestPrices() throws Exception {
        CommodityPrice gold = CommodityPrice.builder()
                .commodity(Commodity.GOLD)
                .currentPrice(2350.00)
                .previousPrice(2350.00)
                .changePercent(0.0)
                .timestamp(Instant.now())
                .build();

        when(commodityDataService.getLatestPrice(Commodity.GOLD)).thenReturn(gold);

        mockMvc.perform(get("/api/commodities")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGetPriceHistorySuccess() throws Exception {
        CommodityPrice gold = CommodityPrice.builder()
                .commodity(Commodity.GOLD)
                .currentPrice(2350.00)
                .previousPrice(2350.00)
                .changePercent(0.0)
                .timestamp(Instant.now())
                .build();

        when(commodityDataService.getPriceHistory(Commodity.GOLD)).thenReturn(List.of(gold));

        mockMvc.perform(get("/api/commodities/XAU/history")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].commodity").value("GOLD"))
                .andExpect(jsonPath("$[0].currentPrice").value(2350.00));
    }

    @Test
    void testGetPriceHistoryNotFound() throws Exception {
        mockMvc.perform(get("/api/commodities/UNKNOWN/history")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
