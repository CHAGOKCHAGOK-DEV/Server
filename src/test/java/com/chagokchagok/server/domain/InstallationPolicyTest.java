package com.chagokchagok.server.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chagokchagok.server.domain.installation.exception.InstallationErrorCode;
import com.chagokchagok.server.domain.installation.service.RegistrationRateLimiter;
import com.chagokchagok.server.global.exception.GeneralException;
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
}
