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

import java.util.Locale;

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

            return User.withUsername(user.getEmail())
                    .password(user.getPasswordHash())
                    .roles("USER")
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers(
                                HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/register.html",
                                "/api/status",
                                "/api/csrf",
                                "/listing-photos.js",
                                "/api/listings",
                                "/api/listings/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }
}