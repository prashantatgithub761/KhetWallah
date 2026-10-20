package com.prashant.KhetWallah.report;

import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "listing_reports", indexes = @Index(name = "ix_reports_queue", columnList = "status,created_at,id"), uniqueConstraints = @UniqueConstraint(
        name = "uk_report_listing_user", columnNames = {"listing_id", "reporter_id"}))
public class ListingReport {
    public enum Status { OPEN, DISMISSED, HIDDEN }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private ListingEntity listing;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private AppUser reporter;
    @Column(nullable = false, length = 500)
    private String reason;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Status status = Status.OPEN;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant resolvedAt;
    @Column(length = 254)
    private String resolvedBy;
    @Column(length = 500)
    private String resolutionReason;

    protected ListingReport() {}
    public ListingReport(ListingEntity listing, AppUser reporter, String reason) {
        this.listing = listing; this.reporter = reporter; this.reason = reason; this.createdAt = Instant.now();
    }
    public void resolve(Status status, String actor, String reason) {
        if (this.status != Status.OPEN || status == Status.OPEN) throw new IllegalStateException("Report already resolved or invalid decision");
        this.status = status; this.resolvedBy = actor; this.resolutionReason = reason; this.resolvedAt = Instant.now();
    }
    public Long getId() { return id; }
    public ListingEntity getListing() { return listing; }
    public AppUser getReporter() { return reporter; }
    public String getReason() { return reason; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public String getResolutionReason() { return resolutionReason; }
}
