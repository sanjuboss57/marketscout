package com.marketscout.service;

import com.marketscout.model.AlertCondition;
import com.marketscout.model.AlertNotification;
import com.marketscout.model.AlertRule;
import com.marketscout.model.Commodity;
import com.marketscout.model.CommodityPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertEngineServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private DatabaseService databaseService;

    private ThresholdAlertEvaluator alertEvaluator = new ThresholdAlertEvaluator();

    private AlertEngineService alertEngineService;

    @BeforeEach
    void setUp() {
        alertEngineService = new AlertEngineService(messagingTemplate, databaseService, alertEvaluator);
    }

    @Test
    void testCreateAndGetActiveRules() {
        AlertRule rule = alertEngineService.createRule(Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2400.00);

        assertNotNull(rule.getId());
        assertEquals(Commodity.GOLD, rule.getCommodity());
        assertEquals(AlertCondition.GREATER_THAN_OR_EQUAL, rule.getCondition());
        assertEquals(2400.00, rule.getTargetPrice());
        assertFalse(rule.isTriggered());

        List<AlertRule> active = alertEngineService.getActiveRules();
        assertEquals(1, active.size());
        assertEquals(rule.getId(), active.get(0).getId());
    }

    @Test
    void testDeleteRule() {
        AlertRule rule = alertEngineService.createRule(Commodity.SILVER, AlertCondition.LESS_THAN_OR_EQUAL, 25.00);
        assertTrue(alertEngineService.deleteRule(rule.getId()));
        assertTrue(alertEngineService.getActiveRules().isEmpty());
    }

    @Test
    void testEvaluatePriceTriggersGreaterAlert() {
        AlertRule rule = alertEngineService.createRule(Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2360.00);

        CommodityPrice belowPrice = CommodityPrice.builder()
                .commodity(Commodity.GOLD)
                .currentPrice(2355.00)
                .previousPrice(2350.00)
                .changePercent(0.21)
                .timestamp(Instant.now())
                .build();

        alertEngineService.evaluatePrice(belowPrice);
        assertFalse(rule.isTriggered());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));

        CommodityPrice breachedPrice = CommodityPrice.builder()
                .commodity(Commodity.GOLD)
                .currentPrice(2362.50)
                .previousPrice(2355.00)
                .changePercent(0.53)
                .timestamp(Instant.now())
                .build();

        alertEngineService.evaluatePrice(breachedPrice);
        assertTrue(rule.isTriggered());

        ArgumentCaptor<AlertNotification> captor = ArgumentCaptor.forClass(AlertNotification.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/alerts"), captor.capture());

        AlertNotification notification = captor.getValue();
        assertEquals(rule.getId(), notification.getAlertId());
        assertEquals(Commodity.GOLD, notification.getCommodity());
        assertEquals(2362.50, notification.getTriggeredPrice());
        assertEquals(2360.00, notification.getTargetPrice());

        // Subsequent price should not re-trigger
        alertEngineService.evaluatePrice(breachedPrice);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
    }

    @Test
    void testEvaluatePriceTriggersLesserAlert() {
        AlertRule rule = alertEngineService.createRule(Commodity.CRUDE_OIL, AlertCondition.LESS_THAN_OR_EQUAL, 80.00);

        CommodityPrice breachedPrice = CommodityPrice.builder()
                .commodity(Commodity.CRUDE_OIL)
                .currentPrice(79.50)
                .previousPrice(81.00)
                .changePercent(-3.05)
                .timestamp(Instant.now())
                .build();

        alertEngineService.evaluatePrice(breachedPrice);
        assertTrue(rule.isTriggered());
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
    }
}
