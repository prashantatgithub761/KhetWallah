package com.prashant.KhetWallah.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/me/notifications")
public class OrderNotificationController {
    private final OrderNotificationService service;
    public OrderNotificationController(OrderNotificationService service){this.service=service;}
    @GetMapping public OrderNotificationService.NotificationPage list(Principal principal,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(principal.getName(),page,size);}
    @PostMapping("/{id}/read") public OrderNotificationService.NotificationView read(Principal principal,@PathVariable Long id){return service.read(principal.getName(),id);}
    @PostMapping("/read-all") public ResponseEntity<Void> all(Principal principal){service.readAll(principal.getName());return ResponseEntity.noContent().build();}
}
