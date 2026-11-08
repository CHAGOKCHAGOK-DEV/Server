package com.chagokchagok.server.domain.trip.repository;


import com.chagokchagok.server.domain.trip.dto.TripSyncRequest;
import com.chagokchagok.server.domain.trip.dto.TripSyncResponse;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TripSyncRepository {

    private final JdbcTemplate jdbc;

    public boolean lockActiveInstallation(UUID installationId) {
        return !jdbc.query("SELECT id FROM installations WHERE id = ? AND status = 'ACTIVE' FOR SHARE",
                (rs, row) -> rs.getObject("id", UUID.class), installationId).isEmpty();
    }

    public Optional<StoredTrip> findTrip(UUID tripId) {
        return jdbc.query("""
                SELECT t.id, t.installation_id, t.payload_hash, t.created_at,
                       (SELECT count(*) FROM mission_results r WHERE r.trip_id = t.id) AS result_count
                FROM trips t WHERE t.id = ?
                """, (rs, row) -> new StoredTrip(rs.getObject("installation_id", UUID.class),
                rs.getString("payload_hash"), new TripSyncResponse(rs.getObject("id", UUID.class),
                rs.getTimestamp("created_at").toInstant(), rs.getInt("result_count"))), tripId).stream().findFirst();
    }

    public Optional<Long> findAvailablePack(String code, int version) {
        return jdbc.query("""
                SELECT id FROM mission_packs WHERE code = ? AND version = ?
                AND status IN ('PUBLISHED', 'ARCHIVED')
                """, (rs, row) -> rs.getLong("id"), code, version).stream().findFirst();
    }

    public List<MissionReference> findMissions(long packId) {
        return jdbc.query("SELECT id, code FROM missions WHERE mission_pack_id = ?",
                (rs, row) -> new MissionReference(rs.getLong("id"), rs.getString("code")), packId);
    }

    public boolean insertTrip(UUID installationId, long packId, TripSyncRequest request, String hash) {
        return jdbc.update("""
                INSERT INTO trips (id, installation_id, mission_pack_id, status, end_reason,
                                   started_at, ended_at, payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, request.tripId(), installationId, packId, request.status().name(), request.endReason().name(),
                Timestamp.from(request.startedAt()), Timestamp.from(request.endedAt()), hash) == 1;
    }

    public boolean insertResult(UUID tripId, long missionId, TripSyncRequest.Result result) {
        return jdbc.update("""
                INSERT INTO mission_results (id, trip_id, mission_id, status, participated,
                                             is_correct, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, result.resultId(), tripId, missionId, result.status().name(), result.participated(),
                result.correct(), Timestamp.from(result.startedAt()), Timestamp.from(result.endedAt())) == 1;
    }

    public record StoredTrip(UUID installationId, String payloadHash, TripSyncResponse response) {
    }

    public record MissionReference(long id, String code) {
    }
}
