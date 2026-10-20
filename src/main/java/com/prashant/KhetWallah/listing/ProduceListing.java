package com.prashant.KhetWallah.listing;
import java.math.BigDecimal;
import java.time.LocalDate;

public class ProduceListing {
    private final Long id;
    private final String produceName;
    private final String farmerName;
    private final BigDecimal availableQuantityKg;
    private final BigDecimal pricePerKg;
    private final String pickupArea;
    private final ListingStatus status;
    private String photoUrl;
    private String variety;
    private LocalDate harvestDate;
    private BigDecimal minimumOrderQuantityKg = new BigDecimal("0.01");
    private String packagingDetails;

    private Long farmerId;
    private String district;
    private String pinCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDate availableUntil;
    public Long getFarmerId() { return farmerId; }
    public String getDistrict() { return district; }
    public String getPinCode() { return pinCode; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public LocalDate getAvailableUntil() { return availableUntil; }
    public void setDiscoveryDetails(Long farmerId, String district, String pinCode, BigDecimal latitude,
            BigDecimal longitude, LocalDate availableUntil) {
        this.farmerId = farmerId; this.district = district; this.pinCode = pinCode;
        this.latitude = latitude; this.longitude = longitude; this.availableUntil = availableUntil;
    }

    public String getVariety() { return variety; }
    public LocalDate getHarvestDate() { return harvestDate; }
    public BigDecimal getMinimumOrderQuantityKg() { return minimumOrderQuantityKg; }
    public String getPackagingDetails() { return packagingDetails; }

    public ProduceListing(Long id, String produceName, String farmerName, BigDecimal availableQuantityKg,
                          BigDecimal pricePerKg, String pickupArea, ListingStatus status, String variety,
                          LocalDate harvestDate, BigDecimal minimumOrderQuantityKg, String packagingDetails) {
        this(id, produceName, farmerName, availableQuantityKg, pricePerKg, pickupArea, status);
        this.variety = variety;
        this.harvestDate = harvestDate;
        this.minimumOrderQuantityKg = minimumOrderQuantityKg == null ? new BigDecimal("0.01") : minimumOrderQuantityKg;
        this.packagingDetails = packagingDetails;
    }

    public ProduceListing(
            Long id,
            String produceName,
            String farmerName,
            BigDecimal availableQuantityKg,
            BigDecimal pricePerKg,
            String pickupArea,
            ListingStatus status
    ) {
        this.id = id;
        this.produceName = produceName;
        this.farmerName = farmerName;
        this.availableQuantityKg = availableQuantityKg;
        this.pricePerKg = pricePerKg;
        this.pickupArea = pickupArea;
        this.status = status;
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

    public BigDecimal getPricePerKg() {
        return pricePerKg;
    }

    public String getPickupArea() {
        return pickupArea;
    }
    public ListingStatus getStatus() {
        return status;
    }
    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }


}
