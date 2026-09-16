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
public class AlertNotification {
    private String alertId;
    private Commodity commodity;
    private double triggeredPrice;
    private double targetPrice;
    private String message;
    private Instant timestamp;
}
