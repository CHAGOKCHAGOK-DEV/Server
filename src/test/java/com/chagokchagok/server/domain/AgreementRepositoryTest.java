package com.chagokchagok.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chagokchagok.server.domain.agreement.entity.AgreementAction;
import com.chagokchagok.server.domain.agreement.entity.AgreementDocument;
import com.chagokchagok.server.domain.agreement.entity.AgreementDocumentStatus;
import com.chagokchagok.server.domain.agreement.entity.AgreementHistory;
import com.chagokchagok.server.domain.agreement.repository.AgreementDocumentRepository;
import com.chagokchagok.server.domain.agreement.repository.AgreementHistoryRepository;
import com.chagokchagok.server.domain.installation.entity.Installation;
import com.chagokchagok.server.domain.installation.repository.InstallationRepository;
import com.chagokchagok.server.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AgreementRepositoryTest {

    @Autowired
    private AgreementDocumentRepository documents;

    @Autowired
    private AgreementHistoryRepository histories;

    @Autowired
    private InstallationRepository installations;

    @Autowired
    private EntityManager entityManager;

    @Test
    void preservesDocumentVersionsAndSeparateAcceptanceWithdrawalEventsWithoutCopilot() {
        Installation installation = installations.saveAndFlush(
                new Installation(UUID.randomUUID(), "a".repeat(64)));
        AgreementDocument v1 = documents.saveAndFlush(document(1));
        AgreementDocument v2 = documents.saveAndFlush(document(2));
        Instant occurredAt = Instant.parse("2026-11-01T00:00:00Z");
        UUID acceptedId = UUID.randomUUID();
        histories.saveAndFlush(new AgreementHistory(acceptedId, installation, v1,
                AgreementAction.ACCEPTED, occurredAt, null, null));
        histories.saveAndFlush(new AgreementHistory(UUID.randomUUID(), installation, v1,
                AgreementAction.WITHDRAWN, occurredAt.plusSeconds(60), null, null));
        histories.saveAndFlush(new AgreementHistory(UUID.randomUUID(), installation, v2,
                AgreementAction.ACCEPTED, occurredAt.plusSeconds(120), "TEST_VERIFICATION", occurredAt));
        entityManager.clear();

        AgreementHistory accepted = histories.findById(acceptedId).orElseThrow();
        assertThat(histories.count()).isEqualTo(3);
        assertThat(accepted.getInstallation().getId()).isEqualTo(installation.getId());
        assertThat(accepted.getAgreementDocument().getContent()).isEqualTo("원문 v1");
        assertThat(accepted.getAgreementDocument().getStatus()).isEqualTo(AgreementDocumentStatus.DRAFT);
        assertThat(accepted.getAgreementDocument().getCreatedAt()).isNotNull();
        assertThat(accepted.getAgreementDocument().getUpdatedAt()).isNotNull();
        assertThat(accepted.getAction()).isEqualTo(AgreementAction.ACCEPTED);
        assertThat(accepted.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(accepted.getCreatedAt()).isNotNull();
        assertThat(accepted.getVerificationMethod()).isNull();
        assertThat(accepted.getVerifiedAt()).isNull();
    }

    @Test
    void rejectsDuplicateDocumentCodeAndVersion() {
        documents.saveAndFlush(document(1));
        assertThatThrownBy(() -> documents.saveAndFlush(document(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mergeCannotOverwriteExistingHistory() {
        Installation installation = installations.saveAndFlush(
                new Installation(UUID.randomUUID(), "a".repeat(64)));
        AgreementDocument document = documents.saveAndFlush(document(1));
        UUID id = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-01T00:00:00Z");
        histories.saveAndFlush(new AgreementHistory(id, installation, document,
                AgreementAction.ACCEPTED, occurredAt, null, null));
        entityManager.clear();

        histories.saveAndFlush(new AgreementHistory(id, installation, document,
                AgreementAction.WITHDRAWN, occurredAt.plusSeconds(60), "FORGED", occurredAt));
        entityManager.clear();

        AgreementHistory stored = histories.findById(id).orElseThrow();
        assertThat(histories.count()).isEqualTo(1);
        assertThat(stored.getAction()).isEqualTo(AgreementAction.ACCEPTED);
        assertThat(stored.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(stored.getVerificationMethod()).isNull();
        assertThat(stored.getVerifiedAt()).isNull();
    }

    @Test
    void cannotDeleteDocumentReferencedByHistory() {
        Installation installation = installations.saveAndFlush(
                new Installation(UUID.randomUUID(), "a".repeat(64)));
        AgreementDocument document = documents.saveAndFlush(document(1));
        histories.saveAndFlush(new AgreementHistory(UUID.randomUUID(), installation, document,
                AgreementAction.ACCEPTED, Instant.now(), null, null));

        assertThatThrownBy(() -> {
            documents.delete(document);
            documents.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cannotDeleteInstallationReferencedByHistory() {
        Installation installation = installations.saveAndFlush(
                new Installation(UUID.randomUUID(), "a".repeat(64)));
        AgreementDocument document = documents.saveAndFlush(document(1));
        histories.saveAndFlush(new AgreementHistory(UUID.randomUUID(), installation, document,
                AgreementAction.ACCEPTED, Instant.now(), null, null));

        assertThatThrownBy(() -> {
            installations.delete(installation);
            installations.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private AgreementDocument document(int version) {
        return new AgreementDocument("PRIVACY_COLLECTION_USE", version, "개인정보 수집·이용", "원문 v" + version, true);
    }
}
