package com.chagokchagok.server.domain.agreement.dto;

import com.chagokchagok.server.domain.agreement.entity.AgreementAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgreementConsentRequest(
        @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Event> events
) {
    public record Event(
            @NotNull UUID eventId,
            @NotBlank @Size(max = 50) String code,
            @NotNull @Positive Integer version,
            @NotNull AgreementAction action,
            @NotNull Instant occurredAt
    ) {
    }
}
