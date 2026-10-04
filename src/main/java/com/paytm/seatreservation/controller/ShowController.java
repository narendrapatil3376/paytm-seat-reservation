package com.paytm.seatreservation.controller;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.ShowResponse;
import com.paytm.seatreservation.service.ShowService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(
            ShowService showService
    ) {
        this.showService = showService;
    }

    /**
     * Create a new show.
     *
     * Only an authenticated ADMIN should be allowed.
     *
     * Example:
     * Authorization: Bearer admin-token
     */
    @PostMapping
    public ResponseEntity<ShowResponse> createShow(
            @Valid @RequestBody CreateShowRequest request
    ) {

        ShowResponse response =
                showService.createShow(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Get show details including seat-level status.
     *
     * Example:
     * GET /shows/{showId}
     */
    @GetMapping("/{showId}")
    public ResponseEntity<ShowResponse> getShow(
            @PathVariable UUID showId
    ) {

        ShowResponse response =
                showService.getShow(showId);

        return ResponseEntity.ok(response);
    }
}