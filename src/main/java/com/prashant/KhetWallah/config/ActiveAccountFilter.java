package com.prashant.KhetWallah.config;

import com.prashant.KhetWallah.user.AppUserRepository;
import com.prashant.KhetWallah.user.AccountPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ActiveAccountFilter extends OncePerRequestFilter {
    private final AppUserRepository users;

    public ActiveAccountFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && users.findByEmail(authentication.getName())
                        .map(user -> user.isSuspended()
                                || user.getAuthVersion() != credentialVersion(authentication)
                                || !authentication.getAuthorities().stream().anyMatch(
                                        authority -> authority.getAuthority()
                                                .equals("ROLE_" + user.getRole().name())))
                        .orElse(true)) {
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "This account is unavailable. Sign in again or contact the administrator.");
            return;
        }
        chain.doFilter(request, response);
    }

    private long credentialVersion(Authentication authentication) {
        return authentication.getPrincipal() instanceof AccountPrincipal principal
                ? principal.getAuthVersion() : 0L;
    }
}
