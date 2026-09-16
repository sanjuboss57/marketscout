package com.marketscout.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {
    private String id;
    private Commodity commodity;
    private AlertCondition condition;
    private double targetPrice;
    private boolean triggered;
    private Instant createdAt;
}
