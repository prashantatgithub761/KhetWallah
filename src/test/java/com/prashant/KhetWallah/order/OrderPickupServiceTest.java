package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.listing.ListingRepository;
import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPickupServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final ListingRepository listings = mock(ListingRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final OrderService service = new OrderService(orders, listings, users);
    private OrderEntity order;
    private ListingEntity listing;
    private final Instant future = Instant.now().plusSeconds(86400);

    @BeforeEach
    void prepare() {
        listing = new ListingEntity("Tomato",
                new AppUser("Farmer", "farmer@example.com", "hash"),
                new BigDecimal("30.00"), new BigDecimal("20.00"), "Jani");
        order = new OrderEntity(listing,
                new AppUser("Buyer", "buyer@example.com", "hash"), new BigDecimal("2.00"));
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
    }

    private SchedulePickupRequest validRequest() {
        return new SchedulePickupRequest(future, "  Village market gate  ");
    }

    private void acceptedWithPickup() {
        order.accept();
        service.schedulePickup(1L, validRequest(), "farmer@example.com");
    }

    @Test
    void ownerCanScheduleAndUpdateWithoutChangingStock() {
        acceptedWithPickup();
        assertEquals(future, order.getPickupAt());
        assertEquals("Village market gate", order.getPickupInstructions());
        Instant revised = future.plusSeconds(3600);
        service.schedulePickup(1L, new SchedulePickupRequest(revised, "East gate"), "farmer@example.com");
        assertEquals(revised, order.getPickupAt());
        assertEquals("East gate", order.getPickupInstructions());
        assertEquals(new BigDecimal("30.00"), listing.getAvailableQuantityKg());
        verifyNoInteractions(listings);
    }

    @Test
    void buyerAndStrangerCannotSchedulePickup() {
        order.accept();
        for (String email : List.of("buyer@example.com", "stranger@example.com")) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.schedulePickup(1L, validRequest(), email));
            assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        }
        assertNull(order.getPickupAt());
    }

    @Test
    void pendingOrderCannotBeScheduled() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.schedulePickup(1L, validRequest(), "farmer@example.com"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void invalidInputDoesNotOverwriteExistingPickup() {
        acceptedWithPickup();
        for (SchedulePickupRequest request : List.of(
                new SchedulePickupRequest(null, "Gate"),
                new SchedulePickupRequest(Instant.now().minusSeconds(60), "Gate"),
                new SchedulePickupRequest(future, null),
                new SchedulePickupRequest(future, "   "),
                new SchedulePickupRequest(future, "x".repeat(501)))) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.schedulePickup(1L, request, "farmer@example.com"));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
            assertEquals(future, order.getPickupAt());
            assertEquals("Village market gate", order.getPickupInstructions());
        }
    }

    @Test
    void buyerCannotCompleteBeforePickupDetailsExist() {
        order.accept();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.completeOrder(1L, "buyer@example.com"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
    }

    @Test
    void buyerCanConfirmCollectionRepeatedlyWithoutChangingStock() {
        acceptedWithPickup();
        service.completeOrder(1L, "buyer@example.com");
        service.completeOrder(1L, "buyer@example.com");
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(new BigDecimal("30.00"), listing.getAvailableQuantityKg());
        verifyNoInteractions(listings);
    }

    @Test
    void farmerAndOtherBuyerCannotCompleteEvenAfterCompletion() {
        acceptedWithPickup();
        for (String email : List.of("farmer@example.com", "stranger@example.com")) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.completeOrder(1L, email));
            assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        }
        service.completeOrder(1L, "buyer@example.com");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.completeOrder(1L, "farmer@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void completedOrderCannotBeRescheduled() {
        acceptedWithPickup();
        service.completeOrder(1L, "buyer@example.com");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.schedulePickup(1L, validRequest(), "farmer@example.com"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void cancelledOrderCannotBeCompleted() {
        order.cancel();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.completeOrder(1L, "buyer@example.com"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void missingOrderReturnsNotFound() {
        when(orders.findByIdForUpdate(999L)).thenReturn(Optional.empty());
        ResponseStatusException pickup = assertThrows(ResponseStatusException.class,
                () -> service.schedulePickup(999L, validRequest(), "farmer@example.com"));
        ResponseStatusException complete = assertThrows(ResponseStatusException.class,
                () -> service.completeOrder(999L, "buyer@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, pickup.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, complete.getStatusCode());
    }

    @Test
    void bothPrivateOrderListsIncludePickupDetails() {
        acceptedWithPickup();
        when(orders.findByBuyer_EmailOrderByIdDesc(eq("buyer@example.com"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orders.findByListing_Owner_EmailOrderByIdDesc(eq("farmer@example.com"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        OrderResponse buyer = service.getMyOrders("buyer@example.com", 0, 10).content().getFirst();
        OrderResponse farmer = service.getReceivedOrders("farmer@example.com", 0, 10).content().getFirst();
        assertEquals(future, buyer.pickupAt());
        assertEquals("Village market gate", buyer.pickupInstructions());
        assertEquals(buyer, farmer);
    }
}
