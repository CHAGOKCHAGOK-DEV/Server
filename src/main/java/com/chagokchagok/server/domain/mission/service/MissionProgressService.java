package com.chagokchagok.server.domain.mission.service;

import com.chagokchagok.server.domain.mission.dto.DailyMissionProgressResponse;
import com.chagokchagok.server.domain.mission.repository.MissionProgressRepository;
import com.chagokchagok.server.global.exception.ErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MissionProgressService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    // 달성 인정 정책 미확정으로 목표값과 원시 건수만 제공
    private static final int DAILY_GOAL = 5;
    // 동기화 요청 시각의 연도 범위 기준
    private static final LocalDate MIN_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    private final MissionProgressRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DailyMissionProgressResponse daily(UUID installationId, LocalDate requested) {
        LocalDate date = requested == null ? LocalDate.ofInstant(clock.instant(), ZONE) : requested;
        if (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) {
            throw new GeneralException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Instant dayStart = date.atStartOfDay(ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        var counts = repository.countResults(installationId, dayStart, dayEnd);
        return new DailyMissionProgressResponse(date, ZONE.getId(), DAILY_GOAL, counts.tripCount(),
                counts.missionResultCount(), counts.participatedCount(), counts.completedCount(),
                counts.expiredCount(), counts.cancelledCount(), counts.correctCount());
    }
}
