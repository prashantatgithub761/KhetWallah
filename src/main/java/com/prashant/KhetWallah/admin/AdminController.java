package com.prashant.KhetWallah.admin;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service = service; }

    @GetMapping("/me")
    public String me() { return "admin"; }

    @GetMapping("/users")
    public Page<AdminService.UserView> users(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.users(page, size);
    }

    @GetMapping("/listings")
    public Page<AdminService.ListingView> listings(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.listings(page, size);
    }

    @GetMapping("/actions")
    public Page<AdminAction> actions(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return service.actions(page, size);
    }

    @PostMapping("/listings/{id}/hide")
    public AdminService.ListingView hide(@PathVariable Long id, @Valid @RequestBody AdminActionRequest request,
                                         Principal principal) {
        return service.hideListing(id, request.reason(), principal.getName());
    }

    @PostMapping("/listings/{id}/restore")
    public AdminService.ListingView restore(@PathVariable Long id, @Valid @RequestBody AdminActionRequest request,
                                            Principal principal) {
        return service.restoreListing(id, request.reason(), principal.getName());
    }

    @PostMapping("/users/{id}/suspend")
    public AdminService.UserView suspend(@PathVariable Long id, @Valid @RequestBody AdminActionRequest request,
                                         Principal principal) {
        return service.suspendUser(id, request.reason(), principal.getName());
    }

    @PostMapping("/users/{id}/reinstate")
    public AdminService.UserView reinstate(@PathVariable Long id, @Valid @RequestBody AdminActionRequest request,
                                           Principal principal) {
        return service.reinstateUser(id, request.reason(), principal.getName());
    }
}
