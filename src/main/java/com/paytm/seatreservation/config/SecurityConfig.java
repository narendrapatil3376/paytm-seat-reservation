package com.paytm.seatreservation.config;

import com.paytm.seatreservation.security.BearerTokenAuthenticationFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final BearerTokenAuthenticationFilter
            bearerTokenAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Login doesn't require JWT
                        .requestMatchers(
                                "/auth/login"
                        ).permitAll()

                        // Health endpoints
                        .requestMatchers(
                                "/health/live",
                                "/health/ready",
                                "/actuator/**"
                        ).permitAll()

                        // Anyone can see show availability
                        .requestMatchers(
                                HttpMethod.GET,
                                "/shows/**"
                        ).permitAll()

                        // Only ADMIN can create shows
                        .requestMatchers(
                                HttpMethod.POST,
                                "/shows"
                        ).hasRole("ADMIN")

                        // Everything else requires authentication
                        .anyRequest()
                        .authenticated()
                )

                .addFilterBefore(
                        bearerTokenAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}