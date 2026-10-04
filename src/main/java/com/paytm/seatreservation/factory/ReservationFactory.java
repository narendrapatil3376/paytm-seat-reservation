package com.paytm.seatreservation.factory;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.enums.ReservationStatus;

@Component
public class ReservationFactory {

    public Reservation create(
            Show show,
            String userId,
            int seatCount
    ) {

        return Reservation.builder()
                .id(UUID.randomUUID())
                .show(show)
                .userId(userId)
                .amountPaise(
                        show.getPricePaise()
                                * (long) seatCount
                )
                .status(ReservationStatus.CONFIRMED)
                .createdAt(Instant.now())
                .build();
    }
}