package com.chagokchagok.server.domain.installation.controller;

import com.chagokchagok.server.domain.installation.dto.InstallationRegistrationResponse;
import com.chagokchagok.server.domain.installation.service.InstallationService;
import com.chagokchagok.server.domain.installation.service.RegistrationRateLimiter;
import com.chagokchagok.server.global.common.response.ApiResponse;
import com.chagokchagok.server.global.security.InstallationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/installations")
public class InstallationController {

    private final InstallationService service;
    private final RegistrationRateLimiter rateLimiter;

    @Operation(summary = "앱 설치 등록", description = "설치 토큰은 이 응답에서만 반환됩니다. 안전하게 보관하세요.")
    @SecurityRequirements
    @PostMapping
    public ResponseEntity<ApiResponse<InstallationRegistrationResponse>> register(HttpServletRequest request) {
        rateLimiter.check(request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                .body(ApiResponse.success(service.register()));
    }

    @Operation(summary = "설치 토큰 폐기", security = @SecurityRequirement(name = "Installation Token"))
    @DeleteMapping("/me")
    public ApiResponse<Void> revoke(@AuthenticationPrincipal InstallationPrincipal principal) {
        service.revoke(principal.installationId());
        return ApiResponse.success();
    }
}
