package com.paytm.seatreservation.service;

import java.util.UUID;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveRequest;
import com.paytm.seatreservation.exception.ApiException;
import com.paytm.seatreservation.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int MAX_ATTEMPTS = 3;

    private final ReservationTransactionService transactionService;

    public ReservationResult reserve(
            UUID showId,
            String userId,
            ReserveRequest request,
            String idempotencyKey
    ) {

        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS;
             attempt++) {

            try {

                ReservationTransactionService.ReservationResult result =
                        transactionService.reserveTransactional(
                                showId,
                                userId,
                                request,
                                idempotencyKey
                        );

                return new ReservationResult(
                        result.replayed(),
                        result.response()
                );

            } catch (
                    CannotAcquireLockException |
                    DeadlockLoserDataAccessException ex
            ) {

                if (attempt == MAX_ATTEMPTS) {

                    throw new ApiException(
                            ErrorCode.CONCURRENCY_CONFLICT,
                            "Unable to complete reservation due to high concurrency"
                    );
                }

                sleepBeforeRetry(attempt);
            }
        }

        throw new ApiException(
                ErrorCode.CONCURRENCY_CONFLICT,
                "Unable to complete reservation"
        );
    }

    public void cancel(
            UUID reservationId,
            String userId
    ) {

        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS;
             attempt++) {

            try {

                transactionService.cancel(
                        reservationId,
                        userId
                );

                return;

            } catch (
                    CannotAcquireLockException |
                    DeadlockLoserDataAccessException ex
            ) {

                if (attempt == MAX_ATTEMPTS) {

                    throw new ApiException(
                            ErrorCode.CONCURRENCY_CONFLICT,
                            "Unable to cancel reservation due to high concurrency"
                    );
                }

                sleepBeforeRetry(attempt);
            }
        }
    }

    private void sleepBeforeRetry(int attempt) {

        try {

            Thread.sleep(20L * attempt);

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();

            throw new ApiException(
                    ErrorCode.CONCURRENCY_CONFLICT,
                    "Reservation retry interrupted"
            );
        }
    }

    /*
     * Keep ReservationResult here so your existing
     * controller code continues to work.
     */
    public record ReservationResult(
            boolean replayed,
            ReservationResponse response
    ) {
    }
}