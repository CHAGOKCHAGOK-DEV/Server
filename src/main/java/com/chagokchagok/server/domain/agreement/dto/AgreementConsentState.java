package com.chagokchagok.server.domain.agreement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AgreementConsentState", description = "현재 약관 문서에 대한 동의 상태")
public enum AgreementConsentState {
    ACCEPTED,
    OUTDATED,
    NOT_ACCEPTED
}
