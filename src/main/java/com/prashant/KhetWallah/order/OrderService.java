package com.prashant.KhetWallah.order;

import com.prashant.KhetWallah.listing.ListingEntity;
import com.prashant.KhetWallah.listing.ListingRepository;
import com.prashant.KhetWallah.listing.ListingStatus;
import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final AppUserRepository userRepository;
    private final OrderEmailNotifications emails;

    public OrderService(
            OrderRepository orderRepository,
            ListingRepository listingRepository,
            AppUserRepository userRepository,
            OrderEmailNotifications emails
    ) {
        this.orderRepository = orderRepository;
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.emails = emails;
    }

    @Transactional
    public OrderResponse createOrder(
            CreateOrderRequest request,
            String authenticatedEmail
    ) {
        AppUser buyer = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Account no longer exists"
                ));

        ListingEntity listing = listingRepository
                .findByIdForUpdate(request.listingId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Listing not found"
                ));

        if (!listing.isPubliclyAvailable()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This listing is paused, expired or unavailable"
            );
        }

        if (listing.getOwner() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This listing is not available for orders"
            );
        }

        if (listing.getOwner().getId().equals(buyer.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot order your own listing"
            );
        }

        if (request.quantityKg() == null
                || request.quantityKg().signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be positive"
            );
        }

        if (request.quantityKg().compareTo(listing.getMinimumOrderQuantityKg()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Minimum order quantity is " + listing.getMinimumOrderQuantityKg().toPlainString() + " kg");
        }
        if (listing.getAvailableQuantityKg()
                .compareTo(request.quantityKg()) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient available quantity"
            );
        }

        listing.reserveStock(request.quantityKg());

        OrderEntity order = new OrderEntity(
                listing,
                buyer,
                request.quantityKg()
        );

        OrderEntity saved = orderRepository.saveAndFlush(order);
        emails.queue(saved, OrderEmailNotifications.Event.CREATED);

        return OrderResponse.from(saved);
    }
    @Transactional(readOnly = true)
    public OrderPageResponse getMyOrders(
            String authenticatedEmail,
            int page,
            int size
    ) {
        Page<OrderEntity> orders =
                orderRepository.findByBuyer_EmailOrderByIdDesc(
                        authenticatedEmail,
                        PageRequest.of(page, size)
                );

        Page<OrderResponse> responses = orders.map(OrderResponse::from);

        return new OrderPageResponse(
                responses.getContent(),
                responses.getNumber(),
                responses.getSize(),
                responses.getTotalElements(),
                responses.getTotalPages()
        );
    }
    @Transactional(readOnly = true)
    public OrderResponse getOrderForParticipant(Long orderId, String authenticatedEmail) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        boolean buyer = order.getBuyer().getEmail().equals(authenticatedEmail);
        boolean farmer = order.getListing().getOwner() != null
                && order.getListing().getOwner().getEmail().equals(authenticatedEmail);
        if (!buyer && !farmer) {
            // Do not disclose whether another account's order exists.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return OrderResponse.from(order);
    }

    @Transactional
    public void cancelOrder(
            Long orderId,
            String authenticatedEmail
    ) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));

        if (!order.getBuyer().getEmail().equals(authenticatedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only cancel your own orders"
            );
        }

        // Repeated cancellation must not restore stock again.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending orders can be cancelled"
            );
        }

        ListingEntity listing = listingRepository
                .findByIdForUpdate(order.getListing().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Listing not found"
                ));

        listing.restoreStock(order.getQuantityKg());
        order.cancel();
        emails.queue(order, OrderEmailNotifications.Event.CANCELLED);
    }
    @Transactional(readOnly = true)
    public OrderPageResponse getReceivedOrders(
            String authenticatedEmail,
            int page,
            int size
    ) {
        Page<OrderEntity> orders = orderRepository
                .findByListing_Owner_EmailOrderByIdDesc(
                        authenticatedEmail,
                        PageRequest.of(page, size)
                );

        Page<OrderResponse> responses = orders.map(OrderResponse::from);

        return new OrderPageResponse(
                responses.getContent(),
                responses.getNumber(),
                responses.getSize(),
                responses.getTotalElements(),
                responses.getTotalPages()
        );
    }
    private OrderEntity findOrderForFarmer(
            Long orderId,
            String authenticatedEmail
    ) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"
                ));

        AppUser owner = order.getListing().getOwner();

        if (owner == null || !owner.getEmail().equals(authenticatedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only manage orders for your own listings"
            );
        }

        return order;
    }

    @Transactional
    public void acceptOrder(Long orderId, String authenticatedEmail) {
        OrderEntity order = findOrderForFarmer(orderId, authenticatedEmail);

        if (order.getStatus() == OrderStatus.ACCEPTED) {
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending orders can be accepted"
            );
        }

        order.accept();
        emails.queue(order, OrderEmailNotifications.Event.ACCEPTED);
    }

    @Transactional
    public void rejectOrder(Long orderId, String authenticatedEmail) {
        OrderEntity order = findOrderForFarmer(orderId, authenticatedEmail);

        if (order.getStatus() == OrderStatus.REJECTED) {
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending orders can be rejected"
            );
        }

        ListingEntity listing = listingRepository
                .findByIdForUpdate(order.getListing().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Listing not found"
                ));

        listing.restoreStock(order.getQuantityKg());
        order.reject();
        emails.queue(order, OrderEmailNotifications.Event.REJECTED);
    }
    @Transactional
    public void schedulePickup(
            Long orderId,
            SchedulePickupRequest request,
            String authenticatedEmail
    ) {
        OrderEntity order = findOrderForFarmer(orderId, authenticatedEmail);
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only accepted orders can have pickup details"
            );
        }
        boolean changed = !java.util.Objects.equals(order.getPickupAt(), request.pickupAt())
                || !java.util.Objects.equals(order.getPickupInstructions(),
                        request.pickupInstructions() == null ? null : request.pickupInstructions().strip());
        try {
            order.schedulePickup(request.pickupAt(), request.pickupInstructions());
            if (changed) emails.queue(order, OrderEmailNotifications.Event.PICKUP_UPDATED);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @Transactional
    public void completeOrder(Long orderId, String authenticatedEmail) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found"
                ));

        if (!order.getBuyer().getEmail().equals(authenticatedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only the buyer can confirm collection"
            );
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            return;
        }
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only accepted orders can be completed"
            );
        }
        if (order.getPickupAt() == null || order.getPickupInstructions() == null
                || order.getPickupInstructions().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The farmer must set pickup details first"
            );
        }
        order.complete();
        emails.queue(order, OrderEmailNotifications.Event.COMPLETED);
    }
}
