package com.chagokchagok.server.domain.agreement.dto;

import java.time.Instant;
import java.util.List;

public record AgreementCurrentResponse(List<Document> documents) {

    public record Document(long documentId, String code, int version, String title, String content,
                           boolean required, Instant effectiveAt) {
    }
}
