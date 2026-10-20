package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.CreateListingRequest;
import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.listing.ListingRepository;
import com.prashant.KhetWallah.listing.ListingService;
import com.prashant.KhetWallah.mail.EmailSender;
import com.prashant.KhetWallah.notification.OrderNotificationService;
import com.prashant.KhetWallah.review.CreateReviewRequest;
import com.prashant.KhetWallah.review.OrderReviewService;
import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import com.prashant.KhetWallah.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Uses the explicitly configured database, validates its schema and rolls back every synthetic row. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "app.email.enabled=false"})
@Transactional
class MarketplaceFeaturesIntegrationTest {
    @Autowired EntityManager entityManager;
    @Autowired AppUserRepository users;
    @Autowired ListingRepository listings;
    @Autowired ListingService listingService;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired PickupCodeService pickupCodes;
    @Autowired PickupCodeRepository storedCodes;
    @Autowired OrderReviewService reviews;
    @Autowired OrderNotificationService notifications;
    @MockitoBean EmailSender sender;

    private String marker;
    private AppUser farmer;
    private AppUser buyer;
    private AppUser outsider;
    private AppUser admin;
    private Long listingId;

    @BeforeEach
    void createOnlyUniqueSyntheticAccountsAndListing() {
        when(sender.isConfigured()).thenReturn(false);
        marker = "marketplace-" + UUID.randomUUID();
        farmer = account("Farmer", "farmer");
        buyer = account("Buyer", "buyer");
        outsider = account("Outsider", "outsider");
        admin = account("Administrator", "admin");
        // Fixture elevation applies only to this test's new account and is rolled back.
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        users.flush();
        listingId = listingService.createListing(new CreateListingRequest(
                marker, amount("40.00"), amount("20.00"), "Synthetic test pickup area",
                "Pusa Red", LocalDate.now().minusDays(2), amount("5.00"), "Bring reusable bags"
        ), farmer.getEmail()).getId();
        entityManager.flush();
    }

    @Test
    void persistedPickupCodeCompletionAndReviewModerationFollowTheActualOrderLifecycle() {
        Long orderId = requestedOrder();
        Instant pickupAt = futurePickup();
        orderService.acceptOrder(orderId, farmer.getEmail());
        orderService.schedulePickup(orderId,
                new SchedulePickupRequest(pickupAt, "Synthetic test gate"), farmer.getEmail());
        entityManager.flush();
        entityManager.clear();

        OrderEntity accepted = orders.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.ACCEPTED, accepted.getStatus());
        assertEquals(pickupAt, accepted.getPickupAt());
        assertEquals(1L, accepted.getPickupRevision());
        assertFalse(reviews.eligibility(orderId, buyer.getEmail()).eligible());
        PickupCodeService.Challenge challenge = pickupCodes.issue(orderId, buyer.getEmail());
        assertTrue(challenge.code().matches("[0-9]{6}"));
        assertNotEquals(challenge.code(), storedCodes.findById(orderId).orElseThrow().getCodeHash());
        entityManager.flush();
        entityManager.clear();

        pickupCodes.confirm(orderId, farmer.getEmail(), challenge.code());
        entityManager.flush();
        entityManager.clear();
        assertEquals(OrderStatus.COMPLETED, orders.findById(orderId).orElseThrow().getStatus());
        PickupCode consumed = storedCodes.findById(orderId).orElseThrow();
        assertNotNull(consumed.getConsumedAt());
        assertNull(consumed.getCodeHash());
        assertTrue(reviews.eligibility(orderId, buyer.getEmail()).eligible());
        assertEquals(0, listings.findById(listingId).orElseThrow().getAvailableQuantityKg().compareTo(amount("35.00")));
        assertEquals("Pusa Red", listingService.getListingById(listingId).orElseThrow().getVariety());

        OrderReviewService.ReviewView review = reviews.create(orderId,
                new CreateReviewRequest(4, "Synthetic integration feedback: pickup was clear."), buyer.getEmail());
        entityManager.flush();
        entityManager.clear();
        OrderReviewService.ReviewPage publicPage = reviews.publicReviews(listingId, 0, 6);
        assertEquals(1L, publicPage.reviewCount());
        assertEquals(4.0, publicPage.averageRating().doubleValue());
        assertEquals(review.id(), publicPage.content().getFirst().id());
        assertFalse(reviews.eligibility(orderId, buyer.getEmail()).eligible());
        assertStatus(HttpStatus.CONFLICT, () -> reviews.create(orderId,
                new CreateReviewRequest(5, "A duplicate review must not be stored."), buyer.getEmail()));
        assertStatus(HttpStatus.NOT_FOUND, () -> reviews.create(orderId,
                new CreateReviewRequest(5, "An outsider must not review this order."), outsider.getEmail()));

