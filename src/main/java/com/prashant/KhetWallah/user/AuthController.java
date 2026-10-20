package com.prashant.KhetWallah.user;

import com.prashant.KhetWallah.verification.ResendCodeRequest;
import com.prashant.KhetWallah.verification.VerificationChallenge;
import com.prashant.KhetWallah.verification.VerificationEmailRequest;
import com.prashant.KhetWallah.verification.VerificationRateLimiter;
import com.prashant.KhetWallah.verification.VerificationService;
import com.prashant.KhetWallah.verification.VerifiedRegistrationRequest;
import com.prashant.KhetWallah.verification.VerifiedRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final VerifiedRegistrationService registrations;
    private final VerificationService verification;
    private final VerificationRateLimiter rateLimiter;

    public AuthController(VerifiedRegistrationService registrations,
            VerificationService verification, VerificationRateLimiter rateLimiter) {
        this.registrations = registrations;
        this.verification = verification;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/api/auth/verification/start")
    public ResponseEntity<VerificationChallenge> start(
            @Valid @RequestBody VerificationEmailRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check(servletRequest.getRemoteAddr());
        return ResponseEntity.accepted().body(verification.start(request.email()));
    }

    @PostMapping("/api/auth/verification/resend")
    public ResponseEntity<VerificationChallenge> resend(
            @Valid @RequestBody ResendCodeRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check(servletRequest.getRemoteAddr());
        return ResponseEntity.accepted().body(verification.resend(request.challengeId()));
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody VerifiedRegistrationRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check(servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(registrations.register(request));
    }
}
