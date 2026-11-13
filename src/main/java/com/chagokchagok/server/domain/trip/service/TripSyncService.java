package com.chagokchagok.server.domain.trip.service;

import com.chagokchagok.server.domain.agreement.service.AgreementConsentGuard;
import com.chagokchagok.server.domain.trip.dto.TripSyncRequest;
import com.chagokchagok.server.domain.trip.dto.TripSyncResponse;
import com.chagokchagok.server.domain.trip.exception.TripErrorCode;
import com.chagokchagok.server.domain.trip.repository.TripSyncRepository;
import com.chagokchagok.server.domain.trip.repository.TripSyncRepository.MissionReference;
import com.chagokchagok.server.domain.trip.repository.TripSyncRepository.StoredTrip;
import com.chagokchagok.server.global.exception.ErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripSyncService {

    private final TripSyncRepository repository;
    private final TripSyncPayload payload;
    private final AgreementConsentGuard agreementConsentGuard;

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    public TripSyncResponse sync(UUID installationId, TripSyncRequest request) {
        if (!repository.lockActiveInstallation(installationId)) {
            throw new GeneralException(ErrorCode.UNAUTHORIZED);
        }
        agreementConsentGuard.requireCurrentConsent(installationId);
        payload.validate(request);
        String hash = payload.hash(request);
        var existing = repository.findTrip(request.tripId());
        if (existing.isPresent()) {
            return replay(existing.get(), installationId, hash);
        }

        long packId = repository.findAvailablePack(request.missionPackCode(), request.missionPackVersion())
                .orElseThrow(() -> new GeneralException(TripErrorCode.INVALID_CONTENT));
        Map<String, MissionReference> missions = repository.findMissions(packId).stream()
                .collect(Collectors.toMap(MissionReference::code, Function.identity()));
        if (request.missionResults().stream().anyMatch(result -> !missions.containsKey(result.missionCode()))) {
            throw new GeneralException(TripErrorCode.INVALID_CONTENT);
        }

        // READ_COMMITTED의 후속 SELECT로 동시 INSERT 승자의 커밋 결과 확인
        if (!repository.insertTrip(installationId, packId, request, hash)) {
            return replay(repository.findTrip(request.tripId()).orElseThrow(), installationId, hash);
        }
        // 여러 요청이 공유하는 결과 UUID를 같은 순서로 잠가 교차 경합의 데드락 방지
        for (TripSyncRequest.Result result : request.missionResults().stream()
                .sorted(Comparator.comparing(r -> r.resultId().toString())).toList()) {
            if (!repository.insertResult(request.tripId(), missions.get(result.missionCode()).id(), result)) {
                throw new GeneralException(TripErrorCode.SYNC_CONFLICT);
            }
        }
        return repository.findTrip(request.tripId()).orElseThrow().response();
    }

    private TripSyncResponse replay(StoredTrip existing, UUID installationId, String hash) {
        if (!existing.installationId().equals(installationId)) {
            throw new GeneralException(TripErrorCode.TRIP_NOT_FOUND);
        }
        if (!existing.payloadHash().equals(hash)) {
            throw new GeneralException(TripErrorCode.SYNC_CONFLICT);
        }
        return existing.response();
    }
}