        assertTrue(reviews.moderate(review.id(), true, "Synthetic moderation check", admin.getEmail()).hidden());
        entityManager.flush();
        entityManager.clear();
        OrderReviewService.ReviewPage moderated = reviews.publicReviews(listingId, 0, 6);
        assertEquals(0L, moderated.reviewCount());
        assertNull(moderated.averageRating());
        assertTrue(moderated.content().isEmpty());
        // Retrying a completed handover must not create another completed notification.
        pickupCodes.confirm(orderId, farmer.getEmail(), challenge.code());
        assertEquals(4L, notifications.list(buyer.getEmail(), 0, 20).totalElements());
        assertEquals(4L, notifications.list(farmer.getEmail(), 0, 20).totalElements());
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void notificationsRemainPrivateAndRepeatedOrChangedPickupUpdatesHaveCorrectDeduplication() {
        Long orderId = requestedOrder();
        orderService.acceptOrder(orderId, farmer.getEmail());
        orderService.acceptOrder(orderId, farmer.getEmail());
        Instant pickupAt = futurePickup();
        SchedulePickupRequest first = new SchedulePickupRequest(pickupAt, "Synthetic first gate");
        orderService.schedulePickup(orderId, first, farmer.getEmail());
        orderService.schedulePickup(orderId, first, farmer.getEmail());
        SchedulePickupRequest changed = new SchedulePickupRequest(pickupAt.plusSeconds(3600), "Synthetic second gate");
        orderService.schedulePickup(orderId, changed, farmer.getEmail());
        orderService.schedulePickup(orderId, changed, farmer.getEmail());
        entityManager.flush();
        entityManager.clear();

        OrderNotificationService.NotificationPage buyerPage = notifications.list(buyer.getEmail(), 0, 20);
        OrderNotificationService.NotificationPage farmerPage = notifications.list(farmer.getEmail(), 0, 20);
        assertEquals(4L, buyerPage.totalElements());
        assertEquals(4L, farmerPage.totalElements());
        assertEquals(4L, buyerPage.unreadCount());
        assertEquals(2L, buyerPage.content().stream().filter(item -> item.eventType().equals("PICKUP_UPDATED")).count());
        assertTrue(buyerPage.content().stream().allMatch(item -> item.orderId().equals(orderId)
                && item.orderUrl().startsWith("/my-orders.html")));
        assertTrue(farmerPage.content().stream().allMatch(item -> item.orderId().equals(orderId)
                && item.orderUrl().startsWith("/received-orders.html")));
        assertEquals(0L, notifications.list(outsider.getEmail(), 0, 20).totalElements());
        Long buyerNotificationId = buyerPage.content().getFirst().id();
        assertStatus(HttpStatus.NOT_FOUND, () -> notifications.read(outsider.getEmail(), buyerNotificationId));
        assertStatus(HttpStatus.NOT_FOUND, () -> notifications.read(farmer.getEmail(), buyerNotificationId));
        assertNotNull(notifications.read(buyer.getEmail(), buyerNotificationId).readAt());
        assertEquals(3L, notifications.list(buyer.getEmail(), 0, 20).unreadCount());
        notifications.readAll(buyer.getEmail());
        entityManager.flush();
        entityManager.clear();
        assertEquals(0L, notifications.list(buyer.getEmail(), 0, 20).unreadCount());
        assertEquals(4L, notifications.list(farmer.getEmail(), 0, 20).unreadCount());
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void orderMinimumAndPickupRevisionCannotBeBypassedByAnotherAccountOrAnOldCode() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> orderService.createOrder(
                new CreateOrderRequest(listingId, amount("4.99")), buyer.getEmail()));
        assertEquals(0, listings.findById(listingId).orElseThrow().getAvailableQuantityKg().compareTo(amount("40.00")));
        Long orderId = requestedOrder();
        orderService.acceptOrder(orderId, farmer.getEmail());
        orderService.schedulePickup(orderId,
                new SchedulePickupRequest(futurePickup(), "Synthetic original gate"), farmer.getEmail());
        assertStatus(HttpStatus.FORBIDDEN, () -> pickupCodes.issue(orderId, farmer.getEmail()));
        PickupCodeService.Challenge challenge = pickupCodes.issue(orderId, buyer.getEmail());
        assertStatus(HttpStatus.FORBIDDEN, () -> pickupCodes.confirm(orderId, outsider.getEmail(), challenge.code()));
        orderService.schedulePickup(orderId,
                new SchedulePickupRequest(futurePickup().plusSeconds(7200), "Synthetic replacement gate"), farmer.getEmail());
        entityManager.flush();
        entityManager.clear();
        assertThrows(InvalidPickupCodeException.class,
                () -> pickupCodes.confirm(orderId, farmer.getEmail(), challenge.code()));
        assertEquals(OrderStatus.ACCEPTED, orders.findById(orderId).orElseThrow().getStatus());
        assertNull(storedCodes.findById(orderId).orElseThrow().getConsumedAt());
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    private AppUser account(String displayName, String suffix) {
        return users.saveAndFlush(new AppUser("Integration " + displayName,
                marker + "-" + suffix + "@example.test", "unused-test-hash"));
    }
    private Long requestedOrder() {
        return orderService.createOrder(new CreateOrderRequest(listingId, amount("5.00")), buyer.getEmail()).id();
    }
    private static BigDecimal amount(String value) { return new BigDecimal(value); }
    private static Instant futurePickup() { return Instant.now().plusSeconds(86400).truncatedTo(ChronoUnit.SECONDS); }
    private static void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, operation).getStatusCode());
    }
}
