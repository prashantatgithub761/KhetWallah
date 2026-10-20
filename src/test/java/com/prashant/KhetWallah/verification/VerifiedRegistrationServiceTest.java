package com.prashant.KhetWallah.verification;

import com.prashant.KhetWallah.user.RegisterRequest;
import com.prashant.KhetWallah.user.RegistrationService;
import com.prashant.KhetWallah.user.UserResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerifiedRegistrationServiceTest {
    private final VerificationService verification = mock(VerificationService.class);
    private final RegistrationService registration = mock(RegistrationService.class);
    private final VerifiedRegistrationService service = new VerifiedRegistrationService(verification, registration);
    private final VerifiedRegistrationRequest request = new VerifiedRegistrationRequest(
            "Demo Farmer", "Farmer@Example.com", "Local-Demo-Only-2026!",
            "c0223dc3-490c-472c-914a-a8135a03f225", "123456");

    @Test
    void createsAccountOnlyAfterConsumingProofInTheSameCall() {
        UserResponse response = new UserResponse(1L, "Demo Farmer", "farmer@example.com");
        when(registration.register(any(RegisterRequest.class))).thenReturn(response);

        assertSame(response, service.register(request));

        var order = inOrder(verification, registration);
        order.verify(verification).verifyAndConsume(request.email(), request.challengeId(), request.code());
        ArgumentCaptor<RegisterRequest> captured = ArgumentCaptor.forClass(RegisterRequest.class);
        order.verify(registration).register(captured.capture());
        assertEquals(request.displayName(), captured.getValue().displayName());
        assertEquals(request.email(), captured.getValue().email());
        assertEquals(request.password(), captured.getValue().password());
    }

    @Test
    void invalidProofNeverCallsAccountRegistration() {
        doThrow(new InvalidCodeException()).when(verification)
                .verifyAndConsume(request.email(), request.challengeId(), request.code());

        assertThrows(InvalidCodeException.class, () -> service.register(request));

        verifyNoInteractions(registration);
    }

    @Test
    void expiredProofNeverCallsAccountRegistration() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST)).when(verification)
                .verifyAndConsume(request.email(), request.challengeId(), request.code());

        assertThrows(ResponseStatusException.class, () -> service.register(request));

        verifyNoInteractions(registration);
    }

    @Test
    void transactionCommitsWrongAttemptButRollsBackAccountFailures() throws Exception {
        var method = VerifiedRegistrationService.class.getMethod("register", VerifiedRegistrationRequest.class);
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertArrayEquals(new Class<?>[] {InvalidCodeException.class}, annotation.noRollbackFor());
        var transaction = new AnnotationTransactionAttributeSource()
                .getTransactionAttribute(method, VerifiedRegistrationService.class);
        assertFalse(transaction.rollbackOn(new InvalidCodeException()));
        assertTrue(transaction.rollbackOn(new ResponseStatusException(HttpStatus.CONFLICT)));
        assertTrue(transaction.rollbackOn(new IllegalStateException("Database unavailable")));
    }
}
