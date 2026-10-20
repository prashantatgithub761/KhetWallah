package com.prashant.KhetWallah.message;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
public interface MessageReportRepository extends JpaRepository<MessageReport,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from MessageReport r where r.reporter.id=:reporter and r.createdAt>:cutoff order by r.createdAt desc")
 List<MessageReport> recentReportsForUpdate(@Param("reporter")Long reporter,@Param("cutoff")Instant cutoff,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from MessageReport r where r.message.id=:message and r.reporter.id=:reporter")
 Optional<MessageReport> existingForUpdate(@Param("message")Long message,@Param("reporter")Long reporter);
 boolean existsByMessage_IdAndReporter_Id(Long messageId,Long reporterId);
 long countByReporter_IdAndCreatedAtAfter(Long reporterId,Instant cutoff);
 @EntityGraph(attributePaths={"message","message.order","message.sender","reporter"})
 Page<MessageReport> findAllByOrderByIdDesc(Pageable pageable);
}
