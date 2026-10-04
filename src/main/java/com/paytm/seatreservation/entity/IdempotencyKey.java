package com.paytm.seatreservation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_idempotency",
                        columnNames = {
                                "show_id",
                                "user_id",
                                "idempotency_key"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_idempotency_lookup",
                        columnList = "show_id,user_id,idempotency_key"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @Column(
            columnDefinition = "BINARY(16)",
            nullable = false
    )
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "show_id",
            nullable = false
    )
    private Show show;

    @Column(
            name = "user_id",
            nullable = false,
            length = 100
    )
    private String userId;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 100
    )
    private String idempotencyKey;

    @Column(
            name = "request_hash",
            nullable = false,
            length = 64
    )
    private String requestHash;

    @Column(
            name = "reservation_id",
            columnDefinition = "BINARY(16)"
    )
    private UUID reservationId;

    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;
}