package com.chagokchagok.server.domain.agreement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chagokchagok.server.domain.agreement.dto.AgreementConsentRequest;
import com.chagokchagok.server.domain.agreement.dto.AgreementConsentRequest.Event;
import com.chagokchagok.server.domain.agreement.dto.AgreementConsentState;
import com.chagokchagok.server.domain.agreement.entity.AgreementAction;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository.AcceptedDocument;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository.ConsentDocument;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository.CurrentDocument;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository.StoredHistory;
import com.chagokchagok.server.domain.agreement.service.AgreementConsentPayload;
import com.chagokchagok.server.domain.agreement.service.AgreementService;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgreementServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    private static final Instant EFFECTIVE = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant RECORDED = Instant.parse("2026-11-09T00:00:01Z");
    private static final UUID INSTALLATION = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID LOW = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID HIGH = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");

    private final AgreementConsentRepository repository = mock(AgreementConsentRepository.class);
    private final AgreementService service = new AgreementService(repository, new AgreementConsentPayload(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void statusMarksOutdatedNotAcceptedAndRequiredFlag() {
        when(repository.findCurrentDocuments(NOW)).thenReturn(List.of(current(2, "TERMS", 2, true),
                current(5, "OPTIONAL", 1, false)));
        when(repository.findAcceptedDocuments(INSTALLATION)).thenReturn(List.of(new AcceptedDocument(1, "TERMS")));

        var status = service.status(INSTALLATION);

        assertThat(status.consentRequired()).isTrue();
        assertThat(status.documents()).extracting(document -> document.state())
                .containsExactly(AgreementConsentState.OUTDATED, AgreementConsentState.NOT_ACCEPTED);
    }

    @Test
    void optionalDocumentDoesNotBlockAndNoDocumentsMeansNoConsentRequired() {
        when(repository.findCurrentDocuments(NOW)).thenReturn(List.of(current(2, "TERMS", 2, true),
                current(5, "OPTIONAL", 1, false)));
        when(repository.findAcceptedDocuments(INSTALLATION)).thenReturn(List.of(new AcceptedDocument(2, "TERMS")));
        assertThat(service.status(INSTALLATION).consentRequired()).isFalse();

        when(repository.findCurrentDocuments(NOW)).thenReturn(List.of());
        assertThat(service.status(INSTALLATION).consentRequired()).isFalse();
    }

    @Test
    void revokedInstallationStopsBeforeAnyLookup() {
        when(repository.lockActiveInstallation(INSTALLATION)).thenReturn(false);
        assertCode(() -> service.record(INSTALLATION, request(event(LOW, "TERMS", 2))), "COMMON_401");
        verify(repository, never()).findHistory(any());
        verify(repository, never()).insertHistory(any(), anyLong(), any());
    }

    @Test
    void ownershipIsCheckedBeforeContent() {
        when(repository.lockActiveInstallation(INSTALLATION)).thenReturn(true);
        when(repository.findHistory(LOW)).thenReturn(Optional.of(new StoredHistory(OTHER, "OTHER", 9,
                AgreementAction.WITHDRAWN, EFFECTIVE, RECORDED)));
        assertCode(() -> service.record(INSTALLATION, request(event(LOW, "TERMS", 2))), "AGREEMENT_404");
    }

    @Test
    void storedActionCodeVersionOrTimeMismatchIsConflict() {
        when(repository.lockActiveInstallation(INSTALLATION)).thenReturn(true);
        Event event = event(LOW, "TERMS", 2);
        for (StoredHistory stored : List.of(
                new StoredHistory(INSTALLATION, "TERMS", 2, AgreementAction.WITHDRAWN, event.occurredAt(), RECORDED),
                new StoredHistory(INSTALLATION, "OPTIONAL", 2, AgreementAction.ACCEPTED, event.occurredAt(), RECORDED),
                new StoredHistory(INSTALLATION, "TERMS", 1, AgreementAction.ACCEPTED, event.occurredAt(), RECORDED),
                new StoredHistory(INSTALLATION, "TERMS", 2, AgreementAction.ACCEPTED,
                        event.occurredAt().plusNanos(1000), RECORDED))) {
            when(repository.findHistory(LOW)).thenReturn(Optional.of(stored));
            assertCode(() -> service.record(INSTALLATION, request(event)), "AGREEMENT_409");
        }
        verify(repository, never()).insertHistory(any(), anyLong(), any());
    }

    @Test
    void replayOfCommittedEventSkipsTimeAndDocumentChecks() {
        when(repository.lockActiveInstallation(INSTALLATION)).thenReturn(true);
        // 서버 시각보다 미래인 occurredAt과 동의 불가 상태 문서에서도 커밋된 이벤트는 그대로 반환
        Event event = new Event(LOW, "TERMS", 2, AgreementAction.ACCEPTED, NOW.plusSeconds(3600));
        when(repository.findHistory(LOW)).thenReturn(Optional.of(new StoredHistory(INSTALLATION, "TERMS", 2,
                AgreementAction.ACCEPTED, event.occurredAt(), RECORDED)));

        var response = service.record(INSTALLATION, request(event));

        assertThat(response.consents()).singleElement()
                .satisfies(consent -> assertThat(consent.recordedAt()).isEqualTo(RECORDED));
        verify(repository, never()).findConsentableDocument(any(), anyInt(), any());
        verify(repository, never()).insertHistory(any(), anyLong(), any());
    }

    @Test
    void insertsFreshEventsInEventIdOrderAndMapsEachVersion() {
        when(repository.lockActiveInstallation(INSTALLATION)).thenReturn(true);
        Event high = event(HIGH, "TERMS", 2);
        Event low = event(LOW, "TERMS", 1);
        when(repository.findHistory(LOW)).thenReturn(Optional.empty(), Optional.of(stored(low)));
        when(repository.findHistory(HIGH)).thenReturn(Optional.empty(), Optional.of(stored(high)));
        when(repository.findConsentableDocument("TERMS", 1, NOW)).thenReturn(Optional.of(new ConsentDocument(1, EFFECTIVE)));
        when(repository.findConsentableDocument("TERMS", 2, NOW)).thenReturn(Optional.of(new ConsentDocument(2, EFFECTIVE)));

        var response = service.record(INSTALLATION, request(high, low));

        var order = inOrder(repository);
        order.verify(repository).insertHistory(INSTALLATION, 1L, low);
        order.verify(repository).insertHistory(INSTALLATION, 2L, high);
        assertThat(response.consents()).extracting(consent -> consent.eventId()).containsExactly(LOW, HIGH);
    }

    @Test
    void invalidFreshEventStopsBeforeAnyInsert() {
        when(repository.lockActiveInstallation(INSTALLATION)).thenReturn(true);
        Event valid = event(LOW, "TERMS", 2);
        Event invalid = event(HIGH, "TERMS", 99);
        when(repository.findHistory(any())).thenReturn(Optional.empty());
        when(repository.findConsentableDocument("TERMS", 2, NOW)).thenReturn(Optional.of(new ConsentDocument(2, EFFECTIVE)));
        when(repository.findConsentableDocument("TERMS", 99, NOW)).thenReturn(Optional.empty());

        assertCode(() -> service.record(INSTALLATION, request(valid, invalid)), "AGREEMENT_400_1");
        verify(repository, never()).insertHistory(any(), anyLong(), any());
    }

    @Test
    void existingEventErrorsTakePriorityOverFreshValidationInEventIdOrder() {
        when(repository.lockActiveInstallation(INSTALLATION)).thenReturn(true);
        Event conflicting = event(LOW, "TERMS", 2);
        Event foreign = event(HIGH, "OPTIONAL", 1);
        when(repository.findHistory(LOW)).thenReturn(Optional.of(new StoredHistory(INSTALLATION, "TERMS", 2,
                AgreementAction.ACCEPTED, conflicting.occurredAt().plusSeconds(1), RECORDED)));
        when(repository.findHistory(HIGH)).thenReturn(Optional.of(new StoredHistory(OTHER, "OPTIONAL", 1,
                AgreementAction.ACCEPTED, foreign.occurredAt(), RECORDED)));
        assertCode(() -> service.record(INSTALLATION, request(foreign, conflicting)), "AGREEMENT_409");

        Event invalidFresh = event(LOW, "TERMS", 99);
        when(repository.findHistory(LOW)).thenReturn(Optional.empty());
        assertCode(() -> service.record(INSTALLATION, request(invalidFresh, foreign)), "AGREEMENT_404");
        verify(repository, never()).findConsentableDocument(any(), anyInt(), any());
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
    }

    private StoredHistory stored(Event event) {
        return new StoredHistory(INSTALLATION, event.code(), event.version(), event.action(), event.occurredAt(), RECORDED);
    }

    private CurrentDocument current(long id, String code, int version, boolean required) {
        return new CurrentDocument(id, code, version, "제목", "원문", required, EFFECTIVE);
    }

    private AgreementConsentRequest request(Event... events) {
        return new AgreementConsentRequest(List.of(events));
    }

    private Event event(UUID id, String code, int version) {
        return new Event(id, code, version, AgreementAction.ACCEPTED, NOW.minusSeconds(60));
    }
}
