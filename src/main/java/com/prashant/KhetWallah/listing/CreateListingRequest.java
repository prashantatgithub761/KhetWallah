package com.prashant.KhetWallah.listing;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateListingRequest(

        @NotBlank
        @Size(max = 100)
        String produceName,

        @NotNull
        @Positive
        @Digits(integer = 8, fraction = 2)
        BigDecimal availableQuantityKg,

        @NotNull
        @Positive
        @Digits(integer = 8, fraction = 2)
        BigDecimal pricePerKg,

        @NotBlank
        @Size(max = 150)
        String pickupArea

) {}