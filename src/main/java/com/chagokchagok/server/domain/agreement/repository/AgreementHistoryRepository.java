package com.chagokchagok.server.domain.agreement.repository;

import com.chagokchagok.server.domain.agreement.entity.AgreementHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgreementHistoryRepository extends JpaRepository<AgreementHistory, UUID> {
}
