package com.prashant.KhetWallah.order;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record SchedulePickupRequest(
        @NotNull(message = "Choose a pickup time")
        @Future(message = "Pickup time must be in the future")
        Instant pickupAt,
        @NotBlank(message = "Enter the pickup location and instructions")
        @Size(max = 500, message = "Use no more than 500 characters")
        String pickupInstructions
) {}
