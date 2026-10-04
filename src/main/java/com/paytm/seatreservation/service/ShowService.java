package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.*;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.entity.ShowSeat;
import com.paytm.seatreservation.enums.SeatStatus;
import com.paytm.seatreservation.exception.ApiException;
import com.paytm.seatreservation.exception.ErrorCode;
import com.paytm.seatreservation.repository.ShowRepository;
import com.paytm.seatreservation.repository.ShowSeatRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ShowService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    @Transactional
    public ShowResponse createShow(
            CreateShowRequest request
    ) {

        List<String> seats =
                request.seats()
                        .stream()
                        .map(String::trim)
                        .distinct()
                        .sorted()
                        .toList();

        if (seats.isEmpty()) {
            throw new ApiException(
                    ErrorCode.INVALID_SEAT,
                    "At least one seat is required"
            );
        }

        Integer limit =
                request.perUserLimit() == null
                        ? 4
                        : request.perUserLimit();

        Show show = Show.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .pricePaise(request.pricePaise())
                .perUserLimit(limit)
                .totalSeats(seats.size())
                .createdAt(Instant.now())
                .build();

        showRepository.save(show);

        List<ShowSeat> showSeats =
                seats.stream()
                        .map(seat ->
                                ShowSeat.builder()
                                        .id(UUID.randomUUID())
                                        .show(show)
                                        .seatNumber(seat)
                                        .status(
                                                SeatStatus.AVAILABLE
                                        )
                                        .createdAt(
                                                Instant.now()
                                        )
                                        .build()
                        )
                        .toList();

        showSeatRepository.saveAll(showSeats);

        return getShow(show.getId());
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(UUID showId) {

        Show show =
                showRepository.findById(showId)
                        .orElseThrow(() ->
                                new ApiException(
                                        ErrorCode.SHOW_NOT_FOUND,
                                        "Show not found"
                                )
                        );

        List<ShowSeat> seats =
                showSeatRepository
                        .findByShowIdOrderBySeatNumber(showId);

        List<SeatResponse> seatResponses =
                seats.stream()
                        .map(s ->
                                new SeatResponse(
                                        s.getSeatNumber(),
                                        s.getStatus().name()
                                )
                        )
                        .toList();

        int available = 0;
        int held = 0;
        int confirmed = 0;

        for (ShowSeat seat : seats) {

            switch (seat.getStatus()) {

                case AVAILABLE -> available++;

                case HELD -> held++;

                case CONFIRMED -> confirmed++;
            }
        }

        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                show.getTotalSeats(),
                available,
                held,
                confirmed,
                seatResponses
        );
    }
}