package com.prashant.KhetWallah.error;

import java.util.Map;

public record ApiError(
        int status,
        String message,
        Map<String, String> fieldErrors
) {}