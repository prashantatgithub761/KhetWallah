package com.prashant.KhetWallah.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(
                min = 15,
                max = 128,
                message = "Password must contain 15 to 128 characters"
        )
        String password

) {
    @Override
    public String toString() {
        return "RegisterRequest[REDACTED]";
    }
}