package com.paytm.seatreservation.dto;

import java.util.List;
import java.util.UUID;

import com.paytm.seatreservation.enums.ReservationStatus;

public record ReservationResponse(
        UUID reservationId,
        UUID showId,
        String userId,
        List<String> seats,
        Long amountPaise,
        ReservationStatus status
) {
}