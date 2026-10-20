package com.prashant.KhetWallah.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ListingRepository
        extends JpaRepository<ListingEntity, Long>, JpaSpecificationExecutor<ListingEntity> {
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
    List<ListingEntity> findByOwner_IdAndStatus(Long ownerId, ListingStatus status);
}
