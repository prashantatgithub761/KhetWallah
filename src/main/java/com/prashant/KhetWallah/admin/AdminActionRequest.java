package com.prashant.KhetWallah.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminActionRequest(@NotBlank @Size(max = 500) String reason) {}
