package com.prashant.KhetWallah.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        for (var error : exception.getBindingResult().getFieldErrors()) {
            String message = error.getDefaultMessage();

            fieldErrors.putIfAbsent(
                    error.getField(),
                    message != null ? message : "Invalid value"
            );
        }

        ApiError response = new ApiError(
                400,
                "Please correct the highlighted fields",
                fieldErrors
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException exception
    ) {
        String message = exception.getReason();

        ApiError response = new ApiError(
                exception.getStatusCode().value(),
                message != null ? message : "Request failed",
                Map.of()
        );

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        ApiError response = new ApiError(
                400,
                "Invalid value for parameter: " + exception.getName(),
                Map.of()
        );

        return ResponseEntity.badRequest().body(response);
    }
}