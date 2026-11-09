package com.chagokchagok.server.domain.agreement.service;

import com.chagokchagok.server.domain.agreement.dto.AgreementConsentRequest;
import com.chagokchagok.server.domain.agreement.entity.AgreementAction;
import com.chagokchagok.server.domain.agreement.exception.AgreementErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AgreementConsentPayload {

    static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    public void validate(AgreementConsentRequest request) {
        Set<UUID> ids = new HashSet<>();
        Set<DocumentKey> documents = new HashSet<>();
        for (AgreementConsentRequest.Event event : request.events()) {
            // WITHDRAWN은 현재 동의 상태의 순서 정책 확정 전까지 거부
            if (event.action() != AgreementAction.ACCEPTED
                    || !ids.add(event.eventId())
                    || !documents.add(DocumentKey.of(event))
                    // TIMESTAMPTZ 마이크로초 저장으로 재전송 비교 불일치 방지
                    || event.occurredAt().getNano() % 1000 != 0) {
                invalid();
            }
        }
    }

    // 신규 이벤트에만 적용하여 커밋된 이벤트의 재전송이 서버 시각에 좌우되지 않도록 유지
    public void validateOccurredAt(AgreementConsentRequest.Event event, Instant effectiveAt, Instant now) {
        if (event.occurredAt().isAfter(now.plus(CLOCK_SKEW))
                || event.occurredAt().isBefore(effectiveAt.minus(CLOCK_SKEW))) {
            invalid();
        }
    }

    private void invalid() {
        throw new GeneralException(AgreementErrorCode.INVALID_CONSENT);
    }

    public record DocumentKey(String code, int version) {
        static DocumentKey of(AgreementConsentRequest.Event event) {
            return new DocumentKey(event.code(), event.version());
        }
    }
}
