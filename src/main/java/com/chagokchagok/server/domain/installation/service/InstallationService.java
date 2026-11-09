package com.chagokchagok.server.domain.installation.service;

import com.chagokchagok.server.domain.installation.dto.InstallationRegistrationResponse;
import com.chagokchagok.server.domain.installation.entity.Installation;
import com.chagokchagok.server.domain.installation.repository.InstallationRepository;
import com.chagokchagok.server.global.exception.ErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstallationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final InstallationRepository installations;
    private final EntityManager entityManager;

    @Transactional
    public InstallationRegistrationResponse register() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID id = UUID.randomUUID();
        entityManager.persist(new Installation(id, sha256(token)));
        entityManager.flush();
        return new InstallationRegistrationResponse(id, token);
    }

    @Transactional
    public void revoke(UUID id) {
        Installation installation = installations.findById(id)
                .orElseThrow(() -> new GeneralException(ErrorCode.UNAUTHORIZED));
        installation.revoke();
    }

    private String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
