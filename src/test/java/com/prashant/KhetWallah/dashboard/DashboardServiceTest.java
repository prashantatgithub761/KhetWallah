package com.prashant.KhetWallah.dashboard;

import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    @Test
    void resolvesTheSignedInAccountAndScopesEveryQueryToItsId() {
        var users = mock(AppUserRepository.class);
        var repository = mock(DashboardRepository.class);
        var user = new AppUser("Farmer", "farmer@example.test", "private-hash");
        ReflectionTestUtils.setField(user, "id", 42L);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(repository.activeListings(42L)).thenReturn(3L);
        when(repository.pendingOrders(42L)).thenReturn(2L);
        when(repository.pendingReceivedOrders(42L)).thenReturn(4L);
        when(repository.upcomingPickups(eq(42L), any(Instant.class))).thenReturn(0L);
        when(repository.upcomingPickupDetails(eq(42L), any(Instant.class))).thenReturn(List.of());
        var response = new DashboardService(repository, users).forAccount(user.getEmail());
        assertEquals(3, response.activeListings());
        assertEquals(2, response.pendingOrders());
        assertEquals(4, response.pendingReceivedOrders());
        assertTrue(response.pickups().isEmpty());
        verify(repository).activeListings(42L);
        verify(repository).pendingOrders(42L);
        verify(repository).pendingReceivedOrders(42L);
        verify(repository).upcomingPickups(42L, response.asOf());
        verify(repository).upcomingPickupDetails(42L, response.asOf());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingAccountDoesNotRunDashboardQueries() {
        var users = mock(AppUserRepository.class);
        var repository = mock(DashboardRepository.class);
        when(users.findByEmail("missing@example.test")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> new DashboardService(repository, users).forAccount("missing@example.test"));
        verifyNoInteractions(repository);
    }
}
