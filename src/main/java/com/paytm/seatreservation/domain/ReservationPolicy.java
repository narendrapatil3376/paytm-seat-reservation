package com.paytm.seatreservation.domain;

public interface ReservationPolicy {

    void validate(ReservationContext context);
}