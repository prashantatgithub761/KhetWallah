package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.listing.ListingRepository;
import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ListingRepository listings = mock(ListingRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);

    private final OrderService service =
            new OrderService(orders, listings, users);

    private ListingEntity listing;

    @BeforeEach
    void prepareAccountsAndListing() {
        AppUser farmer = mock(AppUser.class);
        when(farmer.getId()).thenReturn(1L);
        when(farmer.getDisplayName()).thenReturn("Demo Farmer");

        AppUser buyer = mock(AppUser.class);
        when(buyer.getId()).thenReturn(2L);

        listing = new ListingEntity(
                "Potato",
                farmer,
                new BigDecimal("50.00"),
                new BigDecimal("24.00"),
                "Meerut"
        );

        when(users.findByEmail("buyer@example.com"))
                .thenReturn(Optional.of(buyer));

        when(users.findByEmail("farmer@example.com"))
                .thenReturn(Optional.of(farmer));

        when(listings.findByIdForUpdate(5L))
                .thenReturn(Optional.of(listing));

        when(orders.saveAndFlush(any(OrderEntity.class)))
                .thenAnswer(call ->
                        call.getArgument(0, OrderEntity.class));
    }

    @Test
    void orderReservesStockAndUsesTheListingPrice() {
        OrderResponse response = service.createOrder(
                new CreateOrderRequest(5L, new BigDecimal("10.00")),
                "buyer@example.com"
        );

        assertEquals(
                new BigDecimal("40.00"),
                listing.getAvailableQuantityKg()
        );
        assertEquals(new BigDecimal("24.00"), response.pricePerKg());
        assertEquals(new BigDecimal("240.0000"), response.totalAmount());
        assertEquals(OrderStatus.PENDING, response.status());

        ArgumentCaptor<OrderEntity> captor =
                ArgumentCaptor.forClass(OrderEntity.class);

        verify(orders).saveAndFlush(captor.capture());

        OrderEntity saved = captor.getValue();
        assertSame(listing, saved.getListing());
        assertEquals(Long.valueOf(2L), saved.getBuyer().getId());
        assertEquals(new BigDecimal("10.00"), saved.getQuantityKg());

        // Later listing-price changes must not change this order.
        listing.updateDetails(
                new BigDecimal("40.00"),
                new BigDecimal("30.00"),
                "Meerut"
        );

        assertEquals(new BigDecimal("24.00"), saved.getPricePerKg());
        assertEquals(new BigDecimal("240.0000"), saved.getTotalAmount());
    }

    @Test
    void insufficientStockRejectsOrderWithoutChangingQuantity() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createOrder(
                        new CreateOrderRequest(
                                5L, new BigDecimal("51.00")
                        ),
                        "buyer@example.com"
                )
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(
                new BigDecimal("50.00"),
                listing.getAvailableQuantityKg()
        );
        verify(orders, never()).saveAndFlush(any(OrderEntity.class));
    }

    @Test
    void farmerCannotOrderTheirOwnListing() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createOrder(
                        new CreateOrderRequest(
                                5L, new BigDecimal("10.00")
                        ),
                        "farmer@example.com"
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals(
                new BigDecimal("50.00"),
                listing.getAvailableQuantityKg()
        );
        verify(orders, never()).saveAndFlush(any(OrderEntity.class));
    }

    @Test
    void closedListingCannotReceiveOrders() {
        listing.close();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createOrder(
                        new CreateOrderRequest(
                                5L, new BigDecimal("10.00")
                        ),
                        "buyer@example.com"
                )
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(
                new BigDecimal("50.00"),
                listing.getAvailableQuantityKg()
        );
        verify(orders, never()).saveAndFlush(any(OrderEntity.class));
    }
}