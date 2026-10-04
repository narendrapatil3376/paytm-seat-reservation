package com.paytm.seatreservation.dto;

import java.util.List;
import java.util.UUID;

public record ShowResponse(

        UUID showId,

        String name,

        Long pricePaise,

        Integer perUserLimit,

        Integer totalSeats,

        Integer available,

        Integer held,

        Integer confirmed,

        List<SeatResponse> seats

) {
}