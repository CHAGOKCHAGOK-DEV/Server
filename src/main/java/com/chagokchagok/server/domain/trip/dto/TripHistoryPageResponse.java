package com.chagokchagok.server.domain.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "TripHistoryPageResponse", description = "커서 기반 주행 이력 목록 응답")
public record TripHistoryPageResponse(List<TripHistoryItem> items, String nextCursor) {
}
