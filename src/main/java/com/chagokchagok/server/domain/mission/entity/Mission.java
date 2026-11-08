package com.chagokchagok.server.domain.mission.entity;

import com.chagokchagok.server.global.common.entity.BaseTimeEntity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "missions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_missions_pack_code",
                columnNames = {"mission_pack_id", "code"}
        )
)
@Getter
public class Mission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_pack_id", nullable = false)
    private MissionPack missionPack;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private TriggerType triggerType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode config;

    @Column(nullable = false)
    private boolean enabled;

    protected Mission() {
    }

    public Mission(MissionPack missionPack, String code, TriggerType triggerType, JsonNode config) {
        this.missionPack = Objects.requireNonNull(missionPack);
        this.code = Objects.requireNonNull(code);
        this.triggerType = Objects.requireNonNull(triggerType);
        this.config = Objects.requireNonNull(config);
        this.enabled = true;
    }

}
