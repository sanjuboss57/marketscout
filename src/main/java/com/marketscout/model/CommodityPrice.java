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
public class CommodityPrice {
    private Commodity commodity;
    private double currentPrice;
    private double previousPrice;
    private double changePercent;
    private Instant timestamp;
}
