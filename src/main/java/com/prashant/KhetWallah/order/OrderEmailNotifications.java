package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.mail.EmailQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Queue immutable order summaries within the transaction that changed the order. */
@Service
public class OrderEmailNotifications {
    public enum Event { CREATED, ACCEPTED, REJECTED, CANCELLED, PICKUP_UPDATED, COMPLETED }

    private static final DateTimeFormatter PICKUP_TIME = DateTimeFormatter
            .ofPattern("dd MMM yyyy, h:mm a 'IST'", java.util.Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Kolkata"));
    private final EmailQueue emails;
    private final String baseUrl;
    @org.springframework.beans.factory.annotation.Autowired
    private com.prashant.KhetWallah.notification.OrderNotificationService inApp;

    public OrderEmailNotifications(EmailQueue emails,
            @Value("${app.email.public-base-url:http://localhost:8080}") String baseUrl) {
        this.emails = emails;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080" : baseUrl.strip();
        URI uri = URI.create(baseUrl);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("APP_PUBLIC_URL must be an http(s) site origin");
        }
        this.baseUrl = baseUrl.strip().replaceAll("/+$", "");
    }

    public void queue(OrderEntity order, Event event) {
        if (inApp != null) inApp.queue(order, event.name());
        String description = switch (event) {
            case CREATED -> "An order request has been placed and is awaiting the farmer's decision.";
            case ACCEPTED -> "The farmer accepted this order. Pickup details will appear in your order page.";
            case REJECTED -> "The farmer rejected this order. Its reserved stock has been released.";
            case CANCELLED -> "The buyer cancelled this pending order. Its reserved stock has been released.";
            case PICKUP_UPDATED -> "The farmer updated the pickup details. Check the latest details before travelling.";
            case COMPLETED -> "Collection has been confirmed. This order is now complete.";
        };
        String subject = "KhetWallah order #" + order.getId() + " — " + switch (event) {
            case CREATED -> "request placed";
            case ACCEPTED -> "accepted";
            case REJECTED -> "rejected";
            case CANCELLED -> "cancelled";
            case PICKUP_UPDATED -> "pickup updated";
            case COMPLETED -> "collection confirmed";
        };
        String summary = description + "\n\n"
                + "Update recorded: " + PICKUP_TIME.format(java.time.Instant.now()) + "\n"
                + "Order: #" + order.getId() + "\n"
                + "Crop: " + order.getListing().getProduceName() + "\n"
                + "Quantity: " + order.getQuantityKg().toPlainString() + " kg\n"
                + "Agreed price per kg: INR " + order.getPricePerKg().toPlainString() + "\n"
                + "Total: INR " + order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString() + "\n"
                + "Status: " + order.getStatus().name() + "\n";
        if (order.getPickupAt() != null) {
            // Retried notifications can arrive out of order: show live meeting details only after sign-in.
            summary += "Pickup details are available on your order page. Open it for the latest time and meeting instructions.\n";
        }
        // Separate messages keep each party's address private.
        emails.enqueue(order.getBuyer().getEmail(), subject,
                summary + "\nView your purchases: " + baseUrl + "/my-orders.html\n\n"
                        + "This is an order update, not a payment receipt. Check the order page for its latest status.");
        if (order.getListing().getOwner() != null) {
            emails.enqueue(order.getListing().getOwner().getEmail(), subject,
                    summary + "\nManage received orders: " + baseUrl + "/received-orders.html\n\n"
                            + "This is an order update, not a payment receipt. Check the order page for its latest status.");
        }
    }
}
