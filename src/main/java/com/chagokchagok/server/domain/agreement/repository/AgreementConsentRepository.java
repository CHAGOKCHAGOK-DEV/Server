package com.chagokchagok.server.domain.agreement.repository;

import com.chagokchagok.server.domain.agreement.dto.AgreementConsentRequest;
import com.chagokchagok.server.domain.agreement.entity.AgreementAction;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AgreementConsentRepository {

    private final JdbcTemplate jdbc;

    public boolean lockActiveInstallation(UUID installationId) {
        return !jdbc.query("SELECT id FROM installations WHERE id = ? AND status = 'ACTIVE' FOR SHARE",
                (rs, row) -> rs.getObject("id", UUID.class), installationId).isEmpty();
    }

    public List<CurrentDocument> findCurrentDocuments(Instant now) {
        return jdbc.query("""
                SELECT DISTINCT ON (code) id, code, version, title, content, is_required, effective_at
                FROM agreement_documents
                WHERE status = 'PUBLISHED' AND effective_at <= ?
                ORDER BY code, version DESC
                """, (rs, row) -> new CurrentDocument(rs.getLong("id"), rs.getString("code"), rs.getInt("version"),
                rs.getString("title"), rs.getString("content"), rs.getBoolean("is_required"),
                rs.getTimestamp("effective_at").toInstant()), Timestamp.from(now));
    }

    public List<AcceptedDocument> findAcceptedDocuments(UUID installationId) {
        return jdbc.query("""
                SELECT DISTINCT d.id, d.code FROM agreement_histories h
                JOIN agreement_documents d ON d.id = h.agreement_document_id
                WHERE h.installation_id = ? AND h.action = 'ACCEPTED'
                """, (rs, row) -> new AcceptedDocument(rs.getLong("id"), rs.getString("code")), installationId);
    }

    public List<RequiredConsent> findCurrentRequiredDocuments(UUID installationId, Instant now) {
        return jdbc.query("""
                WITH current_documents AS (
                    SELECT DISTINCT ON (code) id, code, version, is_required
                    FROM agreement_documents
                    WHERE status = 'PUBLISHED' AND effective_at <= ?
                    ORDER BY code, version DESC
                )
                SELECT d.code, d.version,
                    EXISTS (
                        SELECT 1 FROM agreement_histories h
                        WHERE h.installation_id = ? AND h.agreement_document_id = d.id AND h.action = 'ACCEPTED'
                    ) AS accepted_current,
                    EXISTS (
                        SELECT 1 FROM agreement_histories h
                        JOIN agreement_documents accepted_document ON accepted_document.id = h.agreement_document_id
                        WHERE h.installation_id = ? AND accepted_document.code = d.code AND h.action = 'ACCEPTED'
                    ) AS accepted_previously
                FROM current_documents d
                WHERE d.is_required = TRUE
                ORDER BY d.code
                """, (rs, row) -> new RequiredConsent(rs.getString("code"), rs.getInt("version"),
                rs.getBoolean("accepted_current"), rs.getBoolean("accepted_previously")),
                Timestamp.from(now), installationId, installationId);
    }

    public Optional<ConsentDocument> findConsentableDocument(String code, int version, Instant now) {
        return jdbc.query("""
                SELECT id, effective_at FROM agreement_documents
                WHERE code = ? AND version = ? AND status IN ('PUBLISHED', 'ARCHIVED') AND effective_at <= ?
                """, (rs, row) -> new ConsentDocument(rs.getLong("id"), rs.getTimestamp("effective_at").toInstant()),
                code, version, Timestamp.from(now)).stream().findFirst();
    }

    public void insertHistory(UUID installationId, long documentId, AgreementConsentRequest.Event event) {
        jdbc.update("""
                INSERT INTO agreement_histories (id, installation_id, agreement_document_id, action, occurred_at)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, event.eventId(), installationId, documentId, event.action().name(),
                Timestamp.from(event.occurredAt()));
    }

    public Optional<StoredHistory> findHistory(UUID eventId) {
        return jdbc.query("""
                SELECT h.installation_id, d.code, d.version, h.action, h.occurred_at, h.created_at
                FROM agreement_histories h
                JOIN agreement_documents d ON d.id = h.agreement_document_id
                WHERE h.id = ?
                """, (rs, row) -> new StoredHistory(rs.getObject("installation_id", UUID.class), rs.getString("code"),
                rs.getInt("version"), AgreementAction.valueOf(rs.getString("action")),
                rs.getTimestamp("occurred_at").toInstant(), rs.getTimestamp("created_at").toInstant()),
                eventId).stream().findFirst();
    }

    public record CurrentDocument(long id, String code, int version, String title, String content,
                                  boolean required, Instant effectiveAt) {
    }

    public record AcceptedDocument(long id, String code) {
    }

    public record RequiredConsent(String code, int version, boolean acceptedCurrent, boolean acceptedPreviously) {
    }

    public record ConsentDocument(long id, Instant effectiveAt) {
    }

    public record StoredHistory(UUID installationId, String code, int version, AgreementAction action,
                                Instant occurredAt, Instant recordedAt) {
    }
}
