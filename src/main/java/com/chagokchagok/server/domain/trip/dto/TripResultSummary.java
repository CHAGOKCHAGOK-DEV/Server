package com.chagokchagok.server.domain.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TripResultSummary", description = "주행 미션 결과 집계")
public record TripResultSummary(int missionResultCount, int participatedCount, int completedCount, int correctCount) {
}
