package com.prashant.KhetWallah.listing;

import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ListingCropDetailsTest {
    private final ListingRepository repository = mock(ListingRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final ListingService service = new ListingService(repository, users);
    private final AppUser owner = new AppUser("Farmer", "farmer@example.com", "unused-test-hash");
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private ListingEntity listing() {
        return new ListingEntity("Onion", owner, n("40.00"), n("20.00"), "Meerut",
                " Pusa Red ", LocalDate.now().minusDays(2), n("5.00"), " Bring bags ");
    }
    private void stubCreate() {
        when(users.findByEmail("farmer@example.com")).thenReturn(Optional.of(owner));
        when(repository.save(any(ListingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
    @Test void oldConstructorsKeepSmallMinimumAndNoInventedMetadata() {
        ListingEntity entity = new ListingEntity("Onion", owner, n("2.00"), n("20.00"), "Meerut");
        assertEquals(n("0.01"), entity.getMinimumOrderQuantityKg());
        assertNull(entity.getHarvestDate());
        assertNull(entity.getVariety());
        stubCreate();
        ProduceListing result = service.createListing(new CreateListingRequest("Onion", n("2.00"), n("20.00"), "Meerut"), "farmer@example.com");
        assertEquals(n("0.01"), result.getMinimumOrderQuantityKg());
        assertNull(result.getPackagingDetails());
    }
    @Test void creationExposesTrimmedMetadataAndReportedDate() {
        stubCreate();
        LocalDate date = LocalDate.now().minusDays(3);
        ProduceListing result = service.createListing(new CreateListingRequest("Onion", n("40.00"), n("20.00"), "Meerut",
                " Pusa Red ", date, n("5.00"), " Bring bags "), "farmer@example.com");
        assertEquals("Pusa Red", result.getVariety());
        assertEquals(date, result.getHarvestDate());
        assertEquals(n("5.00"), result.getMinimumOrderQuantityKg());
        assertEquals("Bring bags", result.getPackagingDetails());
    }
    @Test void minimumLargerThanStockCannotBeCreated() {
        stubCreate();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.createListing(new CreateListingRequest("Onion", n("2.00"), n("20.00"), "Meerut",
                        null, null, n("3.00"), null), "farmer@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(repository, never()).save(any());
    }
    @Test void futureHarvestDateIsRejectedEvenWhenServiceIsCalledDirectly() {
        stubCreate();
        assertThrows(ResponseStatusException.class, () -> service.createListing(new CreateListingRequest("Onion", n("20.00"), n("20.00"), "Meerut",
                null, LocalDate.now().plusDays(1), null, null), "farmer@example.com"));
        verify(repository, never()).save(any());
    }
    @Test void omittedMetadataIsPreservedByOldUpdateClients() {
        ListingEntity entity = listing();
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(entity));
        ProduceListing result = service.updateListing(7L, new UpdateListingRequest(n("30.00"), n("22.00"), "Jani"), "farmer@example.com");
        assertEquals("Pusa Red", result.getVariety());
        assertEquals(n("5.00"), result.getMinimumOrderQuantityKg());
        assertEquals("Bring bags", result.getPackagingDetails());
        assertNotNull(result.getHarvestDate());
    }
    @Test void ownerCanClearOptionalTextAndReportedDateExplicitly() {
        ListingEntity entity = listing();
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(entity));
        ProduceListing result = service.updateListing(7L, new UpdateListingRequest(n("30.00"), n("22.00"), "Jani", "", null, n("1.00"), "", true), "farmer@example.com");
        assertNull(result.getVariety()); assertNull(result.getHarvestDate()); assertNull(result.getPackagingDetails());
        assertEquals(n("1.00"), result.getMinimumOrderQuantityKg());
    }
    @Test void zeroStockMayKeepItsConfiguredMinimum() {
        ListingEntity entity = listing();
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(entity));
        assertDoesNotThrow(() -> service.updateListing(7L, new UpdateListingRequest(n("0.00"), n("22.00"), "Jani"), "farmer@example.com"));
        assertEquals(n("5.00"), entity.getMinimumOrderQuantityKg());
    }
    @Test void invalidMinimumDoesNotPartiallyUpdateEntity() {
        ListingEntity entity = listing();
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(entity));
        assertThrows(ResponseStatusException.class, () -> service.updateListing(7L,
                new UpdateListingRequest(n("2.00"), n("22.00"), "Jani"), "farmer@example.com"));
        assertEquals(n("40.00"), entity.getAvailableQuantityKg());
        assertEquals("Meerut", entity.getPickupArea());
    }
    @Test void reservationCannotBypassMinimumAndKeepsStockWhenRejected() {
        ListingEntity entity = listing();
        assertThrows(IllegalArgumentException.class, () -> entity.reserveStock(n("4.99")));
        assertEquals(n("40.00"), entity.getAvailableQuantityKg());
        entity.reserveStock(n("5.00"));
        assertEquals(n("35.00"), entity.getAvailableQuantityKg());
    }
    @Test void requestConstraintsRejectLongMetadataAndFutureDates() {
        try (var validatorFactory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var request = new CreateListingRequest("Onion", n("20.00"), n("20.00"), "Meerut",
                    "v".repeat(101), LocalDate.now().plusDays(1), n("0.00"), "p".repeat(301));
            var fields = validator.validate(request).stream().map(v -> v.getPropertyPath().toString()).toList();
            assertTrue(fields.containsAll(java.util.List.of("variety", "harvestDate", "minimumOrderQuantityKg", "packagingDetails")));
        }
    }
}
