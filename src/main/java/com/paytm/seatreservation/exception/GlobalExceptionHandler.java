package com.paytm.seatreservation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException ex
    ) {

        HttpStatus status =
                switch (ex.getErrorCode()) {

                    case SHOW_NOT_FOUND,
                         RESERVATION_NOT_FOUND ->
                            HttpStatus.NOT_FOUND;

                    case VALIDATION_ERROR ->
                            HttpStatus.BAD_REQUEST;

                    case CONCURRENCY_CONFLICT,
                         SEAT_TAKEN,
                         INVALID_SEAT,
                         PER_USER_LIMIT,
                         IDEMPOTENCY_CONFLICT,
                         NOT_RESERVATION_OWNER,
                         RESERVATION_ALREADY_CANCELLED ->
                            HttpStatus.CONFLICT;
                };

        return ResponseEntity
                .status(status)
                .body(
                        new ErrorResponse(
                                ex.getErrorCode().name(),
                                Instant.now(),
                                ex.getMessage()
                        )
                );
    }

    public record ErrorResponse(
            String code,
            Instant timestamp,
            String message
    ) {
    }
}