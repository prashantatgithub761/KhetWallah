package com.prashant.KhetWallah.config;

import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({LoginPageController.class, CsrfController.class})
@Import({SecurityConfig.class, PasswordConfig.class})
class LoginPageSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean AppUserRepository users;

    @BeforeEach
    void activeAccount() {
        when(users.findByEmail("farmer@example.test")).thenReturn(Optional.of(
                new AppUser("Demo farmer", "farmer@example.test",
                        encoder.encode("Only-a-test-passphrase"))));
    }

    @Test
    void guestCanOpenCustomPageAndPrepareSecurityToken() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/login.html"));
        mvc.perform(get("/login.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"login-form\"")));
        mvc.perform(get("/login.js"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void protectedPageRedirectsGuestToCustomSignIn() throws Exception {
        mvc.perform(get("/account.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void validLoginUsesAccountPageWhenThereIsNoSavedRequest() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "farmer@example.test")
                        .param("password", "Only-a-test-passphrase"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/account.html"))
                .andExpect(authenticated().withUsername("farmer@example.test"));
    }

    @Test
    void successfulLoginReturnsToOriginallyRequestedProtectedPage() throws Exception {
        MvcResult protectedPage = mvc.perform(get("/my-orders.html"))
                .andExpect(status().isFound())
                .andReturn();
        MockHttpSession session = (MockHttpSession) protectedPage.getRequest().getSession(false);
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", "farmer@example.test")
                        .param("password", "Only-a-test-passphrase"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith("http://localhost/my-orders.html")))
                .andExpect(authenticated());
    }

    @Test
    void invalidLoginReturnsGenericFailureFlagAndDoesNotAuthenticate() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "farmer@example.test")
                        .param("password", "incorrect-passphrase"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void onlyKnownLocalReturnDestinationsAreAccepted() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "farmer@example.test")
                        .param("password", "Only-a-test-passphrase").param("returnTo", "/listing.html?id=3"))
                .andExpect(redirectedUrl("/listing.html?id=3"));
        for (String unsafe : java.util.List.of("https://evil.example", "//evil.example", "/listing.html?id=3&next=https://evil.example")) {
            mvc.perform(post("/login").with(csrf()).param("username", "farmer@example.test")
                            .param("password", "Only-a-test-passphrase").param("returnTo", unsafe))
                    .andExpect(redirectedUrl("/account.html"));
        }
    }

    @Test
    void backgroundApiRequestsCannotReplaceThePostLoginPage() throws Exception {
        MvcResult result = mvc.perform(get("/api/me/dashboard")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        var request = post("/login").with(csrf()).param("username", "farmer@example.test")
                .param("password", "Only-a-test-passphrase");
        if (session != null) request.session(session);
        mvc.perform(request).andExpect(redirectedUrl("/account.html"));
    }

    @Test
    void loginWithoutCsrfIsStillRejected() throws Exception {
        mvc.perform(post("/login")
                        .param("username", "farmer@example.test")
                        .param("password", "Only-a-test-passphrase"))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }
}
