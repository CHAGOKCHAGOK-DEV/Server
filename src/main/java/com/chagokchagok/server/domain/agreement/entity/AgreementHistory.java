package com.chagokchagok.server.domain.agreement.entity;

import com.chagokchagok.server.domain.installation.entity.Installation;
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
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "agreement_histories")
@Immutable
@Getter
public class AgreementHistory extends BaseCreatedTimeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installation_id", nullable = false)
    private Installation installation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_document_id", nullable = false)
    private AgreementDocument agreementDocument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgreementAction action;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "verification_method", length = 50)
    private String verificationMethod;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected AgreementHistory() {
    }

    public AgreementHistory(
            UUID id,
            Installation installation,
            AgreementDocument agreementDocument,
            AgreementAction action,
            Instant occurredAt,
            String verificationMethod,
            Instant verifiedAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.installation = Objects.requireNonNull(installation);
        this.agreementDocument = Objects.requireNonNull(agreementDocument);
        this.action = Objects.requireNonNull(action);
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.verificationMethod = verificationMethod;
        this.verifiedAt = verifiedAt;
    }
}
