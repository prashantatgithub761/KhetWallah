package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.listing.ListingRepository;
import com.prashant.KhetWallah.listing.ListingStatus;
import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCancellationServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ListingRepository listings = mock(ListingRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);

    private final OrderService service =
            new OrderService(orders, listings, users);

    private ListingEntity listing;
    private OrderEntity order;

    @BeforeEach
    void preparePendingOrder() {
        AppUser farmer = new AppUser(
                "Demo Farmer", "farmer@example.com", "unused-hash"
        );

        AppUser buyer = new AppUser(
                "Demo Buyer", "buyer@example.com", "unused-hash"
        );

        // Stock is already 40 because this order reserved 10 from 50.
        listing = spy(new ListingEntity(
                "Potato",
                farmer,
                new BigDecimal("40.00"),
                new BigDecimal("24.00"),
                "Meerut"
        ));

        doReturn(5L).when(listing).getId();

        order = new OrderEntity(
                listing,
                buyer,
                new BigDecimal("10.00")
        );

        when(orders.findByIdForUpdate(1L))
                .thenReturn(Optional.of(order));

        when(listings.findByIdForUpdate(5L))
                .thenReturn(Optional.of(listing));
    }

    @Test
    void repeatedCancellationRestoresStockOnlyOnce() {
        service.cancelOrder(1L, "buyer@example.com");
        service.cancelOrder(1L, "buyer@example.com");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(
                new BigDecimal("50.00"),
                listing.getAvailableQuantityKg()
        );

        verify(listing, times(1))
                .restoreStock(new BigDecimal("10.00"));
    }

    @Test
    void anotherAccountCannotCancelTheOrder() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.cancelOrder(1L, "someone@example.com")
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(
                new BigDecimal("40.00"),
                listing.getAvailableQuantityKg()
        );

        verifyNoInteractions(listings);
    }

    @Test
    void cancellationDoesNotReopenAClosedListing() {
        listing.close();

        service.cancelOrder(1L, "buyer@example.com");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(ListingStatus.CLOSED, listing.getStatus());
        assertEquals(
                new BigDecimal("50.00"),
                listing.getAvailableQuantityKg()
        );
    }
}