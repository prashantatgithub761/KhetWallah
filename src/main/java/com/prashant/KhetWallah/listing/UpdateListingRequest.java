package com.prashant.KhetWallah.listing;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateListingRequest(
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal availableQuantityKg,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal pricePerKg,
        @NotBlank @Size(max = 150) String pickupArea,
        @Size(max = 100) String variety,
        @PastOrPresent LocalDate harvestDate,
        @Positive @Digits(integer = 8, fraction = 2) BigDecimal minimumOrderQuantityKg,
        @Size(max = 300) String packagingDetails,
        Boolean clearHarvestDate,
        @Size(max = 100) String district,
        @Pattern(regexp = "^$|^[1-9][0-9]{5}$", message = "Enter a valid six-digit Indian PIN code") String pinCode,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @FutureOrPresent LocalDate availableUntil,
        Boolean clearLocation,
        Boolean clearAvailableUntil
) {
    public UpdateListingRequest(BigDecimal availableQuantityKg, BigDecimal pricePerKg, String pickupArea,
            String variety, LocalDate harvestDate, BigDecimal minimumOrderQuantityKg, String packagingDetails,
            Boolean clearHarvestDate) {
        this(availableQuantityKg, pricePerKg, pickupArea, variety, harvestDate, minimumOrderQuantityKg,
                packagingDetails, clearHarvestDate, null, null, null, null, null, null, null);
    }
    public UpdateListingRequest(BigDecimal availableQuantityKg, BigDecimal pricePerKg, String pickupArea) {
        this(availableQuantityKg, pricePerKg, pickupArea, null, null, null, null, false, null, null, null, null, null, null, null);
    }
    public UpdateListingRequest(BigDecimal availableQuantityKg, BigDecimal pricePerKg, String pickupArea,
                                String variety, LocalDate harvestDate, BigDecimal minimumOrderQuantityKg,
                                String packagingDetails) {
        this(availableQuantityKg, pricePerKg, pickupArea, variety, harvestDate,
                minimumOrderQuantityKg, packagingDetails, false, null, null, null, null, null, null, null);
    }
}
