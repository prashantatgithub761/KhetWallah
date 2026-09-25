package com.prashant.KhetWallah.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ListingRepository
        extends JpaRepository<ListingEntity, Long> {

    Page<ListingEntity>
    findByStatusAndProduceNameContainingIgnoreCaseAndPickupAreaContainingIgnoreCaseOrderByIdAsc(
            ListingStatus status,
            String produceName,
            String pickupArea,
            Pageable pageable
    );
    Optional<ListingEntity> findByIdAndStatus(
            Long id,
            ListingStatus status
    );
    Page<ListingEntity> findByOwner_EmailOrderByIdDesc(
            String email,
            Pageable pageable
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ListingEntity l where l.id = :id")
    Optional<ListingEntity> findByIdForUpdate(@Param("id") Long id);
}