package com.prashant.KhetWallah.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryFinishRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String code,
        @NotBlank @Size(min = 15, max = 128,
                message = "Password must contain 15 to 128 characters") String newPassword) {
    @Override public String toString() { return "RecoveryFinishRequest[REDACTED]"; }
}
