package com.prashant.KhetWallah.admin;

import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.user.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {
    private final AppUserRepository users;
    private final ListingRepository listings;
    private final AdminActionRepository actions;

    public AdminService(AppUserRepository users, ListingRepository listings, AdminActionRepository actions) {
        this.users = users;
        this.listings = listings;
        this.actions = actions;
    }

    @Transactional(readOnly = true)
    public Page<UserView> users(int page, int size) {
        return users.findAll(pageable(page, size)).map(UserView::new);
    }

    @Transactional(readOnly = true)
    public Page<ListingView> listings(int page, int size) {
        return listings.findAll(pageable(page, size)).map(ListingView::new);
    }

    @Transactional(readOnly = true)
    public Page<AdminAction> actions(int page, int size) {
        return actions.findAll(pageable(page, size));
    }

    @Transactional
    public ListingView hideListing(Long id, String reason, String actor) {
        requireAdmin(actor);
        ListingEntity listing = listings.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (listing.getStatus() == ListingStatus.HIDDEN || listing.getStatus() == ListingStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This listing is already hidden or closed");
        }
        listing.hide();
        record(actor, "HIDE_LISTING", "LISTING", id, reason);
        return new ListingView(listing);
    }

    @Transactional
    public ListingView restoreListing(Long id, String reason, String actor) {
        requireAdmin(actor);
        ListingEntity listing = listings.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (listing.getStatus() != ListingStatus.HIDDEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only hidden listings can be restored");
        }
        if (listing.getOwner() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This legacy listing has no owner and cannot be restored");
        }
        if (listing.getOwner().isSuspended()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reinstate the owner first");
        }
        listing.restore();
        record(actor, "RESTORE_LISTING", "LISTING", id, reason);
        return new ListingView(listing);
    }

    @Transactional
    public UserView suspendUser(Long id, String reason, String actor) {
        requireAdmin(actor);
        AppUser user = users.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin accounts cannot be suspended");
        }
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is already suspended");
        }
        user.suspend();
        String listingReason = "Owner suspended: " + reason;
        if (listingReason.length() > 500) listingReason = listingReason.substring(0, 500);
        for (ListingStatus state : java.util.List.of(ListingStatus.ACTIVE, ListingStatus.PAUSED, ListingStatus.SOLD_OUT)) {
            for (ListingEntity listing : listings.findByOwner_IdAndStatus(id, state)) {
                listing.hide();
                record(actor, "HIDE_LISTING", "LISTING", listing.getId(), listingReason);
            }
        }
        record(actor, "SUSPEND_USER", "USER", id, reason);
        return new UserView(user);
    }

    @Transactional
    public UserView reinstateUser(Long id, String reason, String actor) {
        requireAdmin(actor);
        AppUser user = users.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not suspended");
        }
        user.reinstate();
        record(actor, "REINSTATE_USER", "USER", id, reason);
        return new UserView(user);
    }

    private void requireAdmin(String email) {
        AppUser actor = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (actor.getRole() != UserRole.ADMIN || actor.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void record(String actor, String action, String type, Long id, String reason) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty() || clean.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason must be 1–500 characters");
        }
        actions.save(new AdminAction(actor, action, type, id, clean));
    }

    private PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use page >= 0 and size 1–50");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }

    public record UserView(Long id, String displayName, String email, UserRole role, boolean suspended) {
        public UserView(AppUser user) {
            this(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole(), user.isSuspended());
        }
    }

    public record ListingView(Long id, String produceName, String farmerName, String ownerEmail,
                              String pickupArea, ListingStatus status) {
        public ListingView(ListingEntity listing) {
            this(listing.getId(), listing.getProduceName(), listing.getFarmerName(),
                    listing.getOwner() == null ? "Unassigned" : listing.getOwner().getEmail(), listing.getPickupArea(), listing.getStatus());
        }
    }
}
