package com.chagokchagok.server.domain.copilot.repository;

import com.chagokchagok.server.domain.copilot.entity.Copilot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CopilotRepository extends JpaRepository<Copilot, UUID> {
}
