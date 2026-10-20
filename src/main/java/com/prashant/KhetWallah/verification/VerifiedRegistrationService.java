package com.prashant.KhetWallah.verification;

import com.prashant.KhetWallah.user.RegisterRequest;
import com.prashant.KhetWallah.user.RegistrationService;
import com.prashant.KhetWallah.user.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerifiedRegistrationService {
    private final VerificationService verificationService;
    private final RegistrationService registrationService;

    public VerifiedRegistrationService(
            VerificationService verificationService,
            RegistrationService registrationService
    ) {
        this.verificationService = verificationService;
        this.registrationService = registrationService;
    }

    @Transactional(noRollbackFor = InvalidCodeException.class)
    public UserResponse register(VerifiedRegistrationRequest request) {
        verificationService.verifyAndConsume(request.email(), request.challengeId(), request.code());
        return registrationService.register(new RegisterRequest(
                request.displayName(), request.email(), request.password()));
    }
}
