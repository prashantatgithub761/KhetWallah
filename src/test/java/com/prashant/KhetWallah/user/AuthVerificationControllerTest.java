package com.prashant.KhetWallah.user;

import com.prashant.KhetWallah.config.PasswordConfig;
import com.prashant.KhetWallah.config.SecurityConfig;
import com.prashant.KhetWallah.error.GlobalExceptionHandler;
import com.prashant.KhetWallah.verification.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, PasswordConfig.class, GlobalExceptionHandler.class})
class AuthVerificationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VerifiedRegistrationService registrations;
    @MockitoBean VerificationService verification;
    @MockitoBean VerificationRateLimiter rateLimiter;
    @MockitoBean AppUserRepository users;

    @Test
    void signedOutUserCanRequestCodeWithCsrf() throws Exception {
        when(verification.start("buyer@example.com")).thenReturn(new VerificationChallenge(
                "44444444-4444-4444-4444-444444444444", Instant.parse("2099-01-01T00:00:00Z"), 60));
        mvc.perform(post("/api/auth/verification/start").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"buyer@example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("resendAfterSeconds").value(60))
                .andExpect(jsonPath("code").doesNotExist());
        verify(rateLimiter).check("127.0.0.1");
        verifyNoInteractions(registrations);
    }

    @Test
    void sendingCodeWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/verification/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"buyer@example.com\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(verification, registrations);
    }

    @Test
    void legacyRegisterBodyCannotCreateAnUnverifiedAccount() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Buyer\",\"email\":\"buyer@example.com\",\"password\":\"Example-passphrase-2026!\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registrations);
    }

    @Test
    void finalRegisterDelegatesToTheVerifiedFlow() throws Exception {
        when(registrations.register(any())).thenReturn(new UserResponse(8L, "Buyer", "buyer@example.com"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Buyer\",\"email\":\"buyer@example.com\",\"password\":\"Example-passphrase-2026!\",\"challengeId\":\"44444444-4444-4444-4444-444444444444\",\"code\":\"123456\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("id").value(8));
        verify(registrations).register(any(VerifiedRegistrationRequest.class));
    }
}
