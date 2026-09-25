package com.prashant.KhetWallah.order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import java.security.Principal;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    @GetMapping("/api/me/orders")
    public OrderPageResponse getMyOrders(
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater; size must be between 1 and 50"
            );
        }

        return orderService.getMyOrders(
                principal.getName(),
                page,
                size
        );
    }
    @PostMapping("/api/orders/{id}/accept")
    public ResponseEntity<Void> acceptOrder(
            @PathVariable("id") Long id,
            Principal principal
    ) {
        orderService.acceptOrder(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/orders/{id}/reject")
    public ResponseEntity<Void> rejectOrder(
            @PathVariable("id") Long id,
            Principal principal
    ) {
        orderService.rejectOrder(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/orders/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable("id") Long id,
            Principal principal
    ) {
        orderService.cancelOrder(id, principal.getName());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Principal principal
    ) {
        OrderResponse response = orderService.createOrder(
                request,
                principal.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping("/api/me/received-orders")
    public OrderPageResponse getReceivedOrders(
            Principal principal,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater; size must be between 1 and 50"
            );
        }

        return orderService.getReceivedOrders(
                principal.getName(),
                page,
                size
        );
    }
    @PostMapping("/api/orders/{id}/pickup")
    public ResponseEntity<Void> schedulePickup(
            @PathVariable("id") Long id,
            @Valid @RequestBody SchedulePickupRequest request,
            Principal principal
    ) {
        orderService.schedulePickup(id, request, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/orders/{id}/complete")
    public ResponseEntity<Void> completeOrder(
            @PathVariable("id") Long id,
            Principal principal
    ) {
        orderService.completeOrder(id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}