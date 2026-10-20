package com.prashant.KhetWallah.mail;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, String> {
    @Query("select e.id from EmailOutbox e where e.sentAt is null and e.failed = false "
            + "and e.nextAttemptAt <= :now order by e.nextAttemptAt, e.id")
    List<String> findDueIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailOutbox e where e.id = :id")
    Optional<EmailOutbox> findByIdForUpdate(@Param("id") String id);
}
