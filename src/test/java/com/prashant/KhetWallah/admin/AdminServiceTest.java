package com.prashant.KhetWallah.admin;

import com.prashant.KhetWallah.listing.*;
import com.prashant.KhetWallah.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminServiceTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final ListingRepository listings = mock(ListingRepository.class);
    private final AdminActionRepository actions = mock(AdminActionRepository.class);
    private final AdminService service = new AdminService(users, listings, actions);

    private void admin() {
        AppUser account = mock(AppUser.class);
        when(account.getRole()).thenReturn(UserRole.ADMIN);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(account));
    }

    @Test
    void ordinaryUserCannotModerate() {
        AppUser account = new AppUser("Buyer", "buyer@example.com", "hash");
        when(users.findByEmail("buyer@example.com")).thenReturn(Optional.of(account));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.hideListing(9L, "Inappropriate content", "buyer@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(listings, actions);
    }

    @Test
    void suspendingOwnerHidesActiveListingsAndRecordsActions() {
        admin();
        AppUser farmer = new AppUser("Farmer", "farmer@example.com", "hash");
        ListingEntity active = new ListingEntity("Tomato", farmer,
                new BigDecimal("10"), new BigDecimal("20"), "Meerut");
        when(users.findByIdForUpdate(4L)).thenReturn(Optional.of(farmer));
        when(listings.findByOwner_IdAndStatus(4L, ListingStatus.ACTIVE)).thenReturn(List.of(active));

        service.suspendUser(4L, "Repeated abuse", "admin@example.com");

        assertTrue(farmer.isSuspended());
        assertEquals(ListingStatus.HIDDEN, active.getStatus());
        verify(actions, times(2)).save(any(AdminAction.class));
    }

    @Test
    void cannotRestoreListingWhileOwnerIsSuspended() {
        admin();
        AppUser farmer = new AppUser("Farmer", "farmer@example.com", "hash");
        farmer.suspend();
        ListingEntity listing = new ListingEntity("Tomato", farmer,
                new BigDecimal("10"), new BigDecimal("20"), "Meerut");
        listing.hide();
        when(listings.findByIdForUpdate(8L)).thenReturn(Optional.of(listing));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.restoreListing(8L, "Appeal accepted", "admin@example.com"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(ListingStatus.HIDDEN, listing.getStatus());
        verifyNoInteractions(actions);
    }
    @Test
    void legacyListingWithoutOwnerCanBeDisplayed() {
        ListingEntity legacy = mock(ListingEntity.class);
        when(legacy.getStatus()).thenReturn(ListingStatus.ACTIVE);
        assertEquals("Unassigned", new AdminService.ListingView(legacy).ownerEmail());
    }

    @Test
    void legacyListingCannotBeRestoredWithoutAnOwner() {
        admin();
        ListingEntity legacy = mock(ListingEntity.class);
        when(legacy.getStatus()).thenReturn(ListingStatus.HIDDEN);
        when(listings.findByIdForUpdate(9L)).thenReturn(Optional.of(legacy));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.restoreListing(9L, "Reviewed", "admin@example.com"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(legacy, never()).restore();
    }
}
