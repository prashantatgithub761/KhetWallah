package com.prashant.KhetWallah.report;

import com.prashant.KhetWallah.admin.*;
import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.user.*;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ListingReportService {
    private final ListingReportRepository reports;
    private final ListingRepository listings;
    private final AppUserRepository users;
    private final AdminActionRepository actions;
    public ListingReportService(ListingReportRepository reports, ListingRepository listings,
                                AppUserRepository users, AdminActionRepository actions) {
        this.reports = reports; this.listings = listings; this.users = users; this.actions = actions;
    }

    @Transactional
    public Receipt submit(Long listingId, String reason, String email) {
        String clean = reason(reason);
        AppUser reporter = activeUser(email);
        // Serialize submissions for a listing; the unique constraint is a second safeguard.
        ListingEntity listing = listings.findByIdForUpdate(listingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Listing not found"));
        if (listing.getStatus() != ListingStatus.ACTIVE) throw error(HttpStatus.CONFLICT, "This listing is no longer active");
        if (listing.getOwner() != null && listing.getOwner().getEmail().equals(email))
            throw error(HttpStatus.BAD_REQUEST, "You cannot report your own listing");
        if (reports.existsByListing_IdAndReporter_Id(listingId, reporter.getId()))
            throw error(HttpStatus.CONFLICT, "You have already reported this listing");
        ListingReport report = reports.saveAndFlush(new ListingReport(listing, reporter, clean));
        return new Receipt(report.getId(), report.getStatus());
    }

    @Transactional(readOnly = true)
    public Page<ReportView> list(ListingReport.Status status, int page, int size, String email) {
        admin(email);
        if (page < 0 || size < 1 || size > 50) throw error(HttpStatus.BAD_REQUEST, "Use page >= 0 and size 1–50");
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending().and(Sort.by("id")));
        return reports.findByStatus(status, pageable).map(ReportView::new);
    }

    @Transactional
    public ReportView resolve(Long id, boolean hide, String reason, String email) {
        admin(email);
        String clean = reason(reason);
        ListingReport report = reports.findByIdForUpdate(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Report not found"));
        if (report.getStatus() != ListingReport.Status.OPEN) throw error(HttpStatus.CONFLICT, "Report has already been resolved");
        if (hide) {
            ListingEntity listing = listings.findByIdForUpdate(report.getListing().getId())
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Listing not found"));
            if (listing.getStatus() == ListingStatus.CLOSED)
                throw error(HttpStatus.CONFLICT, "Listing is closed; dismiss the report with an explanation");
            if (listing.getStatus() != ListingStatus.HIDDEN && listing.getStatus() != ListingStatus.CLOSED) {
                listing.hide();
                actions.save(new AdminAction(email, "HIDE_LISTING", "LISTING", listing.getId(), clean));
            }
        }
        report.resolve(hide ? ListingReport.Status.HIDDEN : ListingReport.Status.DISMISSED, email, clean);
        actions.save(new AdminAction(email, hide ? "REPORT_HIDE" : "REPORT_DISMISS", "REPORT", id, clean));
        return new ReportView(report);
    }

    private AppUser activeUser(String email) {
        AppUser user = users.findByEmail(email).orElseThrow(() -> error(HttpStatus.FORBIDDEN, "Account unavailable"));
        if (user.isSuspended()) throw error(HttpStatus.FORBIDDEN, "Account suspended");
        return user;
    }
    private void admin(String email) {
        if (activeUser(email).getRole() != UserRole.ADMIN) throw error(HttpStatus.FORBIDDEN, "Admin access required");
    }
    private String reason(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 500)
            throw error(HttpStatus.BAD_REQUEST, "Enter a reason of 1–500 characters");
        return value.trim();
    }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    public record Receipt(Long id, ListingReport.Status status) {}
    public record ReportView(Long id, Long listingId, String produceName, String ownerEmail,
                             String reporterEmail, String pickupArea, java.math.BigDecimal pricePerKg, ListingStatus listingStatus, String reason, ListingReport.Status status,
                             Instant createdAt, Instant resolvedAt, String resolvedBy, String resolutionReason) {
        public ReportView(ListingReport r) {
            this(r.getId(), r.getListing().getId(), r.getListing().getProduceName(),
                    r.getListing().getOwner() == null ? "Unassigned" : r.getListing().getOwner().getEmail(),
                    r.getReporter().getEmail(), r.getListing().getPickupArea(), r.getListing().getPricePerKg(), r.getListing().getStatus(), r.getReason(), r.getStatus(), r.getCreatedAt(),
                    r.getResolvedAt(), r.getResolvedBy(), r.getResolutionReason());
        }
    }
}
