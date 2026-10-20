package com.prashant.KhetWallah.verification;

import java.time.Instant;

public record VerificationChallenge(
        String challengeId,
        Instant expiresAt,
        int resendAfterSeconds
) {}
