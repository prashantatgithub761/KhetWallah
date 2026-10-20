package com.prashant.KhetWallah.dashboard;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/me/dashboard")
    public ResponseEntity<DashboardResponse> account(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to see your account");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(dashboard.forAccount(principal.getName()));
    }
}
