package com.paytm.seatreservation.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.enums.ReservationStatus;

import jakarta.persistence.LockModeType;

public interface ReservationRepository
        extends JpaRepository<Reservation, UUID> {

    @Query("""
        SELECT COUNT(r)
        FROM Reservation r
        WHERE r.show.id = :showId
          AND r.userId = :userId
          AND r.status = :status
        """)
    long countSeatsByUserAndShow(
            @Param("showId") UUID showId,
            @Param("userId") String userId,
            @Param("status") ReservationStatus status
    );

    default long countConfirmedSeatsByUserAndShow(
            UUID showId,
            String userId
    ) {
        return countSeatsByUserAndShow(
                showId,
                userId,
                ReservationStatus.CONFIRMED
        );
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM Reservation r
        WHERE r.id = :reservationId
        """)
    Optional<Reservation> findByIdForUpdate(
            @Param("reservationId") UUID reservationId
    );
}