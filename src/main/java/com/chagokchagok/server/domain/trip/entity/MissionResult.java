package com.chagokchagok.server.domain.trip.entity;

import com.chagokchagok.server.domain.mission.entity.Mission;
import com.chagokchagok.server.global.common.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "mission_results")
@Getter
public class MissionResult extends BaseCreatedTimeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MissionResultStatus status;

    @Column(nullable = false)
    private boolean participated;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    protected MissionResult() {
    }

    public MissionResult(
            UUID id,
            Trip trip,
            Mission mission,
            MissionResultStatus status,
            boolean participated,
            Boolean correct,
            Instant startedAt,
            Instant endedAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.trip = Objects.requireNonNull(trip);
        this.mission = Objects.requireNonNull(mission);
        this.status = Objects.requireNonNull(status);
        this.participated = participated;
        this.correct = correct;
        this.startedAt = Objects.requireNonNull(startedAt);
        this.endedAt = Objects.requireNonNull(endedAt);
    }

}
