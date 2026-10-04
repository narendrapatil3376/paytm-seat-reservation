package com.paytm.seatreservation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shows")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Show {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(
            mappedBy = "show",
            cascade = CascadeType.ALL
    )
    @Builder.Default
    private List<ShowSeat> seats = new ArrayList<>();
}