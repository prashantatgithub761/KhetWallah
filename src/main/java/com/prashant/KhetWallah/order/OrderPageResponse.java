package com.prashant.KhetWallah.order;

import java.util.List;

public record OrderPageResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}