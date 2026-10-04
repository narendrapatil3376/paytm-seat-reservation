package com.paytm.seatreservation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/live")
    public ResponseEntity<?> live() {

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "UP"
                )
        );
    }

    @GetMapping("/ready")
    public ResponseEntity<?> ready() {

        try {

            jdbcTemplate.queryForObject(
                    "SELECT 1",
                    Integer.class
            );

            return ResponseEntity.ok(
                    Map.of(
                            "status",
                            "UP",
                            "database",
                            "UP"
                    )
            );

        } catch (Exception ex) {

            return ResponseEntity
                    .status(503)
                    .body(
                            Map.of(
                                    "status",
                                    "DOWN",
                                    "database",
                                    "DOWN"
                            )
                    );
        }
    }
}