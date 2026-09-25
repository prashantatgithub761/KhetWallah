package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.listing.ListingRepository;
import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class OrderPersistenceIntegrationTest {

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Test
    void creatingOrderPersistsItAndReducesStock() {
        AppUser farmer = userRepository.saveAndFlush(
                new AppUser(
                        "Integration Farmer",
                        "integration-farmer@example.com",
                        "unused-test-hash"
                )
        );

        userRepository.saveAndFlush(
                new AppUser(
                        "Integration Buyer",
                        "integration-buyer@example.com",
                        "unused-test-hash"
                )
        );

        ListingEntity listing = listingRepository.saveAndFlush(
                new ListingEntity(
                        "Potato",
                        farmer,
                        new BigDecimal("50.00"),
                        new BigDecimal("24.00"),
                        "Meerut"
                )
        );

        OrderResponse response = orderService.createOrder(
                new CreateOrderRequest(
                        listing.getId(),
                        new BigDecimal("10.00")
                ),
                "integration-buyer@example.com"
        );

        assertNotNull(response.id());
        assertEquals(OrderStatus.PENDING, response.status());
        assertEquals(0, response.pricePerKg()
                .compareTo(new BigDecimal("24.00")));
        assertEquals(0, response.totalAmount()
                .compareTo(new BigDecimal("240.0000")));

        OrderEntity storedOrder = orderRepository
                .findById(response.id())
                .orElseThrow();

        ListingEntity storedListing = listingRepository
                .findById(listing.getId())
                .orElseThrow();

        assertEquals(
                listing.getId(),
                storedOrder.getListing().getId()
        );

        assertEquals(
                "integration-buyer@example.com",
                storedOrder.getBuyer().getEmail()
        );

        assertEquals(
                0,
                storedListing.getAvailableQuantityKg()
                        .compareTo(new BigDecimal("40.00"))
        );

        java.time.Instant pickupAt = java.time.Instant.now()
                .plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        orderService.acceptOrder(response.id(), "integration-farmer@example.com");
        orderService.schedulePickup(response.id(), new SchedulePickupRequest(
                pickupAt, "Village market entrance"
        ), "integration-farmer@example.com");
        entityManager.flush();
        entityManager.clear();

        OrderEntity reloaded = orderRepository.findById(response.id()).orElseThrow();
        assertEquals(pickupAt, reloaded.getPickupAt());
        assertEquals("Village market entrance", reloaded.getPickupInstructions());

        orderService.completeOrder(response.id(), "integration-buyer@example.com");
        entityManager.flush();
        entityManager.clear();
        assertEquals(OrderStatus.COMPLETED,
                orderRepository.findById(response.id()).orElseThrow().getStatus());
    }
}