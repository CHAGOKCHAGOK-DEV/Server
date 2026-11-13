package com.chagokchagok.server.domain.agreement.dto;

import com.chagokchagok.server.domain.agreement.entity.AgreementAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "AgreementConsentResponse", description = "저장된 약관 동의 이력 응답")
public record AgreementConsentResponse(List<Consent> consents) {

    @Schema(name = "AgreementConsent", description = "저장된 약관 동의 내역")
    public record Consent(UUID eventId, String code, int version, AgreementAction action,
                          Instant occurredAt, Instant recordedAt) {
    }
}
