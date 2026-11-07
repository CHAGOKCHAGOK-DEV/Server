package com.chagokchagok.server.domain.agreement.entity;

import com.chagokchagok.server.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

@Entity
@Table(name = "agreement_documents", uniqueConstraints = @UniqueConstraint(
        name = "uk_agreement_documents_code_version", columnNames = {"code", "version"}
))
@Getter
public class AgreementDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgreementDocumentStatus status;

    @Column(name = "effective_at")
    private Instant effectiveAt;

    protected AgreementDocument() {
    }

    public AgreementDocument(String code, int version, String title, String content, boolean required) {
        this.code = Objects.requireNonNull(code);
        this.version = version;
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.required = required;
        this.status = AgreementDocumentStatus.DRAFT;
    }
}
