package com.chagokchagok.server.domain.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chagokchagok.server.domain.agreement.service.AgreementConsentGuard;
import com.chagokchagok.server.domain.copilot.dto.CopilotRequest;
import com.chagokchagok.server.domain.copilot.entity.AgeGroup;
import com.chagokchagok.server.domain.copilot.entity.MissionIntensity;
import com.chagokchagok.server.domain.copilot.exception.CopilotErrorCode;
import com.chagokchagok.server.domain.copilot.repository.CopilotRepository;
import com.chagokchagok.server.domain.copilot.service.CopilotService;
import com.chagokchagok.server.domain.installation.entity.Installation;
import com.chagokchagok.server.domain.installation.repository.InstallationRepository;
import com.chagokchagok.server.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

class CopilotServiceTest {

    private static final CopilotRequest REQUEST = new CopilotRequest("부기장", AgeGroup.AGE_5_6, MissionIntensity.NORMAL);

    @Test
    void duplicatePrimaryKeyAtFlushBecomesCopilotConflict() {
        var violation = mock(ConstraintViolationException.class);
        when(violation.getConstraintName()).thenReturn("pk_copilots");
        when(violation.getSQLState()).thenReturn("23505");
        var service = serviceWithFlushError(new PersistenceException("insert failed", violation));

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), REQUEST))
                .isInstanceOfSatisfying(GeneralException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CopilotErrorCode.ALREADY_EXISTS));
    }

    @Test
    void unrelatedConstraintViolationIsNotChanged() {
        var violation = mock(ConstraintViolationException.class);
        when(violation.getConstraintName()).thenReturn("fk_copilots_installation");
        when(violation.getSQLState()).thenReturn("23503");
        var failure = new PersistenceException("insert failed", violation);
        var service = serviceWithFlushError(failure);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), REQUEST)).isSameAs(failure);
    }

    private CopilotService serviceWithFlushError(PersistenceException failure) {
        var copilots = mock(CopilotRepository.class);
        var installations = mock(InstallationRepository.class);
        var manager = mock(EntityManager.class);
        var agreementConsentGuard = mock(AgreementConsentGuard.class);
        when(installations.findById(org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    return Optional.of(new Installation(id, "token-hash"));
                });
        doThrow(failure).when(manager).flush();
        return new CopilotService(copilots, installations, manager, agreementConsentGuard);
    }
}
