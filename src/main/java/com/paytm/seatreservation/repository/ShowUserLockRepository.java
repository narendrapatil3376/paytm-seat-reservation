package com.paytm.seatreservation.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.paytm.seatreservation.entity.ShowUserLock;

public interface ShowUserLockRepository
        extends JpaRepository<ShowUserLock, UUID> {

    @Modifying
    @Query(
        value = """
            INSERT INTO show_user_locks
            (
                id,
                show_id,
                user_id
            )
            VALUES
            (
                UUID_TO_BIN(:id),
                UUID_TO_BIN(:showId),
                :userId
            )
            ON DUPLICATE KEY UPDATE
                id = id
            """,
        nativeQuery = true
    )
    int insertIfAbsent(
            @Param("id") String id,
            @Param("showId") String showId,
            @Param("userId") String userId
    );

    @Query(
        value = """
            SELECT *
            FROM show_user_locks
            WHERE show_id = UUID_TO_BIN(:showId)
              AND user_id = :userId
            FOR UPDATE
            """,
        nativeQuery = true
    )
    Optional<ShowUserLock> findForUpdate(
            @Param("showId") String showId,
            @Param("userId") String userId
    );
}