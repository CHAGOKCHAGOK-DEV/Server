package com.chagokchagok.server.domain.copilot.service;

import com.chagokchagok.server.domain.copilot.dto.CopilotRequest;
import com.chagokchagok.server.domain.copilot.dto.CopilotResponse;
import com.chagokchagok.server.domain.copilot.entity.Copilot;
import com.chagokchagok.server.domain.copilot.exception.CopilotErrorCode;
import com.chagokchagok.server.domain.copilot.repository.CopilotRepository;
import com.chagokchagok.server.domain.installation.repository.InstallationRepository;
import com.chagokchagok.server.global.exception.ErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CopilotService {

    private final CopilotRepository copilots;
    private final InstallationRepository installations;
    private final EntityManager entityManager;

    @Transactional
    public CopilotResponse create(UUID installationId, CopilotRequest request) {
        if (copilots.existsById(installationId)) {
            throw new GeneralException(CopilotErrorCode.ALREADY_EXISTS);
        }
        var installation = installations.findById(installationId)
                .orElseThrow(() -> new GeneralException(ErrorCode.UNAUTHORIZED));
        Copilot copilot = new Copilot(installation, request.nickname(), request.ageGroup());
        copilot.update(request.nickname(), request.ageGroup(), request.missionIntensity());
        // Assigned UUID를 가진 엔티티에서 save()/merge()가 기존 프로필을 덮어쓰지 않도록 INSERT만 수행한다.
        try {
            entityManager.persist(copilot);
            entityManager.flush();
        } catch (RuntimeException exception) {
            if (isDuplicateCopilot(exception)) {
                throw new GeneralException(CopilotErrorCode.ALREADY_EXISTS);
            }
            throw exception;
        }
        return CopilotResponse.from(copilot);
    }

    private boolean isDuplicateCopilot(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "pk_copilots".equalsIgnoreCase(violation.getConstraintName())
                    && "23505".equals(violation.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public CopilotResponse get(UUID installationId) {
        return CopilotResponse.from(copilots.findById(installationId)
                .orElseThrow(() -> new GeneralException(CopilotErrorCode.NOT_FOUND)));
    }

    @Transactional
    public CopilotResponse update(UUID installationId, CopilotRequest request) {
        Copilot copilot = copilots.findById(installationId)
                .orElseThrow(() -> new GeneralException(CopilotErrorCode.NOT_FOUND));
        copilot.update(request.nickname(), request.ageGroup(), request.missionIntensity());
        return CopilotResponse.from(copilot);
    }
}
