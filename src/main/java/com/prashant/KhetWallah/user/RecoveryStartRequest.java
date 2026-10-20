package com.prashant.KhetWallah.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryStartRequest(@NotBlank @Email @Size(max = 254) String email) {
    @Override public String toString() { return "RecoveryStartRequest[REDACTED]"; }
}
