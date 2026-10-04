package com.paytm.seatreservation.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.paytm.seatreservation.dto.ReserveRequest;
import com.paytm.seatreservation.service.ReservationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReservationController {

	private final ReservationService reservationService;

//    @PostMapping("/shows/{showId}/reserve")
//    public ResponseEntity<ReservationResponse> reserve(
//            @PathVariable UUID showId,
//
//            @RequestHeader("Idempotency-Key")
//            String idempotencyKey,
//
//            @Valid
//            @RequestBody
//            ReserveRequest request,
//
//            Authentication authentication
//    ) {
//
//        String userId =
//                authentication.getName();
//
//        ReservationService.ReservationResult result =
//                reservationService.reserve(
//                        showId,
//                        userId,
//                        request,
//                        idempotencyKey
//                );
//
//
//        if (result.replayed()) {
//
//            return ResponseEntity
//                    .ok(result.response());
//        }
//
//
//        return ResponseEntity
//                .status(201)
//                .body(result.response());
//    }

	@PostMapping("/shows/{showId}/reserve")
	public ResponseEntity<?> reserve(
	        @PathVariable UUID showId,
	        @RequestHeader(
	                "Authorization"
	        ) String authorization,
	        @RequestHeader(
	                value = "Idempotency-Key",
	                required = false
	        ) String idempotencyKey,
	        @RequestBody ReserveRequest request
	) {

	    String userId =
	            SecurityContextHolder
	                    .getContext()
	                    .getAuthentication()
	                    .getName();

	    ReservationService.ReservationResult result =
	            reservationService.reserve(
	                    showId,
	                    userId,
	                    request,
	                    idempotencyKey
	            );

	    return ResponseEntity
	            .status(
	                    result.replayed()
	                            ? HttpStatus.OK
	                            : HttpStatus.CREATED
	            )
	            .body(result.response());
	}

	@PostMapping("/reservations/{reservationId}/cancel")
	public ResponseEntity<Void> cancel(@PathVariable UUID reservationId, Authentication authentication) {

		reservationService.cancel(reservationId, authentication.getName());

		return ResponseEntity.noContent().build();
	}
}