package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.ListingEntity;

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
        String pickupInstructions,
        String produceName,
        String farmerName,
        String buyerName,
        String photoUrl,
        String pickupArea,
        Instant createdAt
) {
    public static OrderResponse from(OrderEntity order) {
        ListingEntity listing = order.getListing();
        return new OrderResponse(
                order.getId(), listing.getId(),
                order.getQuantityKg(), order.getPricePerKg(),
                order.getTotalAmount(), order.getStatus(),
                order.getPickupAt(), order.getPickupInstructions(),
                listing.getProduceName(), listing.getFarmerName(),
                order.getBuyer().getDisplayName(),
                listing.getPhotoFileName() == null ? null
                        : "/api/listings/" + listing.getId() + "/photo",
                listing.getPickupArea(), order.getCreatedAt()
        );
    }
}
