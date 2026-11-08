package com.chagokchagok.server.domain.installation.dto;

import java.util.UUID;

public record InstallationRegistrationResponse(UUID installationId, String token) {
}
