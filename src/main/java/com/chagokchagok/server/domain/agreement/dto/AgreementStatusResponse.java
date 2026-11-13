package com.chagokchagok.server.domain.agreement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "AgreementStatusResponse", description = "설치의 약관 동의 상태 응답")
public record AgreementStatusResponse(boolean consentRequired, List<Document> documents) {

    @Schema(name = "AgreementStatusDocument", description = "약관 문서별 동의 상태")
    public record Document(long documentId, String code, int version, boolean required, AgreementConsentState state) {
    }
}
