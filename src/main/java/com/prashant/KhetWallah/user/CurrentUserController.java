package com.prashant.KhetWallah.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
public class CurrentUserController {

    private final AppUserRepository repository;

    public CurrentUserController(AppUserRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/auth/me")
    public UserResponse currentUser(Principal principal) {
        AppUser user = repository.findByEmail(principal.getName())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Account no longer exists"
                        )
                );

        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail()
        );
    }
}