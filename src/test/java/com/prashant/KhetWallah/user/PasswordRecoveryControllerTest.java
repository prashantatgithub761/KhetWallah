package com.prashant.KhetWallah.user;

import com.prashant.KhetWallah.config.PasswordConfig;
import com.prashant.KhetWallah.config.SecurityConfig;
import com.prashant.KhetWallah.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PasswordRecoveryController.class)
@Import({SecurityConfig.class, PasswordConfig.class, GlobalExceptionHandler.class})
class PasswordRecoveryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PasswordRecoveryService recovery;
    @MockitoBean AppUserRepository users;

    @Test
    void signedOutStartUsesTheSameGeneric202ResponseForAnyValidEmail() throws Exception {
        var known = mvc.perform(post("/api/auth/recovery/start").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"known@example.test\"}"))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("resendAfterSeconds").value(60))
                .andExpect(jsonPath("code").doesNotExist()).andReturn().getResponse().getContentAsString();
        var unknown = mvc.perform(post("/api/auth/recovery/start").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"unknown@example.test\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(known, unknown);
        assertFalse(known.contains("known@example.test"));
        verify(recovery).start("known@example.test");
        verify(recovery).start("unknown@example.test");
    }

    @Test
    void recoveryEndpointsRequireCsrfEvenWhenSignedOut() throws Exception {
        mvc.perform(post("/api/auth/recovery/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farmer@example.test\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/recovery/finish").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farmer@example.test\",\"code\":\"123456\",\"newPassword\":\"A unique passphrase 2026!\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(recovery);
    }

    @Test
    void successfulFinishReturns204WithoutEchoingSecrets() throws Exception {
        mvc.perform(post("/api/auth/recovery/finish").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farmer@example.test\",\"code\":\"123456\",\"newPassword\":\"A unique passphrase 2026!\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(recovery).finish("farmer@example.test", "123456", "A unique passphrase 2026!");
    }

    @Test
    void shortPasswordAndMalformedEmailAreRejectedBeforeServiceCalls() throws Exception {
        mvc.perform(post("/api/auth/recovery/finish").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farmer@example.test\",\"code\":\"123456\",\"newPassword\":\"too-short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("fieldErrors.newPassword").exists());
        mvc.perform(post("/api/auth/recovery/start").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recovery);
    }

    @Test
    void invalidCodeUsesSanitizedErrorAndRequestToStringsAreRedacted() throws Exception {
        doThrow(new InvalidRecoveryCodeException()).when(recovery)
                .finish("farmer@example.test", "654321", "A unique passphrase 2026!");
        mvc.perform(post("/api/auth/recovery/finish").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"farmer@example.test\",\"code\":\"654321\",\"newPassword\":\"A unique passphrase 2026!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("message").value(new InvalidRecoveryCodeException().getReason()));
        String safe = new RecoveryFinishRequest("farmer@example.test", "123456", "secret").toString();
        assertFalse(safe.contains("secret"));
        assertFalse(safe.contains("123456"));
        assertFalse(safe.contains("farmer@example.test"));
    }
}
