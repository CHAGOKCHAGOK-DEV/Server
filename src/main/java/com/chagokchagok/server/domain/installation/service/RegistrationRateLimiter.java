package com.chagokchagok.server.domain.installation.service;

import com.chagokchagok.server.domain.installation.exception.InstallationErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RegistrationRateLimiter {

    private static final int MAX_PER_HOUR = 20;
    private static final int MAX_ACTIVE_WINDOWS = 1024;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private Instant nextCleanupAt = Instant.MIN;

    public RegistrationRateLimiter() {
        this(Clock.systemUTC());
    }

    public RegistrationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void check(String remoteAddress) {
        Instant now = clock.instant();
        if (!now.isBefore(nextCleanupAt)) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
            nextCleanupAt = now.plus(CLEANUP_INTERVAL);
        }
        Window previous = windows.get(remoteAddress);
        if (previous == null || !now.isBefore(previous.startedAt().plus(WINDOW))) {
            if (previous == null && windows.size() >= MAX_ACTIVE_WINDOWS) {
                throw new GeneralException(InstallationErrorCode.REGISTRATION_LIMITED);
            }
            windows.put(remoteAddress, new Window(now, 1));
        } else if (previous.count() >= MAX_PER_HOUR) {
            throw new GeneralException(InstallationErrorCode.REGISTRATION_LIMITED);
        } else {
            windows.put(remoteAddress, new Window(previous.startedAt(), previous.count() + 1));
        }
    }

    private record Window(Instant startedAt, int count) {
    }
}
