package com.chagokchagok.server.domain.installation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "InstallationRegistrationResponse", description = "설치 등록 응답")
public record InstallationRegistrationResponse(UUID installationId, String token) {
}
