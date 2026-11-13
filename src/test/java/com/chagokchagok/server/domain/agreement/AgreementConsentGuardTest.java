package com.chagokchagok.server.domain.agreement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chagokchagok.server.domain.agreement.exception.AgreementErrorCode;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository.RequiredConsent;
import com.chagokchagok.server.domain.agreement.service.AgreementConsentGuard;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgreementConsentGuardTest {

    private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");
    private static final UUID INSTALLATION = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final AgreementConsentRepository repository = mock(AgreementConsentRepository.class);
    private final AgreementConsentGuard guard = new AgreementConsentGuard(repository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void allowsAccessWhenAllRequiredDocumentsAreAcceptedAtCurrentVersion() {
        when(repository.findCurrentRequiredDocuments(INSTALLATION, NOW)).thenReturn(List.of(
                new RequiredConsent("CHILD_PRIVACY_COLLECTION", 1, true, true),
                new RequiredConsent("TERMS_OF_SERVICE", 1, true, true)));

        assertThatCode(() -> guard.requireCurrentConsent(INSTALLATION)).doesNotThrowAnyException();
    }

    @Test
    void ignoresPrivacyPolicyEvenWhenItIsMarkedRequiredAndHasNoConsent() {
        when(repository.findCurrentRequiredDocuments(INSTALLATION, NOW)).thenReturn(List.of(
                new RequiredConsent("CHILD_PRIVACY_COLLECTION", 1, true, true),
                new RequiredConsent("PRIVACY_POLICY", 1, false, false),
                new RequiredConsent("TERMS_OF_SERVICE", 1, true, true)));

        assertThatCode(() -> guard.requireCurrentConsent(INSTALLATION)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenARequiredDocumentHasNoConsentHistory() {
        when(repository.findCurrentRequiredDocuments(INSTALLATION, NOW)).thenReturn(List.of(
                new RequiredConsent("CHILD_PRIVACY_COLLECTION", 1, true, true),
                new RequiredConsent("TERMS_OF_SERVICE", 1, false, false)));

        assertError(AgreementErrorCode.CONSENT_REQUIRED);
    }

    @Test
    void rejectsWithOutdatedCodeWhenOnlyAnOlderVersionWasAccepted() {
        when(repository.findCurrentRequiredDocuments(INSTALLATION, NOW)).thenReturn(List.of(
                new RequiredConsent("CHILD_PRIVACY_COLLECTION", 1, true, true),
                new RequiredConsent("TERMS_OF_SERVICE", 2, false, true)));

        assertError(AgreementErrorCode.CONSENT_OUTDATED);
    }

    @Test
    void rejectsWhenEitherRequiredDocumentIsNotConfiguredAsRequired() {
        when(repository.findCurrentRequiredDocuments(INSTALLATION, NOW)).thenReturn(List.of(
                new RequiredConsent("TERMS_OF_SERVICE", 1, true, true)));

        assertError(AgreementErrorCode.REQUIRED_AGREEMENT_NOT_CONFIGURED);
    }

    private void assertError(AgreementErrorCode errorCode) {
        assertThatThrownBy(() -> guard.requireCurrentConsent(INSTALLATION))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
