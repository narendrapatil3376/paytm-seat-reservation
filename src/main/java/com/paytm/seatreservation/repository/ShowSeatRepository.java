package com.paytm.seatreservation.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.paytm.seatreservation.entity.ShowSeat;

import jakarta.persistence.LockModeType;

public interface ShowSeatRepository
        extends JpaRepository<ShowSeat, UUID> {

    /**
     * Used for GET /shows/{showId}
     */
    List<ShowSeat> findByShowIdOrderBySeatNumber(
            UUID showId
    );

    /**
     * Used during reservation.
     * Pessimistic lock prevents two transactions
     * from confirming the same seat.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM ShowSeat s
        WHERE s.show.id = :showId
          AND s.seatNumber IN :seatNumbers
        ORDER BY s.seatNumber ASC
        """)
    List<ShowSeat> findSeatsForUpdate(
            @Param("showId") UUID showId,
            @Param("seatNumbers") List<String> seatNumbers
    );
}