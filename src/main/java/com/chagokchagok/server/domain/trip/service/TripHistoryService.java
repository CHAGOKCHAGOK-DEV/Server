package com.chagokchagok.server.domain.trip.service;

import com.chagokchagok.server.domain.trip.dto.TripDetailResponse;
import com.chagokchagok.server.domain.trip.dto.TripHistoryItem;
import com.chagokchagok.server.domain.trip.dto.TripHistoryPageResponse;
import com.chagokchagok.server.domain.trip.exception.TripErrorCode;
import com.chagokchagok.server.domain.trip.repository.TripHistoryRepository;
import com.chagokchagok.server.global.exception.GeneralException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripHistoryService {

    private final TripHistoryRepository repository;

    @Transactional(readOnly = true)
    public TripHistoryPageResponse list(UUID installationId, String cursor, int size) {
        List<TripHistoryItem> rows;
        if (cursor == null) {
            rows = repository.findFirstPage(installationId, size + 1);
        } else {
            TripHistoryCursor after = TripHistoryCursor.decode(cursor);
            rows = repository.findPageAfter(installationId, after.startedAt(), after.tripId(), size + 1);
        }
        if (rows.size() <= size) {
            return new TripHistoryPageResponse(rows, null);
        }
        List<TripHistoryItem> page = List.copyOf(rows.subList(0, size));
        TripHistoryItem last = page.getLast();
        return new TripHistoryPageResponse(page, new TripHistoryCursor(last.startedAt(), last.tripId()).encode());
    }

    @Transactional(readOnly = true)
    public TripDetailResponse detail(UUID installationId, UUID tripId) {
        TripHistoryItem trip = repository.findTrip(installationId, tripId)
                .orElseThrow(() -> new GeneralException(TripErrorCode.TRIP_NOT_FOUND));
        return TripDetailResponse.of(trip, repository.findResults(installationId, tripId));
    }
}
