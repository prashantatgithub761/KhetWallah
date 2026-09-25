package com.prashant.KhetWallah.order;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        Long listingId,
        BigDecimal quantityKg,
        BigDecimal pricePerKg,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant pickupAt,
        String pickupInstructions
) {
    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(
                order.getId(), order.getListing().getId(),
                order.getQuantityKg(), order.getPricePerKg(),
                order.getTotalAmount(), order.getStatus(),
                order.getPickupAt(), order.getPickupInstructions()
        );
    }
}
