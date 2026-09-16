package com.marketscout.service;

import com.marketscout.model.AlertRule;

/**
 * Strategy pattern interface for polymorphic alert rule evaluation.
 */
public interface AlertEvaluator {
    boolean isTriggered(double currentPrice, AlertRule rule);
}
