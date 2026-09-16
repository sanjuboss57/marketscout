package com.marketscout.controller;

import com.marketscout.model.AlertCondition;
import com.marketscout.model.AlertRule;
import com.marketscout.model.Commodity;
import com.marketscout.service.AlertEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertEngineService alertEngineService;

    @MockBean
    private com.marketscout.service.DatabaseService databaseService;

    @Test
    void testGetActiveAlerts() throws Exception {
        AlertRule rule = AlertRule.builder()
                .id("test-rule-1")
                .commodity(Commodity.GOLD)
                .condition(AlertCondition.GREATER_THAN_OR_EQUAL)
                .targetPrice(2400.00)
                .triggered(false)
                .createdAt(Instant.now())
                .build();

        when(alertEngineService.getActiveRules()).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/alerts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value("test-rule-1"))
                .andExpect(jsonPath("$[0].commodity").value("GOLD"));
    }

    @Test
    void testCreateAlertSuccess() throws Exception {
        AlertRule created = AlertRule.builder()
                .id("test-rule-new")
                .commodity(Commodity.SILVER)
                .condition(AlertCondition.LESS_THAN_OR_EQUAL)
                .targetPrice(25.00)
                .triggered(false)
                .createdAt(Instant.now())
                .build();

        when(alertEngineService.createRule(Commodity.SILVER, AlertCondition.LESS_THAN_OR_EQUAL, 25.00))
                .thenReturn(created);

        String json = """
                {
                    "commodity": "SILVER",
                    "condition": "LESS_THAN_OR_EQUAL",
                    "targetPrice": 25.00
                }
                """;

        mockMvc.perform(post("/api/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("test-rule-new"))
                .andExpect(jsonPath("$.commodity").value("SILVER"));
    }

    @Test
    void testDeleteAlertFound() throws Exception {
        when(alertEngineService.deleteRule("test-rule-1")).thenReturn(true);

        mockMvc.perform(delete("/api/alerts/test-rule-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteAlertNotFound() throws Exception {
        when(alertEngineService.deleteRule("unknown-id")).thenReturn(false);

        mockMvc.perform(delete("/api/alerts/unknown-id"))
                .andExpect(status().isNotFound());
    }
}
