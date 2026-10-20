package com.prashant.KhetWallah.config;

import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import java.util.Locale;
import java.util.Set;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

@Configuration
public class SecurityConfig {

    @Bean
    public UserDetailsService userDetailsService(
            AppUserRepository repository
    ) {
        return email -> {
            String normalizedEmail =
                    email.strip().toLowerCase(Locale.ROOT);

            AppUser user = repository.findByEmail(normalizedEmail)
                    .orElseThrow(() ->
                            new UsernameNotFoundException(
                                    "Invalid email or password"
                            )
                    );

            return new com.prashant.KhetWallah.user.AccountPrincipal(user);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AppUserRepository repository
    ) throws Exception {

        HttpSessionRequestCache pageRequests = new HttpSessionRequestCache();
        Set<String> returnPages = Set.of("/account.html", "/create-listing.html", "/my-listings.html",
                "/my-orders.html", "/received-orders.html", "/notifications.html", "/saved.html", "/messages.html", "/farmer-profile.html", "/insights.html", "/admin.html");
        pageRequests.setRequestMatcher(request -> "GET".equals(request.getMethod())
                && returnPages.contains(request.getRequestURI().substring(request.getContextPath().length())));
        http
                .requestCache(cache -> cache.requestCache(pageRequests))
                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers("/admin.html", "/api/admin/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/theme.css",
                                "/enhancements.css", "/app-shell.js", "/sw.js", "/offline.html",
                                "/manifest.webmanifest", "/icons/**",
                                "/farmer.html", "/farmer.js", "/saved-controls.js",
                                "/api/farmers/**",
                                "/register.html",
                                "/login",
                                "/login.html",
                                "/login.js",
                                "/recovery.html", "/recovery.js", "/i18n.js",
                                "/listing-reviews.js",
                                "/listing.html",
                                "/listing-page.js",
                                "/photo-preview.js",
                                "/api/status",
                                "/api/csrf",
                                "/listing-photos.js",
                                "/listing-reports.js",
                                "/api/listings",
                                "/api/listings/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/recovery/start", "/api/auth/recovery/finish",
                                "/api/auth/verification/start",
                                "/api/auth/verification/resend"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(new SafeLoginSuccessHandler())
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .addFilterAfter(new ActiveAccountFilter(repository), AuthorizationFilter.class);

        return http.build();
    }
}
