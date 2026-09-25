package com.prashant.KhetWallah.listing;

import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListingEditServiceTest {

    private final ListingRepository listingRepository =
            mock(ListingRepository.class);

    private final AppUserRepository appUserRepository =
            mock(AppUserRepository.class);

    private final ListingService service = new ListingService(
            listingRepository,
            appUserRepository
    );

    private ListingEntity existingListing() {
        AppUser owner = new AppUser(
                "Demo Farmer",
                "farmer@example.com",
                "unused-test-hash"
        );

        return new ListingEntity(
                "Tomato",
                owner,
                new BigDecimal("40.50"),
                new BigDecimal("18.75"),
                "Meerut"
        );
    }

    private UpdateListingRequest updateRequest() {
        return new UpdateListingRequest(
                new BigDecimal("30.00"),
                new BigDecimal("20.00"),
                "Jani"
        );
    }

    @Test
    void ownerCanUpdateListing() {
        ListingEntity listing = existingListing();
        AppUser originalOwner = listing.getOwner();

        when(listingRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(listing));

        service.updateListing(
                3L,
                updateRequest(),
                "farmer@example.com"
        );

        assertEquals(
                new BigDecimal("30.00"),
                listing.getAvailableQuantityKg()
        );
        assertEquals(
                new BigDecimal("20.00"),
                listing.getPricePerKg()
        );
        assertEquals("Jani", listing.getPickupArea());
        assertSame(originalOwner, listing.getOwner());
        assertEquals("Tomato", listing.getProduceName());
    }

    @Test
    void anotherAccountCannotChangeListing() {
        ListingEntity listing = existingListing();

        when(listingRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(listing));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateListing(
                        3L,
                        updateRequest(),
                        "buyer@example.com"
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        assertEquals(
                new BigDecimal("40.50"),
                listing.getAvailableQuantityKg()
        );
        assertEquals(
                new BigDecimal("18.75"),
                listing.getPricePerKg()
        );
        assertEquals("Meerut", listing.getPickupArea());
    }
    @Test
    void ownerCanCloseListingAndRepeatTheAction() {
        ListingEntity listing = existingListing();

        when(listingRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(listing));

        service.closeListing(3L, "farmer@example.com");

        assertEquals(ListingStatus.CLOSED, listing.getStatus());

        assertDoesNotThrow(() ->
                service.closeListing(3L, "farmer@example.com")
        );

        assertEquals(ListingStatus.CLOSED, listing.getStatus());
        assertEquals("Tomato", listing.getProduceName());
    }
    @Test
    void anotherAccountCannotCloseListing() {
        ListingEntity listing = existingListing();

        when(listingRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(listing));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.closeListing(
                        3L,
                        "buyer@example.com"
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        assertEquals(ListingStatus.ACTIVE, listing.getStatus());
    }
    @Test
    void ownerCannotEditClosedListing() {
        ListingEntity listing = existingListing();
        listing.close();

        when(listingRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(listing));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateListing(
                        3L,
                        updateRequest(),
                        "farmer@example.com"
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(ListingStatus.CLOSED, listing.getStatus());
        assertEquals(
                new BigDecimal("40.50"),
                listing.getAvailableQuantityKg()
        );
        assertEquals(
                new BigDecimal("18.75"),
                listing.getPricePerKg()
        );
        assertEquals("Meerut", listing.getPickupArea());
    }
}