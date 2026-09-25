package com.prashant.KhetWallah.listing;
import java.math.BigDecimal;

public class ProduceListing {
    private final Long id;
    private final String produceName;
    private final String farmerName;
    private final BigDecimal availableQuantityKg;
    private final BigDecimal pricePerKg;
    private final String pickupArea;
    private final ListingStatus status;
    private String photoUrl;
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
