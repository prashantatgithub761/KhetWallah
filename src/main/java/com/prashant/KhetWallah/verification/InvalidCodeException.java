package com.prashant.KhetWallah.verification;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Only a failed code comparison uses this exception, so its attempt can commit. */
public class InvalidCodeException extends ResponseStatusException {
    public InvalidCodeException() {
        super(HttpStatus.BAD_REQUEST, "The verification code is invalid or has expired");
    }
}
