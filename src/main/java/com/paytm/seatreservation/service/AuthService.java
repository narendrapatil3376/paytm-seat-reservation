package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.LoginRequest;
import com.paytm.seatreservation.dto.LoginResponse;
import com.paytm.seatreservation.security.JwtProperties;
import com.paytm.seatreservation.security.JwtService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.BadCredentialsException;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;

    private final JwtProperties jwtProperties;

    public LoginResponse login(LoginRequest request) {

        String username =
                request.getUsername();

        String password =
                request.getPassword();

        String role;

        /*
         * Demo users for the assignment.
         *
         * Production application should use
         * database-backed users and BCrypt passwords.
         */

        if ("admin".equals(username)
                && "admin123".equals(password)) {

            role = "ADMIN";

        } else if ("user1".equals(username)
                && "user123".equals(password)) {

            role = "USER";

        } else if ("user2".equals(username)
                && "user123".equals(password)) {

            role = "USER";

        } else {

            throw new BadCredentialsException(
                    "Invalid username or password"
            );
        }

        String token =
                jwtService.generateToken(
                        username,
                        role
                );

        return new LoginResponse(
                token,
                "Bearer",
                jwtProperties.getExpirationMs() / 1000
        );
    }
}