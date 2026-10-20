package com.prashant.KhetWallah.order;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public class InvalidPickupCodeException extends ResponseStatusException {
    public InvalidPickupCodeException(){super(HttpStatus.BAD_REQUEST,"The pickup code is invalid, expired, or locked. Ask the buyer to check the latest code.");}
}
