package com.prashant.KhetWallah.verification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificationEmailRequest(
        @NotBlank @Email @Size(max = 254) String email
) {
    @Override
    public String toString() {
        return "VerificationEmailRequest[REDACTED]";
    }
}
