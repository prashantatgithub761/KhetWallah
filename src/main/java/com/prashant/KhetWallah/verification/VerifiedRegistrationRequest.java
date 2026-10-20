package com.prashant.KhetWallah.verification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifiedRegistrationRequest(
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank
        @Size(min = 15, max = 128, message = "Password must contain 15 to 128 characters")
        String password,
        @NotBlank
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        String challengeId,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
    @Override
    public String toString() {
        return "VerifiedRegistrationRequest[REDACTED]";
    }
}
