package com.prashant.KhetWallah.listing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import com.prashant.KhetWallah.user.AppUser;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "produce_listings")
public class ListingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @Column(name = "produce_name", nullable = false, length = 100)
    private String produceName;

    @Column(name = "farmer_name", nullable = false, length = 100)
    private String farmerName;

    @Column(
            name = "available_quantity_kg",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal availableQuantityKg;

    @Column(
            name = "price_per_kg",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal pricePerKg;

    @Column(name = "pickup_area", nullable = false, length = 150)
    private String pickupArea;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private ListingStatus status = ListingStatus.ACTIVE;

    protected ListingEntity() {
    }

    public ListingEntity(
            String produceName,
            AppUser owner,
            BigDecimal availableQuantityKg,
            BigDecimal pricePerKg,
            String pickupArea
    ) {
        this.produceName = produceName;
        this.owner = java.util.Objects.requireNonNull(
                owner,
                "A new listing must have an owner"
        );
        this.farmerName = owner.getDisplayName();
        this.availableQuantityKg = availableQuantityKg;
        this.pricePerKg = pricePerKg;
        this.pickupArea = pickupArea;
    }

    public Long getId() {
        return id;
    }

    public String getProduceName() {
        return produceName;
    }

    public String getFarmerName() {
        return farmerName;
    }

    public BigDecimal getAvailableQuantityKg() {
        return availableQuantityKg;
    }
    public AppUser getOwner() {
        return owner;
    }

    public BigDecimal getPricePerKg() {
        return pricePerKg;
    }

    public String getPickupArea() {
        return pickupArea;
    }
    public ListingStatus getStatus() {
        return status;
    }

    public void updateDetails(
            BigDecimal availableQuantityKg,
            BigDecimal pricePerKg,
            String pickupArea
    ) {
        this.availableQuantityKg = availableQuantityKg;
        this.pricePerKg = pricePerKg;
        this.pickupArea = pickupArea;
    }
    public void close() {
        this.status = ListingStatus.CLOSED;
    }
}