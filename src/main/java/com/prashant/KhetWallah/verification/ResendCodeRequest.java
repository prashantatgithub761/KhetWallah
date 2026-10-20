package com.prashant.KhetWallah.verification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResendCodeRequest(
        @NotBlank
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        String challengeId
) {
    @Override
    public String toString() {
        return "ResendCodeRequest[REDACTED]";
    }
}
