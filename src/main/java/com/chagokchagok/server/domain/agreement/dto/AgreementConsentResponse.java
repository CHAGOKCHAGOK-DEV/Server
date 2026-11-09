package com.chagokchagok.server.domain.agreement.dto;

import com.chagokchagok.server.domain.agreement.entity.AgreementAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgreementConsentResponse(List<Consent> consents) {

    public record Consent(UUID eventId, String code, int version, AgreementAction action,
                          Instant occurredAt, Instant recordedAt) {
    }
}
