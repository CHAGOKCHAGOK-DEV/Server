package com.chagokchagok.server.domain.trip.repository;

import com.chagokchagok.server.domain.trip.dto.TripHistoryItem;
import com.chagokchagok.server.domain.trip.dto.TripMissionResultItem;
import com.chagokchagok.server.domain.trip.dto.TripResultSummary;
import com.chagokchagok.server.domain.trip.entity.MissionResultStatus;
import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TripHistoryRepository {

    // 공통 조각에는 바인딩 변수 없음, 목록과 상세의 요약 정의 공유
    private static final String TRIP_COLUMNS = """
            SELECT t.id, mp.code AS mission_pack_code, mp.version AS mission_pack_version,
                   t.status, t.end_reason, t.started_at, t.ended_at, t.created_at,
                   s.mission_result_count, s.participated_count, s.completed_count, s.correct_count
            """;

    // 결과 없는 Trip도 0건 행을 갖도록 GROUP BY 없는 집계 사용
    private static final String SUMMARY = """
            JOIN mission_packs mp ON mp.id = t.mission_pack_id
            LEFT JOIN LATERAL (
                SELECT count(*) AS mission_result_count,
                       count(*) FILTER (WHERE r.participated) AS participated_count,
                       count(*) FILTER (WHERE r.status = 'COMPLETED') AS completed_count,
                       count(*) FILTER (WHERE r.is_correct) AS correct_count
                FROM mission_results r
                WHERE r.trip_id = t.id
            ) s ON TRUE
            """;

    // 요약 집계를 확정된 페이지 행으로 한정
    private static final String FIRST_PAGE = TRIP_COLUMNS + """
            FROM (SELECT id, mission_pack_id, status, end_reason, started_at, ended_at, created_at
                  FROM trips
                  WHERE installation_id = ?
                  ORDER BY started_at DESC, id DESC
                  LIMIT ?) t
            """ + SUMMARY + "ORDER BY t.started_at DESC, t.id DESC";

    // started_at 상한을 별도 조건으로 두어 인덱스 범위 조회 유지
    private static final String NEXT_PAGE = TRIP_COLUMNS + """
            FROM (SELECT id, mission_pack_id, status, end_reason, started_at, ended_at, created_at
                  FROM trips
                  WHERE installation_id = ? AND started_at <= ? AND (started_at < ? OR id < ?)
                  ORDER BY started_at DESC, id DESC
                  LIMIT ?) t
            """ + SUMMARY + "ORDER BY t.started_at DESC, t.id DESC";

    private static final String DETAIL = TRIP_COLUMNS + """
            FROM trips t
            """ + SUMMARY + "WHERE t.id = ? AND t.installation_id = ?";

    private static final RowMapper<TripHistoryItem> TRIP = (rs, row) -> new TripHistoryItem(
            rs.getObject("id", UUID.class), rs.getString("mission_pack_code"), rs.getInt("mission_pack_version"),
            TripStatus.valueOf(rs.getString("status")), TripEndReason.valueOf(rs.getString("end_reason")),
            instant(rs, "started_at"), instant(rs, "ended_at"), instant(rs, "created_at"),
            new TripResultSummary(rs.getInt("mission_result_count"), rs.getInt("participated_count"),
                    rs.getInt("completed_count"), rs.getInt("correct_count")));

    private final JdbcTemplate jdbc;

    public List<TripHistoryItem> findFirstPage(UUID installationId, int limit) {
        return jdbc.query(FIRST_PAGE, TRIP, installationId, limit);
    }

    public List<TripHistoryItem> findPageAfter(UUID installationId, Instant startedAt, UUID tripId, int limit) {
        Timestamp position = Timestamp.from(startedAt);
        return jdbc.query(NEXT_PAGE, TRIP, installationId, position, position, tripId, limit);
    }

    public Optional<TripHistoryItem> findTrip(UUID installationId, UUID tripId) {
        return jdbc.query(DETAIL, TRIP, tripId, installationId).stream().findFirst();
    }

    public List<TripMissionResultItem> findResults(UUID installationId, UUID tripId) {
        return jdbc.query("""
                SELECT r.id, m.code AS mission_code, r.status, r.participated, r.is_correct,
                       r.started_at, r.ended_at
                FROM mission_results r
                JOIN trips t ON t.id = r.trip_id AND t.installation_id = ?
                JOIN missions m ON m.id = r.mission_id
                WHERE r.trip_id = ?
                ORDER BY r.started_at, r.id
                """, (rs, row) -> new TripMissionResultItem(rs.getObject("id", UUID.class),
                rs.getString("mission_code"), MissionResultStatus.valueOf(rs.getString("status")),
                rs.getBoolean("participated"), rs.getObject("is_correct", Boolean.class),
                instant(rs, "started_at"), instant(rs, "ended_at")), installationId, tripId);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }
}
