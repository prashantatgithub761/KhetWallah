package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.config.PasswordConfig;
import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.user.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickupCodeServiceTest {
    private static final String BUYER = "buyer@example.test";
    private static final String FARMER = "farmer@example.test";
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PickupCodeRepository codes = mock(PickupCodeRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final OrderEmailNotifications updates = mock(OrderEmailNotifications.class);
    private final SecureRandom random = mock(SecureRandom.class);
    private final MutableClock clock = new MutableClock(Instant.now());
    private final Instant pickup = Instant.now().plusSeconds(86400);
    private PickupCode stored;
    private ListingEntity listing;
    private OrderEntity order;
    private PickupCodeService service;

    @BeforeEach
    void prepare() {
        listing = new ListingEntity("Tomato", new AppUser("Farmer", FARMER, "unused"),
                new BigDecimal("30.00"), new BigDecimal("20.00"), "Jani");
        order = new OrderEntity(listing, new AppUser("Buyer", BUYER, "unused"), new BigDecimal("2.00"));
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(codes.findById(1L)).thenAnswer(invocation -> Optional.ofNullable(stored));
        when(codes.saveAndFlush(any())).thenAnswer(invocation -> {
            stored = invocation.getArgument(0); return stored;
        });
        when(random.nextInt(1_000_000)).thenReturn(123456);
        when(encoder.encode(anyString())).thenReturn("opaque-code-hash");
        when(encoder.matches(anyString(), anyString())).thenAnswer(invocation ->
                "123456".equals(invocation.getArgument(0))
                        && "opaque-code-hash".equals(invocation.getArgument(1)));
        service = new PickupCodeService(orders, codes, encoder, updates, clock, random);
    }

    private void ready() {
        order.accept();
        order.schedulePickup(pickup, "Village market gate");
    }

    private ResponseStatusException status(HttpStatus expected, org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action);
        assertEquals(expected, error.getStatusCode());
        return error;
    }

    @Test
    void onlyTheBuyerCanGenerateTheCode() {
        ready();
        for (String caller : List.of(FARMER, "other-buyer@example.test")) {
            status(HttpStatus.FORBIDDEN, () -> service.issue(1L, caller));
        }
        verifyNoInteractions(codes, encoder, updates);
    }

    @Test
    void onlyTheListingFarmerCanConfirmEvenAfterCompletion() {
        ready();
        service.issue(1L, BUYER);
        for (String caller : List.of(BUYER, "other-farmer@example.test")) {
            status(HttpStatus.FORBIDDEN, () -> service.confirm(1L, caller, "123456"));
        }
        service.confirm(1L, FARMER, "123456");
        for (String caller : List.of(BUYER, "other-farmer@example.test")) {
            status(HttpStatus.FORBIDDEN, () -> service.confirm(1L, caller, "123456"));
        }
        verify(updates, times(1)).queue(order, OrderEmailNotifications.Event.COMPLETED);
    }

    @Test
    void pendingAndAcceptedWithoutPickupOrdersCannotIssueOrConfirm() {
        status(HttpStatus.CONFLICT, () -> service.issue(1L, BUYER));
        status(HttpStatus.CONFLICT, () -> service.confirm(1L, FARMER, "123456"));
        order.accept();
        status(HttpStatus.CONFLICT, () -> service.issue(1L, BUYER));
        status(HttpStatus.CONFLICT, () -> service.confirm(1L, FARMER, "123456"));
        verifyNoInteractions(codes, updates);
    }

    @Test
    void issuedCodeIsHashedAndBoundToTheCurrentPickupRevision() {
        ready();
        PasswordEncoder actual = new PasswordConfig().passwordEncoder();
        service = new PickupCodeService(orders, codes, actual, updates, clock, random);
        var challenge = service.issue(1L, BUYER);
        assertEquals("123456", challenge.code());
        assertTrue(actual.matches(challenge.code(), stored.getCodeHash()));
        assertNotEquals(challenge.code(), stored.getCodeHash());
        assertEquals(clock.instant().plusSeconds(900), challenge.expiresAt());
        assertEquals(60, challenge.resendAfterSeconds());
        assertEquals(order.getPickupRevision(), stored.getPickupRevision());
        assertFalse(stored.toString().contains(challenge.code()));
        assertFalse(challenge.toString().contains(challenge.code()));
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        verifyNoInteractions(updates);
    }

    @Test
    void leadingZerosAreKeptInTheCode() {
        ready();
        when(random.nextInt(1_000_000)).thenReturn(7);
        assertEquals("000007", service.issue(1L, BUYER).code());
        verify(encoder).encode("000007");
    }

    @Test
    void confirmationIsSingleUseAndRepeatedCompletionIsIdempotent() {
        ready();
        service.issue(1L, BUYER);
        service.confirm(1L, FARMER, "123456");
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(clock.instant(), stored.getConsumedAt());
        assertNull(stored.getCodeHash());
        assertEquals(new BigDecimal("30.00"), listing.getAvailableQuantityKg());
        service.confirm(1L, FARMER, "does-not-matter-after-success");
        verify(updates, times(1)).queue(order, OrderEmailNotifications.Event.COMPLETED);
        verify(encoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    void wrongAndMalformedCodesConsumeTheSameFiveAttemptBudget() {
        ready();
        service.issue(1L, BUYER);
        for (String wrong : List.of("654321", "x", "12345", "1234567", "000000")) {
            assertThrows(InvalidPickupCodeException.class, () -> service.confirm(1L, FARMER, wrong));
        }
        assertEquals(5, stored.getFailedAttempts());
        status(HttpStatus.TOO_MANY_REQUESTS, () -> service.confirm(1L, FARMER, "123456"));
        clock.advance(Duration.ofSeconds(60));
        status(HttpStatus.TOO_MANY_REQUESTS, () -> service.issue(1L, BUYER));
        assertEquals(1, stored.getSendCount());
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        verifyNoInteractions(updates);
    }

    @Test
    void resendingInvalidatesOldCodeAndKeepsFailedAttempts() {
        ready();
        service.issue(1L, BUYER);
        assertThrows(InvalidPickupCodeException.class, () -> service.confirm(1L, FARMER, "654321"));
        clock.advance(Duration.ofSeconds(60));
        when(random.nextInt(1_000_000)).thenReturn(654321);
        when(encoder.encode("654321")).thenReturn("second-code-hash");
        when(encoder.matches("123456", "second-code-hash")).thenReturn(false);
        when(encoder.matches("654321", "second-code-hash")).thenReturn(true);
        service.issue(1L, BUYER);
        assertEquals(1, stored.getFailedAttempts());
        assertThrows(InvalidPickupCodeException.class, () -> service.confirm(1L, FARMER, "123456"));
        service.confirm(1L, FARMER, "654321");
        assertEquals(2, stored.getFailedAttempts());
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    void expiredCodeAtTheExactBoundaryCannotCompleteTheOrder() {
        ready();
        service.issue(1L, BUYER);
        clock.advance(Duration.ofMinutes(15));
        assertThrows(InvalidPickupCodeException.class, () -> service.confirm(1L, FARMER, "123456"));
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        assertNull(stored.getConsumedAt());
        assertEquals(0, stored.getFailedAttempts());
        verifyNoInteractions(updates);
    }

    @Test
    void changedPickupInvalidatesTheOldCodeEvenWhenItHasNotExpired() {
        ready();
        service.issue(1L, BUYER);
        long originalRevision = order.getPickupRevision();
        order.schedulePickup(pickup.plusSeconds(3600), "East gate");
        assertEquals(originalRevision + 1, order.getPickupRevision());
        assertThrows(InvalidPickupCodeException.class, () -> service.confirm(1L, FARMER, "123456"));
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
        clock.advance(Duration.ofSeconds(60));
        service.issue(1L, BUYER);
        assertEquals(order.getPickupRevision(), stored.getPickupRevision());
        service.confirm(1L, FARMER, "123456");
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    void identicalPickupDetailsDoNotInvalidateAnExistingCode() {
        ready();
        service.issue(1L, BUYER);
        long revision = order.getPickupRevision();
        order.schedulePickup(pickup, "  Village market gate  ");
        assertEquals(revision, order.getPickupRevision());
        service.confirm(1L, FARMER, "123456");
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    void sendCooldownAndHourlyBudgetAreEnforcedUnderTheOrderLock() {
        ready();
        service.issue(1L, BUYER);
        status(HttpStatus.TOO_MANY_REQUESTS, () -> service.issue(1L, BUYER));
        for (int send = 1; send < 5; send++) {
            clock.advance(Duration.ofSeconds(60));
            service.issue(1L, BUYER);
        }
        clock.advance(Duration.ofSeconds(60));
        status(HttpStatus.TOO_MANY_REQUESTS, () -> service.issue(1L, BUYER));
        assertEquals(5, stored.getSendCount());
        clock.advance(Duration.ofHours(1));
        service.issue(1L, BUYER);
        assertEquals(1, stored.getSendCount());
    }

    @Test
    void missingOrderOrMissingCodeCannotBeConfirmed() {
        status(HttpStatus.NOT_FOUND, () -> service.issue(999L, BUYER));
        status(HttpStatus.NOT_FOUND, () -> service.confirm(999L, FARMER, "123456"));
        ready();
        assertThrows(InvalidPickupCodeException.class, () -> service.confirm(1L, FARMER, "123456"));
        verifyNoInteractions(updates);
    }

    @Test
    void wrongCodeAttemptsCommitWhileOtherFailuresRollback() throws Exception {
        var transaction = new AnnotationTransactionAttributeSource().getTransactionAttribute(
                PickupCodeService.class.getMethod("confirm", Long.class, String.class, String.class),
                PickupCodeService.class);
        assertNotNull(transaction);
        assertFalse(transaction.rollbackOn(new InvalidPickupCodeException()));
        assertTrue(transaction.rollbackOn(new IllegalStateException("database failure")));
        assertTrue(transaction.rollbackOn(new ResponseStatusException(HttpStatus.FORBIDDEN)));
    }

    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
