package com.prashant.KhetWallah.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

public record DashboardPickup(
        Long orderId,
        Long listingId,
        String produceName,
        String farmerName,
        String buyerName,
        String pickupArea,
        Instant pickupAt,
        BigDecimal quantityKg,
        BigDecimal totalAmount,
        String role
) {}
