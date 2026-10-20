package com.prashant.KhetWallah.user;

import com.prashant.KhetWallah.verification.VerificationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    // A separate budget prevents signup requests from consuming reset protection.
    private final VerificationRateLimiter limiter = new VerificationRateLimiter();

    public PasswordRecoveryController(PasswordRecoveryService recovery) { this.recovery = recovery; }

    @PostMapping("/api/auth/recovery/start")
    public ResponseEntity<RecoveryReply> start(@Valid @RequestBody RecoveryStartRequest input,
            HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        recovery.start(input.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new RecoveryReply(
                "If this email belongs to an eligible account and email delivery is available, a recovery code will arrive. Please wait 60 seconds before requesting another code.",
                60));
    }

    @PostMapping("/api/auth/recovery/finish")
    public ResponseEntity<Void> finish(@Valid @RequestBody RecoveryFinishRequest input,
            HttpServletRequest request) {
        limiter.check(request.getRemoteAddr());
        recovery.finish(input.email(), input.code(), input.newPassword());
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record RecoveryReply(String message, int resendAfterSeconds) {}
}
