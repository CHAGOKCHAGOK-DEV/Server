package com.chagokchagok.server.domain.mission.repository;

import com.chagokchagok.server.domain.mission.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionRepository extends JpaRepository<Mission, Long> {
}
