package com.paytm.seatreservation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "show_user_locks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_show_user_lock",
                        columnNames = {
                                "show_id",
                                "user_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowUserLock {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "user_id", nullable = false)
    private String userId;
}