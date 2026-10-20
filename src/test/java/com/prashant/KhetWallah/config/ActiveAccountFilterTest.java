package com.prashant.KhetWallah.config;

import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AccountPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActiveAccountFilterTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final ActiveAccountFilter filter = new ActiveAccountFilter(users);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    private void authenticate(String role) {
        // Deliberately omit login metadata: it must never bypass account checks.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.com", null,
                        AuthorityUtils.createAuthorityList(role)));
    }

    @Test
    void suspendedAccountWithoutLoginMetadataIsBlockedAndSessionInvalidated() throws Exception {
        AppUser user = new AppUser("User", "user@example.com", "hash");
        user.suspend();
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        authenticate("ROLE_USER");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
    }

    @Test
    void deletedAccountIsBlocked() throws Exception {
        authenticate("ROLE_USER");
        when(users.findByEmail("user@example.com")).thenReturn(Optional.empty());
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void staleAdminRoleIsBlocked() throws Exception {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(
                new AppUser("User", "user@example.com", "hash")));
        authenticate("ROLE_ADMIN");
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void activeAccountContinues() throws Exception {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(
                new AppUser("User", "user@example.com", "hash")));
        authenticate("ROLE_USER");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void passwordResetInvalidatesThePreviouslyAuthenticatedPrincipalAndSession() throws Exception {
        AppUser user = new AppUser("User", "user@example.com", "hash");
        var principal = new AccountPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        ReflectionTestUtils.setField(user, "authVersion", 1L);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
    }

    @Test
    void signingInWithTheNewCredentialVersionRestoresAccess() throws Exception {
        AppUser user = new AppUser("User", "user@example.com", "hash");
        ReflectionTestUtils.setField(user, "authVersion", 2L);
        var principal = new AccountPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void legacyPrincipalCannotBypassAPasswordReset() throws Exception {
        AppUser user = new AppUser("User", "user@example.com", "hash");
        ReflectionTestUtils.setField(user, "authVersion", 1L);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        authenticate("ROLE_USER");
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void signedOutRequestContinuesWithoutAccountLookup() throws Exception {
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(users);
    }
}
