package com.prashant.KhetWallah.review;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface OrderReviewRepository extends JpaRepository<OrderReview,Long>{
    @EntityGraph(attributePaths={"buyer","order"})
    Page<OrderReview> findByListingIdAndHiddenFalseOrderByIdDesc(Long listingId,Pageable pageable);
    Optional<OrderReview> findByOrder_Id(Long orderId);
    @Query("select avg(r.rating) from OrderReview r where r.listingId=:listingId and r.hidden=false")
    Double averageVisible(@Param("listingId") Long listingId);
    long countByListingIdAndHiddenFalse(Long listingId);
    @EntityGraph(attributePaths={"buyer","order"})
    Page<OrderReview> findAllByOrderByIdDesc(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderReview r where r.id=:id")
    Optional<OrderReview> findByIdForUpdate(@Param("id") Long id);
}
