package com.prashant.KhetWallah.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface OrderRepository
        extends JpaRepository<OrderEntity, Long> {

    Page<OrderEntity> findByBuyer_EmailOrderByIdDesc(
            String email,
            Pageable pageable
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);
    Page<OrderEntity> findByListing_Owner_EmailOrderByIdDesc(
            String email,
            Pageable pageable
    );
}