package com.chagokchagok.server.domain.agreement.dto;

import java.util.List;

public record AgreementStatusResponse(boolean consentRequired, List<Document> documents) {

    public record Document(long documentId, String code, int version, boolean required, AgreementConsentState state) {
    }
}
