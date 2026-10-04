package com.paytm.seatreservation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paytm.seatreservation.domain.ReservationContext;
import com.paytm.seatreservation.domain.ReservationPolicy;
import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveRequest;
import com.paytm.seatreservation.entity.IdempotencyKey;
import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.ReservationSeat;
import com.paytm.seatreservation.entity.ReservationSeatId;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.entity.ShowSeat;
import com.paytm.seatreservation.enums.ReservationStatus;
import com.paytm.seatreservation.enums.SeatStatus;
import com.paytm.seatreservation.exception.ApiException;
import com.paytm.seatreservation.exception.ErrorCode;
import com.paytm.seatreservation.factory.ReservationFactory;
import com.paytm.seatreservation.repository.IdempotencyKeyRepository;
import com.paytm.seatreservation.repository.ReservationRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import com.paytm.seatreservation.repository.ShowSeatRepository;
import com.paytm.seatreservation.repository.ShowUserLockRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ReservationTransactionService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ShowUserLockRepository showUserLockRepository;
    private final ReservationFactory reservationFactory;
    private final List<ReservationPolicy> reservationPolicies;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReservationResult reserveTransactional(
            UUID showId,
            String userId,
            ReserveRequest request,
            String idempotencyKey
    ) {

        validateUser(userId);
        validateIdempotencyKey(idempotencyKey);

        Show show = showRepository.findById(showId)
                .orElseThrow(() ->
                        new ApiException(
                                ErrorCode.SHOW_NOT_FOUND,
                                "Show not found"
                        )
                );

        List<String> seats =
                normalizeAndValidateSeats(
                        request.getSeats()
                );

        String requestHash =
                createRequestHash(seats);

        /*
         * -------------------------------------------------------
         * 1. IDEMPOTENCY RECORD
         * -------------------------------------------------------
         */

        idempotencyKeyRepository.insertIfAbsent(
                UUID.randomUUID().toString(),
                showId.toString(),
                userId,
                idempotencyKey,
                requestHash,
                Instant.now()
        );

        IdempotencyKey existingKey =
                idempotencyKeyRepository.findForUpdate(
                        showId,
                        userId,
                        idempotencyKey
                );

        if (existingKey == null) {

            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Unable to create idempotency record"
            );
        }

        /*
         * Same key + different request
         */

        if (!requestHash.equals(
                existingKey.getRequestHash()
        )) {

            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used with a different request"
            );
        }

        /*
         * Same key + already completed
         */

        if (existingKey.getReservationId() != null) {

            Reservation original =
                    reservationRepository.findById(
                            existingKey.getReservationId()
                    ).orElseThrow(() ->
                            new ApiException(
                                    ErrorCode.RESERVATION_NOT_FOUND,
                                    "Original reservation not found"
                            )
                    );

            return new ReservationResult(
                    true,
                    toResponse(original)
            );
        }

        /*
         * -------------------------------------------------------
         * 2. LOCK SHOW + USER
         * -------------------------------------------------------
         *
         * This serializes all reservations for the same
         * user + show combination.
         */

        showUserLockRepository.insertIfAbsent(
                UUID.randomUUID().toString(),
                showId.toString(),
                userId
        );

        showUserLockRepository.findForUpdate(
                showId.toString(),
                userId
        ).orElseThrow(() ->
                new ApiException(
                        ErrorCode.CONCURRENCY_CONFLICT,
                        "Unable to acquire user reservation lock"
                )
        );

        /*
         * -------------------------------------------------------
         * 3. LOCK ALL REQUESTED SEATS
         * -------------------------------------------------------
         */

        List<ShowSeat> lockedSeats =
                showSeatRepository.findSeatsForUpdate(
                        showId,
                        seats
                );

        /*
         * Atomic validation:
         * if any requested seat does not exist,
         * nothing gets reserved.
         */

        if (lockedSeats.size() != seats.size()) {

            throw new ApiException(
                    ErrorCode.INVALID_SEAT,
                    "One or more seats are invalid"
            );
        }

        /*
         * -------------------------------------------------------
         * 4. EXISTING USER SEAT COUNT
         * -------------------------------------------------------
         */

        long existingConfirmedSeats =
                reservationRepository
                        .countConfirmedSeatsByUserAndShow(
                                showId,
                                userId
                        );

        ReservationContext context =
                new ReservationContext(
                        show,
                        userId,
                        seats,
                        lockedSeats,
                        existingConfirmedSeats
                );

        /*
         * -------------------------------------------------------
         * 5. BUSINESS POLICIES
         * -------------------------------------------------------
         */

        for (ReservationPolicy policy :
                reservationPolicies) {

            policy.validate(context);
        }

        /*
         * -------------------------------------------------------
         * 6. CREATE RESERVATION
         * -------------------------------------------------------
         */

        Reservation reservation =
                reservationFactory.create(
                        show,
                        userId,
                        lockedSeats.size()
                );

        /*
         * -------------------------------------------------------
         * 7. CONFIRM SEATS
         * -------------------------------------------------------
         */

        for (ShowSeat seat : lockedSeats) {

            /*
             * Defensive check while holding DB lock.
             */

            if (seat.getStatus()
                    != SeatStatus.AVAILABLE) {

                throw new ApiException(
                        ErrorCode.SEAT_TAKEN,
                        "Seat "
                                + seat.getSeatNumber()
                                + " is already reserved"
                );
            }

            seat.setStatus(
                    SeatStatus.CONFIRMED
            );

            ReservationSeatId reservationSeatId =
                    new ReservationSeatId(
                            reservation.getId(),
                            seat.getId()
                    );

            ReservationSeat reservationSeat =
                    ReservationSeat.builder()
                            .id(reservationSeatId)
                            .reservation(reservation)
                            .showSeat(seat)
                            .build();

            reservation.getSeats()
                    .add(reservationSeat);
        }

        /*
         * -------------------------------------------------------
         * 8. SAVE RESERVATION
         * -------------------------------------------------------
         */

        Reservation savedReservation =
                reservationRepository.save(
                        reservation
                );

        /*
         * -------------------------------------------------------
         * 9. COMPLETE IDEMPOTENCY RECORD
         * -------------------------------------------------------
         */

        existingKey.setReservationId(
                savedReservation.getId()
        );

        return new ReservationResult(
                false,
                toResponse(savedReservation)
        );
    }

    @Transactional
    public void cancel(
            UUID reservationId,
            String userId
    ) {

        Reservation reservation =
                reservationRepository.findByIdForUpdate(
                        reservationId
                ).orElseThrow(() ->
                        new ApiException(
                                ErrorCode.RESERVATION_NOT_FOUND,
                                "Reservation not found"
                        )
                );

        if (!reservation.getUserId()
                .equals(userId)) {

            throw new ApiException(
                    ErrorCode.NOT_RESERVATION_OWNER,
                    "You are not the owner of this reservation"
            );
        }

        if (reservation.getStatus()
                == ReservationStatus.CANCELLED) {

            throw new ApiException(
                    ErrorCode.RESERVATION_ALREADY_CANCELLED,
                    "Reservation is already cancelled"
            );
        }

        /*
         * Lock same show + user row.
         */

        UUID showId =
                reservation.getShow().getId();

        showUserLockRepository.insertIfAbsent(
                UUID.randomUUID().toString(),
                showId.toString(),
                userId
        );

        showUserLockRepository.findForUpdate(
                showId.toString(),
                userId
        );

        /*
         * Release seats.
         */

        for (ReservationSeat reservationSeat :
                reservation.getSeats()) {

            ShowSeat seat =
                    reservationSeat.getShowSeat();

            seat.setStatus(
                    SeatStatus.AVAILABLE
            );
        }

        reservation.setStatus(
                ReservationStatus.CANCELLED
        );

        reservation.setCancelledAt(
                Instant.now()
        );

        reservationRepository.save(
                reservation
        );
    }

    private void validateUser(String userId) {

        if (userId == null ||
                userId.isBlank()) {

            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "User authentication is required"
            );
        }
    }

    private void validateIdempotencyKey(
            String idempotencyKey
    ) {

        if (idempotencyKey == null ||
                idempotencyKey.isBlank()) {

            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key header is required"
            );
        }
    }

    private List<String> normalizeAndValidateSeats(
            List<String> requestedSeats
    ) {

        if (requestedSeats == null ||
                requestedSeats.isEmpty()) {

            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Invalid request"
            );
        }

        List<String> normalized =
                new ArrayList<>();

        for (String seat :
                requestedSeats) {

            if (seat == null ||
                    seat.trim().isEmpty()) {

                throw new ApiException(
                        ErrorCode.VALIDATION_ERROR,
                        "Invalid seat"
                );
            }

            normalized.add(
                    seat.trim().toUpperCase()
            );
        }

        /*
         * Duplicate request check.
         */

        Set<String> unique =
                new HashSet<>(normalized);

        if (unique.size() != normalized.size()) {

            throw new ApiException(
                    ErrorCode.INVALID_SEAT,
                    "Duplicate seat requested"
            );
        }

        /*
         * Deterministic lock order.
         */

        normalized.sort(String::compareTo);

        return normalized;
    }

    private String createRequestHash(
            List<String> seats
    ) {

        try {

            String json =
                    objectMapper.writeValueAsString(
                            seats
                    );

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            json.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder result =
                    new StringBuilder();

            for (byte b : hash) {

                result.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return result.toString();

        } catch (
                
                NoSuchAlgorithmException ex
        ) {

            throw new IllegalStateException(
                    "Unable to generate request hash",
                    ex
            );
        }
    }

    private ReservationResponse toResponse(
            Reservation reservation
    ) {

        List<String> seats =
                reservation.getSeats()
                        .stream()
                        .map(rs ->
                                rs.getShowSeat()
                                        .getSeatNumber()
                        )
                        .sorted()
                        .toList();

        return new ReservationResponse(
                reservation.getId(),
                reservation.getShow().getId(),
                reservation.getUserId(),
                seats,
                reservation.getAmountPaise(),
                reservation.getStatus()
        );
    }

    public record ReservationResult(
            boolean replayed,
            ReservationResponse response
    ) {
    }
}