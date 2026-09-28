package com.marketscout.service;

import com.marketscout.model.AlertCondition;
import com.marketscout.model.AlertNotification;
import com.marketscout.model.AlertRule;
import com.marketscout.model.Commodity;
import com.marketscout.model.CommodityPrice;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class AlertEngineService {

    private final SimpMessagingTemplate messagingTemplate;
    private final DatabaseService databaseService;
    private final AlertEvaluator alertEvaluator;
    private final List<AlertRule> rules = new CopyOnWriteArrayList<>();

    public AlertEngineService(SimpMessagingTemplate messagingTemplate,
                              DatabaseService databaseService,
                              AlertEvaluator alertEvaluator) {
        this.messagingTemplate = messagingTemplate;
        this.databaseService = databaseService;
        this.alertEvaluator = alertEvaluator;
    }

    @PostConstruct
    public void loadAlertsFromDatabase() {
        try {
            List<AlertRule> dbRules = databaseService.getAllAlerts();
            rules.addAll(dbRules);
            log.info("Loaded {} alert rules from SQLite database.", dbRules.size());
        } catch (Exception e) {
            log.warn("Could not preload alerts from SQLite: {}", e.getMessage());
        }
    }

    // CRUD: CREATE
    public AlertRule createRule(Commodity commodity, AlertCondition condition, double targetPrice) {
        AlertRule rule = AlertRule.builder()
                .id(UUID.randomUUID().toString())
                .commodity(commodity)
                .condition(condition)
                .targetPrice(targetPrice)
                .triggered(false)
                .createdAt(Instant.now())
                .build();
        rules.add(rule);
        databaseService.createAlert(rule, 1);
        log.info("Created alert rule: {}", rule);
        return rule;
    }

    public AlertRule createRule(AlertRule rule) {
        if (rule.getId() == null || rule.getId().isBlank()) {
            rule.setId(UUID.randomUUID().toString());
        }
        if (rule.getCreatedAt() == null) {
            rule.setCreatedAt(Instant.now());
        }
        rule.setTriggered(false);
        rules.add(rule);
        databaseService.createAlert(rule, 1);
        log.info("Created alert rule from object: {}", rule);
        return rule;
    }

    // CRUD: READ
    public List<AlertRule> getActiveRules() {
        return rules.stream()
                .filter(rule -> !rule.isTriggered())
                .toList();
    }

    public List<AlertRule> getAllRules() {
        return Collections.unmodifiableList(rules);
    }

    // CRUD: UPDATE
    public AlertRule updateRule(String id, double newTargetPrice, AlertCondition newCondition) {
        for (AlertRule rule : rules) {
            if (rule.getId().equals(id)) {
                rule.setTargetPrice(newTargetPrice);
                if (newCondition != null) {
                    rule.setCondition(newCondition);
                }
                rule.setTriggered(false);
                databaseService.updateAlert(id, newTargetPrice, rule.getCondition());
                log.info("Updated alert rule id: {} to targetPrice={}", id, newTargetPrice);
                return rule;
            }
        }
        return null;
    }

    // CRUD: DELETE
    public boolean deleteRule(String id) {
        boolean removed = rules.removeIf(rule -> rule.getId().equals(id));
        if (removed) {
            databaseService.deleteAlert(id);
            log.info("Deleted alert rule id: {}", id);
        }
        return removed;
    }

    @SuppressWarnings("null")
    public void evaluatePrice(CommodityPrice price) {
        if (price == null || price.getCommodity() == null) {
            return;
        }

        for (AlertRule rule : rules) {
            if (rule.isTriggered() || rule.getCommodity() != price.getCommodity()) {
                continue;
            }

            // Using polymorphic strategy interface
            boolean breached = alertEvaluator.isTriggered(price.getCurrentPrice(), rule);

            if (breached) {
                rule.setTriggered(true);
                databaseService.createAlert(rule, 1); // update triggered status in SQLite

                String conditionText = rule.getCondition() == AlertCondition.GREATER_THAN_OR_EQUAL
                        ? ">= target"
                        : "<= target";

                String message = String.format("%s price reached %.2f %s (%s %.2f %s)",
                        rule.getCommodity().name(),
                        price.getCurrentPrice(),
                        rule.getCommodity().getUnit(),
                        conditionText,
                        rule.getTargetPrice(),
                        rule.getCommodity().getUnit());

                AlertNotification notification = AlertNotification.builder()
                        .alertId(rule.getId())
                        .commodity(rule.getCommodity())
                        .triggeredPrice(price.getCurrentPrice())
                        .targetPrice(rule.getTargetPrice())
                        .message(message)
                        .timestamp(Instant.now())
                        .build();

                log.info("Alert triggered: {}", message);
                messagingTemplate.convertAndSend("/topic/alerts", notification);
            }
        }
    }
}
