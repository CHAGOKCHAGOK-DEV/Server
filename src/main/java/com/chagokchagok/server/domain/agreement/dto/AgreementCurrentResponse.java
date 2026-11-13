package com.chagokchagok.server.domain.agreement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "AgreementCurrentResponse", description = "현재 유효한 약관 문서 목록 응답")
public record AgreementCurrentResponse(List<Document> documents) {

    @Schema(name = "AgreementCurrentDocument", description = "현재 유효한 약관 문서")
    public record Document(long documentId, String code, int version, String title, String content,
                           boolean required, Instant effectiveAt) {
    }
}
