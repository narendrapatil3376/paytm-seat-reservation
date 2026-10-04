package com.paytm.seatreservation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "reservation_seats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationSeat {

    @EmbeddedId
    private ReservationSeatId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("reservationId")
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("showSeatId")
    @JoinColumn(name = "show_seat_id")
    private ShowSeat showSeat;
}