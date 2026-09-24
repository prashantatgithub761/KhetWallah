package com.prashant.KhetWallah.listing;

import java.util.List;

public record ListingPageResponse(
        List<ProduceListing> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}