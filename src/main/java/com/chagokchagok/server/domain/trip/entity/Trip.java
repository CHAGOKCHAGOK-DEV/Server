package com.chagokchagok.server.domain.trip.entity;

import com.chagokchagok.server.domain.installation.entity.Installation;
import com.chagokchagok.server.domain.mission.entity.MissionPack;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trips")
@Getter
public class Trip extends BaseCreatedTimeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installation_id", nullable = false)
    private Installation installation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_pack_id", nullable = false)
    private MissionPack missionPack;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", nullable = false, length = 30)
    private TripEndReason endReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payload_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String payloadHash;

    protected Trip() {
    }

    public Trip(
            UUID id,
            Installation installation,
            MissionPack missionPack,
            TripStatus status,
            TripEndReason endReason,
            Instant startedAt,
            Instant endedAt,
            String payloadHash
    ) {
        this.id = Objects.requireNonNull(id);
        this.installation = Objects.requireNonNull(installation);
        this.missionPack = Objects.requireNonNull(missionPack);
        this.status = Objects.requireNonNull(status);
        this.endReason = Objects.requireNonNull(endReason);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.endedAt = Objects.requireNonNull(endedAt);
        this.payloadHash = Objects.requireNonNull(payloadHash);
    }

}
