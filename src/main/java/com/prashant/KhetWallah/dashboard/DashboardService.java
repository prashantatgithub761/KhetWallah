package com.prashant.KhetWallah.dashboard;

import com.prashant.KhetWallah.order.OrderEntity;
import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class DashboardService {
    private final DashboardRepository dashboard;
    private final AppUserRepository users;

    public DashboardService(DashboardRepository dashboard, AppUserRepository users) {
        this.dashboard = dashboard;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public DashboardResponse forAccount(String authenticatedEmail) {
        AppUser user = users.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Account no longer exists"));
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is suspended");
        }
        Instant now = Instant.now();
        Long userId = user.getId();
        long active = dashboard.activeListings(userId);
        long pending = dashboard.pendingOrders(userId);
        long received = dashboard.pendingReceivedOrders(userId);
        long upcoming = dashboard.upcomingPickups(userId, now);
        List<DashboardPickup> pickups = dashboard.upcomingPickupDetails(userId, now)
                .stream().map(order -> pickup(order, userId)).toList();
        return new DashboardResponse(user.getDisplayName(), user.getEmail(),
                active, pending, received, upcoming, now, pickups);
    }

    private DashboardPickup pickup(OrderEntity order, Long userId) {
        var listing = order.getListing();
        return new DashboardPickup(order.getId(), listing.getId(), listing.getProduceName(),
                listing.getFarmerName(), order.getBuyer().getDisplayName(), listing.getPickupArea(),
                order.getPickupAt(), order.getQuantityKg(), order.getTotalAmount(),
                userId.equals(order.getBuyer().getId()) ? "BUYER" : "FARMER");
    }
}
