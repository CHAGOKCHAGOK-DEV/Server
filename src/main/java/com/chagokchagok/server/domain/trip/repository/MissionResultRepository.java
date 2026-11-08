package com.chagokchagok.server.domain.trip.repository;

import com.chagokchagok.server.domain.trip.entity.MissionResult;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionResultRepository extends JpaRepository<MissionResult, UUID> {
}
