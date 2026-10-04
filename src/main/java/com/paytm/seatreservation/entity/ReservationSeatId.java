package com.paytm.seatreservation.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReservationSeatId implements Serializable {

    private UUID reservationId;

    private UUID showSeatId;
}