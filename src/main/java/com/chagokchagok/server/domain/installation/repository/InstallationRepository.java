package com.chagokchagok.server.domain.installation.repository;

import com.chagokchagok.server.domain.installation.entity.Installation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallationRepository extends JpaRepository<Installation, UUID> {
}
