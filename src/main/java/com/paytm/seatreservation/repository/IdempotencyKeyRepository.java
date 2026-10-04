package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO idempotency_keys
        (
            id,
            show_id,
            user_id,
            idempotency_key,
            request_hash,
            created_at
        )
        VALUES
        (
            UUID_TO_BIN(:id),
            UUID_TO_BIN(:showId),
            :userId,
            :idempotencyKey,
            :requestHash,
            :createdAt
        )
        ON DUPLICATE KEY UPDATE
            id = id
        """, nativeQuery = true)
    void insertIfAbsent(
            @Param("id") String id,
            @Param("showId") String showId,
            @Param("userId") String userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("createdAt") Instant createdAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT i
        FROM IdempotencyKey i
        WHERE i.show.id = :showId
          AND i.userId = :userId
          AND i.idempotencyKey = :idempotencyKey
        """)
    IdempotencyKey findForUpdate(
            @Param("showId") UUID showId,
            @Param("userId") String userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}