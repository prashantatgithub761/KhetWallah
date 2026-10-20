package com.prashant.KhetWallah.report;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {
    boolean existsByListing_IdAndReporter_Id(Long listingId, Long reporterId);
    Page<ListingReport> findByStatus(ListingReport.Status status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ListingReport r where r.id = :id")
    Optional<ListingReport> findByIdForUpdate(@Param("id") Long id);
}
