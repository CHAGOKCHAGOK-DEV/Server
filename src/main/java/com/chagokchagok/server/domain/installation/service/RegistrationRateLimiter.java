package com.chagokchagok.server.domain.installation.service;

import com.chagokchagok.server.domain.installation.exception.InstallationErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RegistrationRateLimiter {

    private static final int MAX_PER_HOUR = 20;
    private static final Duration WINDOW = Duration.ofHours(1);
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    public void check(String remoteAddress) {
        Instant now = clock.instant();
        if (windows.size() > 1024) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
        }
        Window current = windows.compute(remoteAddress, (key, previous) ->
                previous == null || !now.isBefore(previous.startedAt().plus(WINDOW))
                        ? new Window(now, 1) : new Window(previous.startedAt(), previous.count() + 1));
        if (current.count() > MAX_PER_HOUR) {
            throw new GeneralException(InstallationErrorCode.REGISTRATION_LIMITED);
        }
    }

    private record Window(Instant startedAt, int count) {
    }
}
