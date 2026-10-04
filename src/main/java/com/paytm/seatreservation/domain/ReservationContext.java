package com.paytm.seatreservation.domain;

import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.entity.ShowSeat;

import java.util.List;

public record ReservationContext(
        Show show,
        String userId,
        List<String> requestedSeats,
        List<ShowSeat> lockedSeats,
        long existingSeatCount
) {
}