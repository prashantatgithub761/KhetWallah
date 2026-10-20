package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.mail.EmailQueue;
import com.prashant.KhetWallah.user.AppUser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderEmailNotificationsTest {
    private final EmailQueue emails = mock(EmailQueue.class);
    private final OrderEmailNotifications notifications = new OrderEmailNotifications(emails, "https://khet.example/");

    private OrderEntity order() {
        AppUser farmer = new AppUser("Farmer", "farmer@example.com", "hash");
        AppUser buyer = new AppUser("Buyer", "buyer@example.com", "hash");
        ListingEntity listing = new ListingEntity("Tomato", farmer,
                new BigDecimal("40"), new BigDecimal("25.50"), "Meerut");
        OrderEntity order = spy(new OrderEntity(listing, buyer, new BigDecimal("2.00")));
        doReturn(4L).when(order).getId();
        return order;
    }

    @Test
    void creationQueuesSeparateEmailsWithTheAgreedPriceAndCorrectLinks() {
        notifications.queue(order(), OrderEmailNotifications.Event.CREATED);
        verify(emails).enqueue(eq("buyer@example.com"), contains("#4"),
                argThat(body -> body.contains("INR 51.00") && body.contains("Tomato")
                        && body.contains("https://khet.example/my-orders.html")
                        && !body.contains("farmer@example.com")));
        verify(emails).enqueue(eq("farmer@example.com"), contains("#4"),
                contains("https://khet.example/received-orders.html"));
        verifyNoMoreInteractions(emails);
    }

    @Test
    void delayedEmailLinksToLivePickupDetailsInsteadOfRepeatingOldInstructions() {
        OrderEntity order = order();
        order.accept();
        order.schedulePickup(Instant.parse("2099-01-01T10:00:00Z"), "Village gate");
        notifications.queue(order, OrderEmailNotifications.Event.PICKUP_UPDATED);
        verify(emails).enqueue(eq("buyer@example.com"), contains("pickup updated"),
                argThat(body -> body.contains("Update recorded:") && body.contains("latest time and meeting instructions") && !body.contains("Village gate")));
    }

    @Test
    void blankBaseUrlUsesTheLocalDefault() {
        assertDoesNotThrow(() -> new OrderEmailNotifications(emails, ""));
    }

    @Test
    void baseUrlRejectsCredentialsAndUntrustedSchemes() {
        assertThrows(IllegalArgumentException.class, () -> new OrderEmailNotifications(emails, "javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> new OrderEmailNotifications(emails, "https://user:pass@example.com"));
    }
}
