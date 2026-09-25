package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.listing.ListingRepository;
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

class OrderDecisionServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ListingRepository listings = mock(ListingRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);

    private final OrderService service =
            new OrderService(orders, listings, users);

    private ListingEntity listing;
    private OrderEntity order;

    @BeforeEach
    void prepareOrder() {
        AppUser farmer = new AppUser(
                "Demo Farmer", "farmer@example.com", "unused-hash"
        );

        AppUser buyer = new AppUser(
                "Demo Buyer", "buyer@example.com", "unused-hash"
        );

        listing = spy(new ListingEntity(
                "Potato",
                farmer,
                new BigDecimal("40.00"),
                new BigDecimal("24.00"),
                "Meerut"
        ));

        doReturn(5L).when(listing).getId();

        order = new OrderEntity(
                listing, buyer, new BigDecimal("10.00")
        );

        when(orders.findByIdForUpdate(1L))
                .thenReturn(Optional.of(order));

        when(listings.findByIdForUpdate(5L))
                .thenReturn(Optional.of(listing));
    }

    @Test
    void repeatedAcceptanceDoesNotReduceStockAgain() {
        service.acceptOrder(1L, "farmer@example.com");
        service.acceptOrder(1L, "farmer@example.com");

        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        assertEquals(
                new BigDecimal("40.00"),
                listing.getAvailableQuantityKg()
        );
    }

    @Test
    void repeatedRejectionRestoresStockOnlyOnce() {
        service.rejectOrder(1L, "farmer@example.com");
        service.rejectOrder(1L, "farmer@example.com");

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertEquals(
                new BigDecimal("50.00"),
                listing.getAvailableQuantityKg()
        );

        verify(listing, times(1))
                .restoreStock(new BigDecimal("10.00"));
    }

    @Test
    void anotherAccountCannotAcceptOrReject() {
        ResponseStatusException acceptError = assertThrows(
                ResponseStatusException.class,
                () -> service.acceptOrder(1L, "buyer@example.com")
        );

        ResponseStatusException rejectError = assertThrows(
                ResponseStatusException.class,
                () -> service.rejectOrder(1L, "buyer@example.com")
        );

        assertEquals(HttpStatus.FORBIDDEN, acceptError.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, rejectError.getStatusCode());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(
                new BigDecimal("40.00"),
                listing.getAvailableQuantityKg()
        );
    }

    @Test
    void acceptedOrderCannotBeRejected() {
        service.acceptOrder(1L, "farmer@example.com");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.rejectOrder(1L, "farmer@example.com")
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        assertEquals(
                new BigDecimal("40.00"),
                listing.getAvailableQuantityKg()
        );
    }

    @Test
    void rejectedOrderCannotBeAccepted() {
        service.rejectOrder(1L, "farmer@example.com");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.acceptOrder(1L, "farmer@example.com")
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertEquals(
                new BigDecimal("50.00"),
                listing.getAvailableQuantityKg()
        );
    }
    @Test
    void acceptedOrderCanBeCompletedRepeatedlyWithoutChangingStock() {
        service.acceptOrder(1L, "farmer@example.com");

        service.schedulePickup(1L, new SchedulePickupRequest(
                java.time.Instant.now().plusSeconds(86400), "Main village gate"
        ), "farmer@example.com");
        service.completeOrder(1L, "buyer@example.com");
        service.completeOrder(1L, "buyer@example.com");

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(
                new BigDecimal("40.00"),
                listing.getAvailableQuantityKg()
        );
    }

    @Test
    void pendingOrderCannotBeCompleted() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.completeOrder(1L, "buyer@example.com")
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(
                new BigDecimal("40.00"),
                listing.getAvailableQuantityKg()
        );
    }

    @Test
    void farmerCannotMarkOrderCompleted() {
        service.acceptOrder(1L, "farmer@example.com");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.completeOrder(1L, "farmer@example.com")
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        assertEquals(
                new BigDecimal("40.00"),
                listing.getAvailableQuantityKg()
        );
    }
}