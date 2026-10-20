package com.prashant.KhetWallah.message;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;
public interface OrderMessageRepository extends JpaRepository<OrderMessage,Long> {
 @EntityGraph(attributePaths={"sender","recipient"}) Page<OrderMessage> findByOrder_IdOrderByIdDesc(Long orderId,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from OrderMessage m where m.sender.id=:sender and m.createdAt>:cutoff order by m.createdAt desc")
 List<OrderMessage> recentSentForUpdate(@Param("sender")Long sender,@Param("cutoff")Instant cutoff,Pageable pageable);
 long countBySender_IdAndCreatedAtAfter(Long senderId,Instant cutoff);
 long countByRecipient_IdAndReadAtIsNull(Long recipientId);
 long countByOrder_IdAndRecipient_IdAndReadAtIsNull(Long orderId,Long recipientId);
 @Modifying @Query("update OrderMessage m set m.readAt=:now where m.order.id=:order and m.recipient.id=:recipient and m.id<=:last and m.readAt is null")
 int markRead(@Param("order")Long order,@Param("recipient")Long recipient,@Param("last")Long last,@Param("now")Instant now);
 @EntityGraph(attributePaths={"order","order.listing","order.listing.owner","order.buyer","sender","recipient"})
 @Query(value="select m from OrderMessage m where (m.sender.id=:user or m.recipient.id=:user) and m.id=(select max(n.id) from OrderMessage n where n.order.id=m.order.id)",countQuery="select count(distinct m.order.id) from OrderMessage m where m.sender.id=:user or m.recipient.id=:user")
 Page<OrderMessage> conversations(@Param("user")Long user,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from OrderMessage m where m.id=:id") Optional<OrderMessage> findByIdForUpdate(@Param("id")Long id);
}
