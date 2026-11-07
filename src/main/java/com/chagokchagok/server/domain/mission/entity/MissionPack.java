package com.chagokchagok.server.domain.mission.entity;

import com.chagokchagok.server.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

@Entity
@Table(
        name = "mission_packs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mission_packs_code_version",
                columnNames = {"code", "version"}
        )
)
@Getter
public class MissionPack extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MissionPackStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected MissionPack() {
    }

    public MissionPack(String code, int version) {
        this.code = Objects.requireNonNull(code);
        this.version = version;
        this.schemaVersion = 1;
        this.status = MissionPackStatus.DRAFT;
    }

}
