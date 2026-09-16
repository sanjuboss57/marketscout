package com.marketscout.service;

import com.marketscout.model.AlertCondition;
import com.marketscout.model.AlertRule;
import org.springframework.stereotype.Component;

/**
 * Concrete implementation of AlertEvaluator strategy for threshold rules.
 */
@Component
public class ThresholdAlertEvaluator implements AlertEvaluator {

    @Override
    public boolean isTriggered(double currentPrice, AlertRule rule) {
        if (rule == null || rule.isTriggered()) {
            return false;
        }

        if (rule.getCondition() == AlertCondition.GREATER_THAN_OR_EQUAL) {
            return currentPrice >= rule.getTargetPrice();
        } else if (rule.getCondition() == AlertCondition.LESS_THAN_OR_EQUAL) {
            return currentPrice <= rule.getTargetPrice();
        }
        return false;
    }
}
