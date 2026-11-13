package com.chagokchagok.server.domain.mission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(name = "DailyMissionProgressResponse", description = "일별 미션 참여 진행도 응답")
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
