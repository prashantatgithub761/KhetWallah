package com.prashant.KhetWallah.review;
import jakarta.validation.constraints.*;
public record CreateReviewRequest(@Min(1) @Max(5) int rating,@NotBlank @Size(max=1000) String comment){}
