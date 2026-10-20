package com.prashant.KhetWallah.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Safe generic response; the service commits a failed-attempt count for this error. */
public class InvalidRecoveryCodeException extends ResponseStatusException {
    public InvalidRecoveryCodeException() {
        super(HttpStatus.BAD_REQUEST,
                "This recovery code is invalid, expired, or unavailable. Request a new code and try again.");
    }
}
