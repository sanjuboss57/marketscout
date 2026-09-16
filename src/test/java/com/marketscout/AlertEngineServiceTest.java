package com.marketscout;

import com.marketscout.model.AlertCondition;
import com.marketscout.model.AlertNotification;
import com.marketscout.model.AlertRule;
import com.marketscout.model.Commodity;
import com.marketscout.model.CommodityPrice;
import com.marketscout.service.AlertEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

/**
 * Comprehensive integration-style unit tests for AlertEngineService.
 * Placed at the top-level package (com.marketscout) as requested.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertEngineService - Alert Trigger Verification")
public class AlertEngineServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.marketscout.service.DatabaseService databaseService;

    private com.marketscout.service.ThresholdAlertEvaluator alertEvaluator = new com.marketscout.service.ThresholdAlertEvaluator();

    private AlertEngineService alertEngineService;

    @BeforeEach
    void setUp() {
        alertEngineService = new AlertEngineService(messagingTemplate, databaseService, alertEvaluator);
    }

    // ─────────────────────────────────────────────
    // Rule lifecycle
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Rule Management")
    class RuleManagement {

        @Test
        @DisplayName("createRule assigns UUID and stores as active")
        void createRule_assignsUuidAndStoresAsActive() {
            AlertRule rule = alertEngineService.createRule(
                    Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2400.00);

            assertNotNull(rule.getId(), "Rule ID must be a non-null UUID");
            assertFalse(rule.isTriggered(), "Newly created rule must not be triggered");
            assertNotNull(rule.getCreatedAt(), "createdAt must be set");
            assertEquals(Commodity.GOLD, rule.getCommodity());
            assertEquals(AlertCondition.GREATER_THAN_OR_EQUAL, rule.getCondition());
            assertEquals(2400.00, rule.getTargetPrice());

            List<AlertRule> active = alertEngineService.getActiveRules();
            assertEquals(1, active.size());
            assertEquals(rule.getId(), active.get(0).getId());
        }

        @Test
        @DisplayName("deleteRule removes rule and returns true; unknown id returns false")
        void deleteRule_removesAndReturnsTrueForKnownId() {
            AlertRule rule = alertEngineService.createRule(
                    Commodity.SILVER, AlertCondition.LESS_THAN_OR_EQUAL, 25.00);

            assertTrue(alertEngineService.deleteRule(rule.getId()));
            assertTrue(alertEngineService.getActiveRules().isEmpty());
            assertFalse(alertEngineService.deleteRule("non-existent-id"),
                    "Deleting an unknown id must return false");
        }

        @Test
        @DisplayName("getActiveRules excludes triggered rules")
        void getActiveRules_excludesTriggeredRules() {
            alertEngineService.createRule(Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2360.00);

            CommodityPrice breach = CommodityPrice.builder()
                    .commodity(Commodity.GOLD)
                    .currentPrice(2365.00)
                    .previousPrice(2350.00)
                    .changePercent(0.64)
                    .timestamp(Instant.now())
                    .build();

            alertEngineService.evaluatePrice(breach);
            assertTrue(alertEngineService.getActiveRules().isEmpty(),
                    "Triggered rule must not appear in active list");
        }

        @Test
        @DisplayName("getAllRules includes both triggered and un-triggered rules")
        void getAllRules_includesTriggeredAndUntriggered() {
            alertEngineService.createRule(Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2360.00);
            alertEngineService.createRule(Commodity.SILVER, AlertCondition.LESS_THAN_OR_EQUAL, 100.00); // won't trigger

            CommodityPrice breach = CommodityPrice.builder()
                    .commodity(Commodity.GOLD)
                    .currentPrice(2370.00)
                    .previousPrice(2350.00)
                    .changePercent(0.85)
                    .timestamp(Instant.now())
                    .build();

            alertEngineService.evaluatePrice(breach);

            List<AlertRule> all = alertEngineService.getAllRules();
            assertEquals(2, all.size());
            assertEquals(1, alertEngineService.getActiveRules().size(),
                    "Only the un-triggered rule should remain active");
        }
    }

    // ─────────────────────────────────────────────
    // Trigger evaluation - GREATER_THAN_OR_EQUAL
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Trigger: GREATER_THAN_OR_EQUAL")
    class GreaterThanOrEqualTrigger {

        @Test
        @DisplayName("does NOT fire when price is below threshold")
        void doesNotFire_whenPriceBelowThreshold() {
            alertEngineService.createRule(Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2400.00);

            alertEngineService.evaluatePrice(priceOf(Commodity.GOLD, 2395.00));

            verify(messagingTemplate, never())
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
        }

        @Test
        @DisplayName("fires EXACTLY once when price equals threshold")
        void firesOnce_whenPriceEqualsThreshold() {
            AlertRule rule = alertEngineService.createRule(
                    Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2400.00);

            alertEngineService.evaluatePrice(priceOf(Commodity.GOLD, 2400.00));
            assertTrue(rule.isTriggered());

            ArgumentCaptor<AlertNotification> captor = ArgumentCaptor.forClass(AlertNotification.class);
            verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/alerts"), captor.capture());

            AlertNotification n = captor.getValue();
            assertEquals(rule.getId(), n.getAlertId());
            assertEquals(Commodity.GOLD, n.getCommodity());
            assertEquals(2400.00, n.getTriggeredPrice());
            assertEquals(2400.00, n.getTargetPrice());
            assertNotNull(n.getMessage());
        }

        @Test
        @DisplayName("fires when price exceeds threshold")
        void fires_whenPriceAboveThreshold() {
            AlertRule rule = alertEngineService.createRule(
                    Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2360.00);

            alertEngineService.evaluatePrice(priceOf(Commodity.GOLD, 2362.50));
            assertTrue(rule.isTriggered());
            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
        }

        @Test
        @DisplayName("does NOT re-fire after initial trigger")
        void doesNotReFire_afterInitialTrigger() {
            alertEngineService.createRule(Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2360.00);
            CommodityPrice breach = priceOf(Commodity.GOLD, 2365.00);

            alertEngineService.evaluatePrice(breach);
            alertEngineService.evaluatePrice(breach);
            alertEngineService.evaluatePrice(breach);

            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
        }
    }

    // ─────────────────────────────────────────────
    // Trigger evaluation - LESS_THAN_OR_EQUAL
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Trigger: LESS_THAN_OR_EQUAL")
    class LessThanOrEqualTrigger {

        @Test
        @DisplayName("does NOT fire when price is above threshold")
        void doesNotFire_whenPriceAboveThreshold() {
            alertEngineService.createRule(Commodity.CRUDE_OIL, AlertCondition.LESS_THAN_OR_EQUAL, 80.00);

            alertEngineService.evaluatePrice(priceOf(Commodity.CRUDE_OIL, 81.00));

            verify(messagingTemplate, never())
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
        }

        @Test
        @DisplayName("fires EXACTLY once when price equals threshold")
        void firesOnce_whenPriceEqualsThreshold() {
            AlertRule rule = alertEngineService.createRule(
                    Commodity.CRUDE_OIL, AlertCondition.LESS_THAN_OR_EQUAL, 80.00);

            alertEngineService.evaluatePrice(priceOf(Commodity.CRUDE_OIL, 80.00));
            assertTrue(rule.isTriggered());
            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
        }

        @Test
        @DisplayName("fires when price drops below threshold")
        void fires_whenPriceDropsBelowThreshold() {
            AlertRule rule = alertEngineService.createRule(
                    Commodity.CRUDE_OIL, AlertCondition.LESS_THAN_OR_EQUAL, 80.00);

            alertEngineService.evaluatePrice(priceOf(Commodity.CRUDE_OIL, 79.50));
            assertTrue(rule.isTriggered());

            ArgumentCaptor<AlertNotification> captor = ArgumentCaptor.forClass(AlertNotification.class);
            verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/alerts"), captor.capture());

            AlertNotification n = captor.getValue();
            assertEquals(Commodity.CRUDE_OIL, n.getCommodity());
            assertEquals(79.50, n.getTriggeredPrice());
            assertEquals(80.00, n.getTargetPrice());
        }

        @Test
        @DisplayName("does NOT re-fire after initial trigger")
        void doesNotReFire_afterInitialTrigger() {
            alertEngineService.createRule(Commodity.CRUDE_OIL, AlertCondition.LESS_THAN_OR_EQUAL, 80.00);
            CommodityPrice breach = priceOf(Commodity.CRUDE_OIL, 78.00);

            alertEngineService.evaluatePrice(breach);
            alertEngineService.evaluatePrice(breach);

            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
        }
    }

    // ─────────────────────────────────────────────
    // Cross-commodity isolation
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-commodity Isolation")
    class CrossCommodityIsolation {

        @Test
        @DisplayName("rule for GOLD does not trigger on SILVER price update")
        void goldRule_doesNotTriggerOnSilverPrice() {
            alertEngineService.createRule(Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2360.00);

            alertEngineService.evaluatePrice(priceOf(Commodity.SILVER, 2370.00)); // wrong commodity

            verify(messagingTemplate, never())
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
        }

        @Test
        @DisplayName("independent rules for different commodities trigger independently")
        void independentRules_triggerIndependently() {
            AlertRule goldRule = alertEngineService.createRule(
                    Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2360.00);
            AlertRule oilRule = alertEngineService.createRule(
                    Commodity.CRUDE_OIL, AlertCondition.LESS_THAN_OR_EQUAL, 80.00);

            alertEngineService.evaluatePrice(priceOf(Commodity.GOLD, 2365.00));

            assertTrue(goldRule.isTriggered());
            assertFalse(oilRule.isTriggered());
            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));

            alertEngineService.evaluatePrice(priceOf(Commodity.CRUDE_OIL, 79.00));

            assertTrue(oilRule.isTriggered());
            verify(messagingTemplate, times(2))
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
        }
    }

    // ─────────────────────────────────────────────
    // Edge cases
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("evaluatePrice with null price does nothing")
        void evaluatePrice_withNullPrice_doesNothing() {
            alertEngineService.createRule(Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2400.00);
            assertDoesNotThrow(() -> alertEngineService.evaluatePrice(null));
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        @DisplayName("evaluatePrice with null commodity does nothing")
        void evaluatePrice_withNullCommodity_doesNothing() {
            alertEngineService.createRule(Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2400.00);

            CommodityPrice badPrice = CommodityPrice.builder()
                    .commodity(null)
                    .currentPrice(9999.00)
                    .timestamp(Instant.now())
                    .build();

            assertDoesNotThrow(() -> alertEngineService.evaluatePrice(badPrice));
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        @DisplayName("multiple rules for same commodity fire independently")
        void multipleRulesForSameCommodity_fireIndependently() {
            AlertRule lowRule = alertEngineService.createRule(
                    Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2355.00);
            AlertRule highRule = alertEngineService.createRule(
                    Commodity.GOLD, AlertCondition.GREATER_THAN_OR_EQUAL, 2400.00);

            alertEngineService.evaluatePrice(priceOf(Commodity.GOLD, 2360.00));

            assertTrue(lowRule.isTriggered(), "Low threshold rule should have triggered");
            assertFalse(highRule.isTriggered(), "High threshold rule should NOT have triggered yet");
            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
        }

        @Test
        @DisplayName("Coffee GREATER_THAN_OR_EQUAL alert fires correctly")
        void coffeeAlert_greaterThanOrEqual_firesCorrectly() {
            AlertRule rule = alertEngineService.createRule(
                    Commodity.COFFEE, AlertCondition.GREATER_THAN_OR_EQUAL, 2.50);

            alertEngineService.evaluatePrice(priceOf(Commodity.COFFEE, 2.20));
            assertFalse(rule.isTriggered());

            alertEngineService.evaluatePrice(priceOf(Commodity.COFFEE, 2.51));
            assertTrue(rule.isTriggered());

            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/alerts"), any(AlertNotification.class));
        }
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private CommodityPrice priceOf(Commodity commodity, double price) {
        return CommodityPrice.builder()
                .commodity(commodity)
                .currentPrice(price)
                .previousPrice(commodity.getBasePrice())
                .changePercent(((price - commodity.getBasePrice()) / commodity.getBasePrice()) * 100.0)
                .timestamp(Instant.now())
                .build();
    }
}
