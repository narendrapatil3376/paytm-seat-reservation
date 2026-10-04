package com.paytm.seatreservation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private String tokenType;

    private long expiresIn;
}