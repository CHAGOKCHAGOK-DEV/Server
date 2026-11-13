package com.chagokchagok.server.domain.agreement.service;

import com.chagokchagok.server.domain.agreement.exception.AgreementErrorCode;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository.RequiredConsent;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgreementConsentGuard {

    private static final Set<String> REQUIRED_DOCUMENT_CODES = Set.of(
            "TERMS_OF_SERVICE", "CHILD_PRIVACY_COLLECTION");

    private final AgreementConsentRepository repository;
    private final Clock clock;

    public void requireCurrentConsent(UUID installationId) {
        List<RequiredConsent> documents = repository.findCurrentRequiredDocuments(installationId, clock.instant());
        Set<String> configuredCodes = documents.stream()
                .map(RequiredConsent::code)
                .collect(Collectors.toSet());
        if (!configuredCodes.containsAll(REQUIRED_DOCUMENT_CODES)) {
            throw new GeneralException(AgreementErrorCode.REQUIRED_AGREEMENT_NOT_CONFIGURED);
        }

        for (RequiredConsent document : documents) {
            if (!document.acceptedCurrent()) {
                AgreementErrorCode errorCode = document.acceptedPreviously()
                        ? AgreementErrorCode.CONSENT_OUTDATED
                        : AgreementErrorCode.CONSENT_REQUIRED;
                throw new GeneralException(errorCode);
            }
        }
    }
}
