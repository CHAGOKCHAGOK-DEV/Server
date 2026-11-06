package com.chagokchagok.server.domain.mission.repository;

import com.chagokchagok.server.domain.mission.entity.MissionPack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionPackRepository extends JpaRepository<MissionPack, Long> {
}
