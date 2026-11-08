package com.chagokchagok.server.domain.mission.dto;

import java.time.LocalDate;

public record DailyMissionProgressResponse(
        LocalDate date,
        String timeZone,
        int goal,
        int contributingTripCount,
        int missionResultCount,
        int participatedCount,
        int completedCount,
        int expiredCount,
        int cancelledCount,
        int correctCount
) {
}
