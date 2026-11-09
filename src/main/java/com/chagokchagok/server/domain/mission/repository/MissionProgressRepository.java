package com.chagokchagok.server.domain.mission.repository;

import com.chagokchagok.server.global.common.jdbc.JdbcInstants;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MissionProgressRepository {

    private final JdbcTemplate jdbc;

    public DailyResultCounts countResults(UUID installationId, Instant dayStart, Instant dayEnd) {
        OffsetDateTime start = JdbcInstants.bind(dayStart);
        OffsetDateTime end = JdbcInstants.bind(dayEnd);
        // 결과 시작 시각이 Trip 시작 이후라는 동기화 검증 기준의 인덱스 범위 축소
        return jdbc.queryForObject("""
                SELECT count(DISTINCT r.trip_id) AS trip_count,
                       count(*) AS mission_result_count,
                       count(*) FILTER (WHERE r.participated) AS participated_count,
                       count(*) FILTER (WHERE r.status = 'COMPLETED') AS completed_count,
                       count(*) FILTER (WHERE r.status = 'EXPIRED') AS expired_count,
                       count(*) FILTER (WHERE r.status = 'CANCELLED') AS cancelled_count,
                       count(*) FILTER (WHERE r.is_correct) AS correct_count
                FROM trips t
                JOIN mission_results r ON r.trip_id = t.id
                WHERE t.installation_id = ? AND t.started_at < ?
                  AND r.started_at >= ? AND r.started_at < ?
                """, (rs, row) -> new DailyResultCounts(rs.getInt("trip_count"), rs.getInt("mission_result_count"),
                rs.getInt("participated_count"), rs.getInt("completed_count"), rs.getInt("expired_count"),
                rs.getInt("cancelled_count"), rs.getInt("correct_count")),
                installationId, end, start, end);
    }

    public record DailyResultCounts(int tripCount, int missionResultCount, int participatedCount, int completedCount,
                                    int expiredCount, int cancelledCount, int correctCount) {
    }
}
