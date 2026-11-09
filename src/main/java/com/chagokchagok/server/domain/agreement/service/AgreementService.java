package com.chagokchagok.server.domain.agreement.service;

import com.chagokchagok.server.domain.agreement.dto.AgreementConsentRequest;
import com.chagokchagok.server.domain.agreement.dto.AgreementConsentResponse;
import com.chagokchagok.server.domain.agreement.dto.AgreementConsentState;
import com.chagokchagok.server.domain.agreement.dto.AgreementCurrentResponse;
import com.chagokchagok.server.domain.agreement.dto.AgreementStatusResponse;
import com.chagokchagok.server.domain.agreement.exception.AgreementErrorCode;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository.AcceptedDocument;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository.ConsentDocument;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository.CurrentDocument;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository.StoredHistory;
import com.chagokchagok.server.global.exception.ErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgreementService {

    private final AgreementConsentRepository repository;
    private final AgreementConsentPayload payload;
    private final Clock clock;

    public AgreementCurrentResponse current() {
        return new AgreementCurrentResponse(repository.findCurrentDocuments(clock.instant()).stream()
                .map(document -> new AgreementCurrentResponse.Document(document.id(), document.code(),
                        document.version(), document.title(), document.content(), document.required(),
                        document.effectiveAt()))
                .toList());
    }

    public AgreementStatusResponse status(UUID installationId) {
        List<CurrentDocument> documents = repository.findCurrentDocuments(clock.instant());
        List<AcceptedDocument> accepted = repository.findAcceptedDocuments(installationId);
        Set<Long> acceptedIds = accepted.stream().map(AcceptedDocument::id).collect(Collectors.toSet());
        Set<String> acceptedCodes = accepted.stream().map(AcceptedDocument::code).collect(Collectors.toSet());
        List<AgreementStatusResponse.Document> states = documents.stream()
                .map(document -> new AgreementStatusResponse.Document(document.id(), document.code(),
                        document.version(), document.required(),
                        acceptedIds.contains(document.id()) ? AgreementConsentState.ACCEPTED
                                : acceptedCodes.contains(document.code()) ? AgreementConsentState.OUTDATED
                                : AgreementConsentState.NOT_ACCEPTED))
                .toList();
        boolean consentRequired = states.stream()
                .anyMatch(state -> state.required() && state.state() != AgreementConsentState.ACCEPTED);
        return new AgreementStatusResponse(consentRequired, states);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    public AgreementConsentResponse record(UUID installationId, AgreementConsentRequest request) {
        if (!repository.lockActiveInstallation(installationId)) {
            throw new GeneralException(ErrorCode.UNAUTHORIZED);
        }
        payload.validate(request);
        Instant now = clock.instant();
        // 여러 요청이 공유하는 이벤트 UUID를 같은 순서로 잠가 교차 경합의 데드락 방지
        List<AgreementConsentRequest.Event> events = request.events().stream()
                .sorted(Comparator.comparing(event -> event.eventId().toString())).toList();

        Map<UUID, StoredHistory> stored = new HashMap<>();
        List<AgreementConsentRequest.Event> fresh = new ArrayList<>();
        for (AgreementConsentRequest.Event event : events) {
            var existing = repository.findHistory(event.eventId());
            if (existing.isPresent()) {
                verify(existing.get(), installationId, event);
                stored.put(event.eventId(), existing.get());
            } else {
                fresh.add(event);
            }
        }

        Map<UUID, Long> documentIds = new HashMap<>();
        for (AgreementConsentRequest.Event event : fresh) {
            ConsentDocument document = repository.findConsentableDocument(event.code(), event.version(), now)
                    .orElseThrow(() -> new GeneralException(AgreementErrorCode.INVALID_DOCUMENT));
            payload.validateOccurredAt(event, document.effectiveAt(), now);
            documentIds.put(event.eventId(), document.id());
        }
        for (AgreementConsentRequest.Event event : fresh) {
            repository.insertHistory(installationId, documentIds.get(event.eventId()), event);
            // READ_COMMITTED의 후속 SELECT로 동시 INSERT 승자의 커밋 결과 확인
            StoredHistory history = repository.findHistory(event.eventId()).orElseThrow();
            verify(history, installationId, event);
            stored.put(event.eventId(), history);
        }

        return new AgreementConsentResponse(events.stream().map(event -> {
            StoredHistory history = stored.get(event.eventId());
            return new AgreementConsentResponse.Consent(event.eventId(), history.code(), history.version(),
                    history.action(), history.occurredAt(), history.recordedAt());
        }).toList());
    }

    private void verify(StoredHistory stored, UUID installationId, AgreementConsentRequest.Event event) {
        if (!stored.installationId().equals(installationId)) {
            throw new GeneralException(AgreementErrorCode.CONSENT_NOT_FOUND);
        }
        if (!stored.code().equals(event.code()) || stored.version() != event.version()
                || stored.action() != event.action() || !stored.occurredAt().equals(event.occurredAt())) {
            throw new GeneralException(AgreementErrorCode.CONSENT_CONFLICT);
        }
    }
}
