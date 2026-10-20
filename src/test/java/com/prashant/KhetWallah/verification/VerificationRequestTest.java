package com.prashant.KhetWallah.verification;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class VerificationRequestTest {
    private static final String CHALLENGE = "c0223dc3-490c-472c-914a-a8135a03f225";

    @Test
    void validSignupRequiresCompleteDetailsAndSixDigitProof() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertTrue(validator.validate(new VerifiedRegistrationRequest("Farmer", "farmer@example.com",
                    "Local-Demo-Only-2026!", CHALLENGE, "000007")).isEmpty());
            Set<String> invalidFields = validator.validate(new VerifiedRegistrationRequest(
                    " ", "invalid-email", "short", "bad-id", "12x456")).stream()
                    .map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet());
            assertEquals(Set.of("displayName", "email", "password", "challengeId", "code"), invalidFields);
        }
    }

    @Test
    void initialRequestCarriesOnlyTheEmailAndResendRequiresAChallenge() {
        assertEquals(1, VerificationEmailRequest.class.getRecordComponents().length);
        assertEquals("email", VerificationEmailRequest.class.getRecordComponents()[0].getName());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertTrue(validator.validate(new VerificationEmailRequest("farmer@example.com")).isEmpty());
            assertFalse(validator.validate(new VerificationEmailRequest("invalid")).isEmpty());
            assertTrue(validator.validate(new ResendCodeRequest(CHALLENGE)).isEmpty());
            assertFalse(validator.validate(new ResendCodeRequest("invalid")).isEmpty());
        }
    }

    @Test
    void nameEmailAndPasswordLengthsMatchExistingRegistration() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertTrue(validator.validate(new VerifiedRegistrationRequest("n".repeat(100),
                    "farmer@example.com", "p".repeat(15), CHALLENGE, "123456")).isEmpty());
            assertTrue(validator.validate(new VerifiedRegistrationRequest("Farmer",
                    "farmer@example.com", "p".repeat(128), CHALLENGE, "123456")).isEmpty());
            assertFalse(validator.validate(new VerifiedRegistrationRequest("n".repeat(101),
                    "farmer@example.com", "p".repeat(129), CHALLENGE, "123456")).isEmpty());
            String longEmail = "x".repeat(64) + "@" + "a".repeat(63) + "." + "b".repeat(63)
                    + "." + "c".repeat(63);
            assertTrue(longEmail.length() > 254);
            assertFalse(validator.validate(new VerificationEmailRequest(longEmail)).isEmpty());
        }
    }

    @Test
    void requestStringsRedactAllSignupSecrets() {
        assertEquals("VerificationEmailRequest[REDACTED]",
                new VerificationEmailRequest("farmer@example.com").toString());
        assertEquals("ResendCodeRequest[REDACTED]", new ResendCodeRequest(CHALLENGE).toString());
        assertEquals("VerifiedRegistrationRequest[REDACTED]", new VerifiedRegistrationRequest(
                "Farmer", "farmer@example.com", "Local-Demo-Only-2026!", CHALLENGE, "123456").toString());
    }
}
