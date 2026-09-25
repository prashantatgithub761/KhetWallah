package com.prashant.KhetWallah.order;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotNull
        @Positive
        Long listingId,

        @NotNull
        @Positive
        @Digits(integer = 8, fraction = 2)
        BigDecimal quantityKg
) {}