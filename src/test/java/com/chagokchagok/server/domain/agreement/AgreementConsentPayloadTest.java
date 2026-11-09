package com.chagokchagok.server.domain.agreement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chagokchagok.server.domain.agreement.dto.AgreementConsentRequest;
import com.chagokchagok.server.domain.agreement.dto.AgreementConsentRequest.Event;
import com.chagokchagok.server.domain.agreement.entity.AgreementAction;
import com.chagokchagok.server.domain.agreement.service.AgreementConsentPayload;
import com.chagokchagok.server.global.exception.GeneralException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgreementConsentPayloadTest {

    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    private static final Instant EFFECTIVE = Instant.parse("2026-11-01T00:00:00Z");
    private static final Duration SKEW = Duration.ofMinutes(5);

    private final AgreementConsentPayload payload = new AgreementConsentPayload();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsDifferentVersionsOfSameCodeAndSkewBoundaries() {
        assertThatCode(() -> payload.validate(request(event("TERMS", 1, NOW), event("TERMS", 2, NOW))))
                .doesNotThrowAnyException();
        assertThatCode(() -> payload.validateOccurredAt(event("TERMS", 1, NOW.plus(SKEW)), EFFECTIVE, NOW))
                .doesNotThrowAnyException();
        assertThatCode(() -> payload.validateOccurredAt(event("TERMS", 1, EFFECTIVE.minus(SKEW)), EFFECTIVE, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWithdrawnDuplicatesAndSubMicrosecondPrecision() {
        Event accepted = event("TERMS", 1, NOW);
        assertInvalid(() -> payload.validate(request(new Event(UUID.randomUUID(), "TERMS", 1,
                AgreementAction.WITHDRAWN, NOW))));
        assertInvalid(() -> payload.validate(request(accepted, new Event(accepted.eventId(), "OPTIONAL", 1,
                AgreementAction.ACCEPTED, NOW))));
        assertInvalid(() -> payload.validate(request(accepted, event("TERMS", 1, NOW.minusSeconds(1)))));
        assertInvalid(() -> payload.validate(request(event("TERMS", 1, NOW.plusNanos(1)))));
    }

    @Test
    void rejectsOccurredAtOutsideSkewWindow() {
        assertInvalid(() -> payload.validateOccurredAt(event("TERMS", 1, NOW.plus(SKEW).plusNanos(1000)), EFFECTIVE, NOW));
        assertInvalid(() -> payload.validateOccurredAt(event("TERMS", 1, Instant.MAX), EFFECTIVE, NOW));
        assertInvalid(() -> payload.validateOccurredAt(event("TERMS", 1, EFFECTIVE.minus(SKEW).minusNanos(1000)),
                EFFECTIVE, NOW));
    }

    @Test
    void beanValidationBoundsEventListAndFields() {
        assertThat(validator.validate(request(events(1)))).isEmpty();
        assertThat(validator.validate(request(events(20)))).isEmpty();
        assertThat(validator.validate(request(events(0)))).isNotEmpty();
        assertThat(validator.validate(request(events(21)))).isNotEmpty();
        assertThat(validator.validate(new AgreementConsentRequest(null))).isNotEmpty();
        assertThat(validator.validate(new AgreementConsentRequest(Collections.singletonList(null)))).isNotEmpty();
        assertThat(validator.validate(request(event("A".repeat(50), 1, NOW)))).isEmpty();
        assertThat(validator.validate(request(event("A".repeat(51), 1, NOW)))).isNotEmpty();
        assertThat(validator.validate(request(event(" ", 1, NOW)))).isNotEmpty();
        assertThat(validator.validate(request(event("TERMS", 0, NOW)))).isNotEmpty();
        assertThat(validator.validate(request(new Event(UUID.randomUUID(), "TERMS", null, AgreementAction.ACCEPTED, NOW))))
                .isNotEmpty();
        assertThat(validator.validate(request(new Event(null, "TERMS", 1, AgreementAction.ACCEPTED, NOW)))).isNotEmpty();
        assertThat(validator.validate(request(new Event(UUID.randomUUID(), "TERMS", 1, null, NOW)))).isNotEmpty();
        assertThat(validator.validate(request(new Event(UUID.randomUUID(), "TERMS", 1, AgreementAction.ACCEPTED, null))))
                .isNotEmpty();
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("AGREEMENT_400"));
    }

    private List<Event> events(int count) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(event("DOC_" + i, 1, NOW));
        }
        return events;
    }

    private AgreementConsentRequest request(Event... events) {
        return new AgreementConsentRequest(List.of(events));
    }

    private AgreementConsentRequest request(List<Event> events) {
        return new AgreementConsentRequest(events);
    }

    private Event event(String code, int version, Instant occurredAt) {
        return new Event(UUID.randomUUID(), code, version, AgreementAction.ACCEPTED, occurredAt);
    }
}
