package com.prashant.KhetWallah.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.io.IOException;
import java.util.Set;

/** Accept only known local destinations; never redirect to a URL supplied verbatim by a client. */
public class SafeLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    private static final Set<String> PAGES = Set.of("/account.html", "/notifications.html", "/saved.html", "/messages.html", "/farmer-profile.html", "/insights.html", "/my-orders.html",
            "/received-orders.html", "/my-listings.html", "/create-listing.html");

    public SafeLoginSuccessHandler() {
        setDefaultTargetUrl("/account.html");
    }

    static boolean allowed(String target) {
        return target != null && (PAGES.contains(target)
                || (target.matches("/farmer\\.html\\?id=[1-9][0-9]{0,17}") || target.matches("/listing\\.html\\?id=[1-9][0-9]{0,17}")));
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        String target = request.getParameter("returnTo");
        if (allowed(target)) {
            new HttpSessionRequestCache().removeRequest(request, response);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, target);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
