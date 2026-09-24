package com.marketscout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MarketScoutApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void testIndexHtmlServesAndImportsRequiredLibraries() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cdn.tailwindcss.com")))
                .andExpect(content().string(containsString("chart.js")))
                .andExpect(content().string(containsString("sockjs.min.js")))
                .andExpect(content().string(containsString("stomp.min.js")))
                .andExpect(content().string(containsString("/js/app.js")));
    }

    @Test
    void testAppJsServesAndHasWebSocketSubscriptions() throws Exception {
        mockMvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/ws-market")))
                .andExpect(content().string(containsString("/topic/prices")))
                .andExpect(content().string(containsString("/topic/alerts")))
                .andExpect(content().string(containsString("/api/commodities")))
                .andExpect(content().string(containsString("/api/alerts")));
    }
}
