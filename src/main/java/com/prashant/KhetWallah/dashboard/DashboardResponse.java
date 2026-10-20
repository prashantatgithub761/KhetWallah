package com.prashant.KhetWallah.dashboard;

import java.time.Instant;
import java.util.List;

public record DashboardResponse(
        String displayName,
        String email,
        long activeListings,
        long pendingOrders,
        long pendingReceivedOrders,
        long upcomingPickups,
        Instant asOf,
        List<DashboardPickup> pickups
) {
    public DashboardResponse {
        pickups = List.copyOf(pickups);
    }
}
