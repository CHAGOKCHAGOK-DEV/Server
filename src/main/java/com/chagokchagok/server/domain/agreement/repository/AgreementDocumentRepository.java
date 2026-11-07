package com.chagokchagok.server.domain.agreement.repository;

import com.chagokchagok.server.domain.agreement.entity.AgreementDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgreementDocumentRepository extends JpaRepository<AgreementDocument, Long> {
}
