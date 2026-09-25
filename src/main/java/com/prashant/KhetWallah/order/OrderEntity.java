package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "marketplace_orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private ListingEntity listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private AppUser buyer;

    @Column(name = "quantity_kg", nullable = false,
            precision = 10, scale = 2)
    private BigDecimal quantityKg;

    @Column(name = "price_per_kg", nullable = false,
            precision = 10, scale = 2)
    private BigDecimal pricePerKg;

    @Column(name = "total_amount", nullable = false,
            precision = 20, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderEntity() {
    }

    public OrderEntity(
            ListingEntity listing,
            AppUser buyer,
            BigDecimal quantityKg
    ) {
        this.listing = Objects.requireNonNull(listing);
        this.buyer = Objects.requireNonNull(buyer);
        Objects.requireNonNull(quantityKg);

        if (quantityKg.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Order quantity must be positive"
            );
        }

        this.quantityKg = quantityKg;
        this.pricePerKg = listing.getPricePerKg();
        this.totalAmount = quantityKg.multiply(this.pricePerKg);
    }

    @PrePersist
    private void setCreationTime() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ListingEntity getListing() {
        return listing;
    }

    public AppUser getBuyer() {
        return buyer;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public BigDecimal getPricePerKg() {
        return pricePerKg;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Column(name = "pickup_at")
    private Instant pickupAt;

    @Column(name = "pickup_instructions", length = 500)
    private String pickupInstructions;

    public void cancel() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending orders can be cancelled"
            );
        }

        status = OrderStatus.CANCELLED;
    }
    public void complete() {
        if (status != OrderStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "Only accepted orders can be completed"
            );
        }
        if (pickupAt == null || pickupInstructions == null
                || pickupInstructions.isBlank()) {
            throw new IllegalStateException("Pickup details must be set before collection");
        }
        status = OrderStatus.COMPLETED;
    }
    public void accept() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be accepted");
        }
        status = OrderStatus.ACCEPTED;
    }

    public void reject() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only pending orders can be rejected");
        }
        status = OrderStatus.REJECTED;
    }
    public Instant getPickupAt() {
        return pickupAt;
    }

    public String getPickupInstructions() {
        return pickupInstructions;
    }

    public void schedulePickup(
            Instant pickupAt,
            String pickupInstructions
    ) {
        if (status != OrderStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "Only accepted orders can have pickup details"
            );
        }

        if (pickupAt == null || !pickupAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "Pickup time must be in the future"
            );
        }

        if (pickupInstructions == null
                || pickupInstructions.isBlank()
                || pickupInstructions.strip().length() > 500) {
            throw new IllegalArgumentException(
                    "Pickup instructions must contain 1 to 500 characters"
            );
        }

        this.pickupAt = pickupAt;
        this.pickupInstructions = pickupInstructions.strip();
    }
}