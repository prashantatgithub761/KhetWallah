package com.prashant.KhetWallah.user;

import com.prashant.KhetWallah.config.PasswordConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {

    @Test
    void registrationSavesAHashThatMatchesThePassword() {
        // Arrange: prepare the service and a fake database repository.
        AppUserRepository repository = mock(AppUserRepository.class);

        PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

        RegistrationService service =
                new RegistrationService(repository, encoder);

        when(repository.existsByEmail("farmer@example.com"))
                .thenReturn(false);

        when(repository.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0, AppUser.class));

        String rawPassword = "Local-Demo-Only-2026!";

        RegisterRequest request = new RegisterRequest(
                "Demo Farmer",
                "Farmer@Example.com",
                rawPassword
        );

        // Act: register the account.
        UserResponse response = service.register(request);

        // Assert: inspect the account sent to the repository.
        ArgumentCaptor<AppUser> captor =
                ArgumentCaptor.forClass(AppUser.class);

        verify(repository).saveAndFlush(captor.capture());

        AppUser savedUser = captor.getValue();
        String storedHash = savedUser.getPasswordHash();

        assertNotNull(storedHash);
        assertNotEquals(rawPassword, storedHash);

        assertTrue(encoder.matches(rawPassword, storedHash));
        assertFalse(encoder.matches("Wrong-Password-2026!", storedHash));

        assertEquals("farmer@example.com", savedUser.getEmail());
        assertEquals("farmer@example.com", response.email());
        assertEquals("Demo Farmer", response.displayName());
    }
    @Test
    void registrationRejectsAnEmailThatAlreadyExists() {
        // Arrange: pretend this email already exists in the database.
        AppUserRepository repository = mock(AppUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        RegistrationService service =
                new RegistrationService(repository, encoder);

        when(repository.existsByEmail("farmer@example.com"))
                .thenReturn(true);

        RegisterRequest request = new RegisterRequest(
                "Another Farmer",
                "Farmer@Example.com",
                "Local-Demo-Only-2026!"
        );

        // Act: registration should throw an exception.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.register(request)
        );

        // Assert: reject the duplicate before hashing or saving.
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        assertEquals(
                "An account with this email already exists",
                exception.getReason()
        );

        verify(repository).existsByEmail("farmer@example.com");
        verify(repository, never()).saveAndFlush(any(AppUser.class));
        verifyNoInteractions(encoder);
    }
}