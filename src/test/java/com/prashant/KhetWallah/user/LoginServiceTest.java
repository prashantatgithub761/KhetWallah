package com.prashant.KhetWallah.user;

import com.prashant.KhetWallah.config.PasswordConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginServiceTest {

    private final AppUserRepository repository =
            mock(AppUserRepository.class);

    private final PasswordEncoder encoder =
            new PasswordConfig().passwordEncoder();

    private final LoginService service =
            new LoginService(repository, encoder);

    @Test
    void correctPasswordReturnsAccountDetails() {
        AppUser user = new AppUser(
                "Demo Farmer",
                "farmer@example.com",
                encoder.encode("Local-Demo-Only-2026!")
        );

        when(repository.findByEmail("farmer@example.com"))
                .thenReturn(Optional.of(user));

        UserResponse response = service.checkCredentials(
                "Farmer@Example.com",
                "Local-Demo-Only-2026!"
        );

        assertEquals("Demo Farmer", response.displayName());
        assertEquals("farmer@example.com", response.email());
    }

    @Test
    void wrongPasswordIsRejected() {
        AppUser user = new AppUser(
                "Demo Farmer",
                "farmer@example.com",
                encoder.encode("Local-Demo-Only-2026!")
        );

        when(repository.findByEmail("farmer@example.com"))
                .thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.checkCredentials(
                        "farmer@example.com",
                        "Wrong-Password-2026!"
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Invalid email or password", exception.getReason());
    }

    @Test
    void unknownEmailIsRejected() {
        when(repository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.checkCredentials(
                        "missing@example.com",
                        "Local-Demo-Only-2026!"
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Invalid email or password", exception.getReason());
    }
}