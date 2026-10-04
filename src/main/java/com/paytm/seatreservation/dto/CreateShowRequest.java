package com.paytm.seatreservation.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public record CreateShowRequest(

        @NotBlank
        String name,

        @NotEmpty
        List<@NotBlank String> seats,

        @NotNull
        @PositiveOrZero
        Long pricePaise,

        @Positive
        Integer perUserLimit

) {
}