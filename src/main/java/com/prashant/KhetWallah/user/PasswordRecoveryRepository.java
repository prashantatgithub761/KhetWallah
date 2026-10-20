package com.prashant.KhetWallah.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface PasswordRecoveryRepository extends JpaRepository<PasswordRecovery, String> {
    // MySQL upsert locks/creates the row atomically before the read. This also avoids
    // an absent-row race between concurrent first recovery requests.
    @Modifying
    @Query(value = """
        INSERT INTO password_recoveries
            (email, code_hash, expires_at, resend_at, window_started_at, send_count, failed_attempts)
        VALUES (:email, '', :now, :now, :now, 0, 0)
        ON DUPLICATE KEY UPDATE email = email
        """, nativeQuery = true)
    void ensureExists(@Param("email") String email, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PasswordRecovery r where r.email = :email")
    Optional<PasswordRecovery> findByEmailForUpdate(@Param("email") String email);
}
