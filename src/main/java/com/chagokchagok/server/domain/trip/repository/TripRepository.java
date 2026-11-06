package com.chagokchagok.server.domain.trip.repository;

import com.chagokchagok.server.domain.trip.entity.Trip;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {
}
