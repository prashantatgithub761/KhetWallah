package com.prashant.KhetWallah.notification;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification,Long> {
    boolean existsByEventKey(String eventKey);
    Page<OrderNotification> findByRecipient_IdOrderByIdDesc(Long recipientId, Pageable pageable);
    long countByRecipient_IdAndReadAtIsNull(Long recipientId);
    Optional<OrderNotification> findByIdAndRecipient_Id(Long id,Long recipientId);
    @Modifying
    @Query("update OrderNotification n set n.readAt=:now where n.recipient.id=:userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId,@Param("now") Instant now);
}
