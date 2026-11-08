package com.chagokchagok.server.domain.trip.dto;

import java.util.List;

public record TripHistoryPageResponse(List<TripHistoryItem> items, String nextCursor) {
}
