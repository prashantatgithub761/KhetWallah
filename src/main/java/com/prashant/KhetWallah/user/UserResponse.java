package com.prashant.KhetWallah.user;

public record UserResponse(
        Long id,
        String displayName,
        String email
) {}