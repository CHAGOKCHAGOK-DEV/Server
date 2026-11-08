package com.chagokchagok.server.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.chagokchagok.server.domain.installation.exception.InstallationErrorCode;
import com.chagokchagok.server.domain.installation.service.RegistrationRateLimiter;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InstallationPolicyTest {

    @Test
    void registrationLimitIsAppliedPerRemoteAddress() {
        var limiter = new RegistrationRateLimiter();
        for (int i = 0; i < 20; i++) {
            limiter.check("192.0.2.1");
        }
        limiter.check("192.0.2.2");
        assertThatThrownBy(() -> limiter.check("192.0.2.1"))
                .isInstanceOfSatisfying(GeneralException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(InstallationErrorCode.REGISTRATION_LIMITED));
    }

    @Test
    void activeWindowLimitPreservesExistingAddressesAndClearsExpiredWindows() {
        var now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        var limiter = new RegistrationRateLimiter(clock);

        IntStream.range(0, 1024).forEach(i -> limiter.check("address-" + i));
        limiter.check("address-0");
        assertThatThrownBy(() -> limiter.check("new-address"))
                .isInstanceOfSatisfying(GeneralException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(InstallationErrorCode.REGISTRATION_LIMITED));

        now.set(now.get().plus(Duration.ofHours(1)).plusSeconds(1));
        limiter.check("new-address");
    }

    @Test
    void concurrentNewAddressesCannotExceedWindowLimit() {
        var limiter = new RegistrationRateLimiter();
        var accepted = new AtomicInteger();
        IntStream.range(0, 1100).parallel().forEach(i -> {
            try {
                limiter.check("address-" + i);
                accepted.incrementAndGet();
            } catch (GeneralException exception) {
                assertThat(exception.getErrorCode()).isEqualTo(InstallationErrorCode.REGISTRATION_LIMITED);
            }
        });
        assertThat(accepted.get()).isEqualTo(1024);
    }
}
