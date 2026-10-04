package com.paytm.seatreservation.domain;

import org.springframework.stereotype.Component;

import com.paytm.seatreservation.entity.ShowSeat;
import com.paytm.seatreservation.enums.SeatStatus;
import com.paytm.seatreservation.exception.ApiException;
import com.paytm.seatreservation.exception.ErrorCode;

@Component
public class SeatAvailabilityPolicy
        implements ReservationPolicy {

    @Override
    public void validate(
            ReservationContext context
    ) {

        for (ShowSeat seat : context.lockedSeats()) {

            if (seat.getStatus() != SeatStatus.AVAILABLE) {

                throw new ApiException(
                        ErrorCode.SEAT_TAKEN,
                        "Seat already taken: "
                                + seat.getSeatNumber()
                );
            }
        }
    }
}