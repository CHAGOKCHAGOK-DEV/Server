package com.chagokchagok.server.domain.copilot.entity;

import com.chagokchagok.server.domain.installation.entity.Installation;
import com.chagokchagok.server.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "copilots")
@Getter
public class Copilot extends BaseTimeEntity {

    @Id
    @Column(name = "installation_id")
    private UUID installationId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installation_id", nullable = false)
    private Installation installation;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false, length = 20)
    private AgeGroup ageGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_intensity", nullable = false, length = 20)
    private MissionIntensity missionIntensity;

    protected Copilot() {
    }

    public Copilot(Installation installation, String nickname, AgeGroup ageGroup) {
        this.installation = Objects.requireNonNull(installation);
        this.nickname = Objects.requireNonNull(nickname);
        this.ageGroup = Objects.requireNonNull(ageGroup);
        this.missionIntensity = MissionIntensity.NORMAL;
    }

}
