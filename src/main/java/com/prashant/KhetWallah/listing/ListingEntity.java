package com.prashant.KhetWallah.listing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.prashant.KhetWallah.user.AppUser;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "produce_listings")
public class ListingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "photo_file_name", length = 60)
    private String photoFileName;

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status_before_hide", length = 16)
    private ListingStatus statusBeforeHide;

    @Column(name = "variety", length = 100)
    private String variety;

    @Column(name = "harvest_date")
    private LocalDate harvestDate;

    @Column(name = "minimum_order_quantity_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumOrderQuantityKg = new BigDecimal("0.01");

    @Column(name = "packaging_details", length = 300)
    private String packagingDetails;

    @Column(name = "district", length = 100)
    private String district;
    @Column(name = "pin_code", length = 6)
    private String pinCode;
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;
    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;
    @Column(name = "available_until")
    private LocalDate availableUntil;

    public String getDistrict() { return district; }
    public String getPinCode() { return pinCode; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public LocalDate getAvailableUntil() { return availableUntil; }
    public boolean isPubliclyAvailable() {
        return status == ListingStatus.ACTIVE
                && (availableUntil == null || !availableUntil.isBefore(LocalDate.now()))
                && (owner == null || !owner.isSuspended());
    }
    public boolean isAvailableForOrders() {
        return isPubliclyAvailable() && availableQuantityKg != null
                && availableQuantityKg.signum() > 0
                && availableQuantityKg.compareTo(getMinimumOrderQuantityKg()) >= 0;
    }
    public void updateDiscoveryDetails(String district, String pinCode, BigDecimal latitude,
            BigDecimal longitude, LocalDate availableUntil, boolean clearLocation, boolean clearAvailableUntil) {
        if (district != null) this.district = trimmedOptional(district);
        if (pinCode != null) this.pinCode = trimmedOptional(pinCode);
        if (clearLocation) { this.latitude = null; this.longitude = null; }
        else if (latitude != null && longitude != null) { this.latitude = latitude; this.longitude = longitude; }
        if (clearAvailableUntil) this.availableUntil = null;
        else if (availableUntil != null) this.availableUntil = availableUntil;
    }
    public void pause() {
        if (status != ListingStatus.ACTIVE) throw new IllegalStateException("Only active listings can be paused");
        status = ListingStatus.PAUSED;
    }
    public void markSoldOut() {
        if (status == ListingStatus.HIDDEN || status == ListingStatus.CLOSED)
            throw new IllegalStateException("Hidden or closed listings cannot be changed");
        status = ListingStatus.SOLD_OUT;
    }
    public void resume() {
        if (status != ListingStatus.PAUSED && status != ListingStatus.SOLD_OUT)
            throw new IllegalStateException("Only paused or sold-out listings can be resumed");
        if (availableUntil != null && availableUntil.isBefore(LocalDate.now()))
            throw new IllegalStateException("Update the availability date before resuming");
        if (availableQuantityKg == null || availableQuantityKg.compareTo(getMinimumOrderQuantityKg()) < 0)
            throw new IllegalStateException("Add enough stock before resuming");
        status = ListingStatus.ACTIVE;
    }

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

    public ListingEntity(String produceName, AppUser owner, BigDecimal availableQuantityKg,
                         BigDecimal pricePerKg, String pickupArea, String variety,
                         LocalDate harvestDate, BigDecimal minimumOrderQuantityKg,
                         String packagingDetails) {
        this(produceName, owner, availableQuantityKg, pricePerKg, pickupArea);
        updateCropDetails(variety, harvestDate, minimumOrderQuantityKg, packagingDetails, false);
    }

    public String getVariety() { return variety; }
    public LocalDate getHarvestDate() { return harvestDate; }
    public BigDecimal getMinimumOrderQuantityKg() {
        return minimumOrderQuantityKg == null ? new BigDecimal("0.01") : minimumOrderQuantityKg;
    }
    public String getPackagingDetails() { return packagingDetails; }

    public void updateCropDetails(String variety, LocalDate harvestDate,
                                  BigDecimal minimumOrderQuantityKg, String packagingDetails,
                                  boolean clearHarvestDate) {
        if (variety != null) this.variety = trimmedOptional(variety);
        if (clearHarvestDate) this.harvestDate = null;
        else if (harvestDate != null) this.harvestDate = harvestDate;
        if (minimumOrderQuantityKg != null) this.minimumOrderQuantityKg = minimumOrderQuantityKg;
        if (packagingDetails != null) this.packagingDetails = trimmedOptional(packagingDetails);
    }

    private static String trimmedOptional(String value) {
        String result = value.strip();
        return result.isEmpty() ? null : result;
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
    public void reserveStock(BigDecimal quantityKg) {
        if (!isPubliclyAvailable()) {
            throw new IllegalStateException("Listing is not currently available");
        }

        if (quantityKg == null || quantityKg.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive"
            );
        }

        if (quantityKg.compareTo(getMinimumOrderQuantityKg()) < 0) {
            throw new IllegalArgumentException("Quantity is below the listing's minimum order quantity");
        }

        if (availableQuantityKg.compareTo(quantityKg) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient available quantity"
            );
        }

        availableQuantityKg = availableQuantityKg.subtract(quantityKg);
    }
    public void close() {
        this.status = ListingStatus.CLOSED;
    }
    public void hide() {
        if (status != ListingStatus.HIDDEN && status != ListingStatus.CLOSED) {
            statusBeforeHide = status;
            status = ListingStatus.HIDDEN;
        }
    }

    public void restore() {
        if (status != ListingStatus.HIDDEN) {
            throw new IllegalStateException("Only hidden listings can be restored");
        }
        status = statusBeforeHide == ListingStatus.PAUSED || statusBeforeHide == ListingStatus.SOLD_OUT
                ? statusBeforeHide : ListingStatus.ACTIVE;
        statusBeforeHide = null;
    }
    public void restoreStock(BigDecimal quantityKg) {
        if (quantityKg == null || quantityKg.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Restored quantity must be positive"
            );
        }

        availableQuantityKg = availableQuantityKg.add(quantityKg);
    }
    public String getPhotoFileName() {
        return photoFileName;
    }

    public void changePhoto(String photoFileName) {
        this.photoFileName = photoFileName;
    }

}
