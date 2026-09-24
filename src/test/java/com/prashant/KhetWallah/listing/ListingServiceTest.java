package com.prashant.KhetWallah.listing;

import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ListingServiceTest {

    private final ListingRepository listingRepository =
            mock(ListingRepository.class);

    private final AppUserRepository appUserRepository =
            mock(AppUserRepository.class);

    private final ListingService service = new ListingService(
            listingRepository,
            appUserRepository
    );

    @Test
    void newListingBelongsToTheAuthenticatedAccount() {
        // Arrange: account found using the authenticated email.
        AppUser owner = new AppUser(
                "Demo Farmer",
                "farmer@example.com",
                "unused-hash-in-this-test"
        );

        when(appUserRepository.findByEmail("farmer@example.com"))
                .thenReturn(Optional.of(owner));

        when(listingRepository.save(any(ListingEntity.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0, ListingEntity.class));

        CreateListingRequest request = new CreateListingRequest(
                "Onion",
                new BigDecimal("75.50"),
                new BigDecimal("22.00"),
                "Meerut"
        );

        // Act.
        ProduceListing response = service.createListing(
                request,
                "farmer@example.com"
        );

        // Assert: inspect the listing sent for saving.
        ArgumentCaptor<ListingEntity> captor =
                ArgumentCaptor.forClass(ListingEntity.class);

        verify(listingRepository).save(captor.capture());

        ListingEntity savedListing = captor.getValue();

        assertSame(owner, savedListing.getOwner());
        assertEquals("Demo Farmer", savedListing.getFarmerName());
        assertEquals("Demo Farmer", response.getFarmerName());
        assertEquals("Onion", savedListing.getProduceName());
    }

    @Test
    void missingAccountCannotCreateAListing() {
        when(appUserRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        CreateListingRequest request = new CreateListingRequest(
                "Onion",
                new BigDecimal("75.50"),
                new BigDecimal("22.00"),
                "Meerut"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createListing(
                        request,
                        "missing@example.com"
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(listingRepository);
    }
}