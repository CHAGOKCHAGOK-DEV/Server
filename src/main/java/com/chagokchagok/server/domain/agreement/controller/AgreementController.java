package com.chagokchagok.server.domain.agreement.controller;

import com.chagokchagok.server.domain.agreement.dto.AgreementConsentRequest;
import com.chagokchagok.server.domain.agreement.dto.AgreementConsentResponse;
import com.chagokchagok.server.domain.agreement.dto.AgreementCurrentResponse;
import com.chagokchagok.server.domain.agreement.dto.AgreementStatusResponse;
import com.chagokchagok.server.domain.agreement.service.AgreementService;
import com.chagokchagok.server.global.common.response.ApiResponse;
import com.chagokchagok.server.global.exception.ErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import com.chagokchagok.server.global.security.InstallationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AgreementController {

    private final AgreementService service;

    @Operation(summary = "현재 적용 중인 동의 문서 조회",
            description = "설치 토큰과 X-Installation-Id 헤더가 필요합니다. 문서 코드별로 적용 시각이 지난 최신 PUBLISHED 버전을 반환합니다.",
            security = @SecurityRequirement(name = "Installation Token"),
            parameters = @Parameter(name = "X-Installation-Id", in = ParameterIn.HEADER, required = true,
                    description = "인증할 설치 UUID", schema = @Schema(type = "string", format = "uuid")))
    @GetMapping("/api/v1/agreements/current")
    public ApiResponse<AgreementCurrentResponse> current(@AuthenticationPrincipal InstallationPrincipal principal) {
        requireInstallation(principal);
        return ApiResponse.success(service.current());
    }

    @Operation(summary = "설치의 현재 문서 동의 상태 조회",
            description = "설치 토큰과 X-Installation-Id 헤더가 필요합니다. 필수 현재 문서 중 현재 버전에 동의하지 않은 문서가 있으면 consentRequired가 true입니다.",
            security = @SecurityRequirement(name = "Installation Token"),
            parameters = @Parameter(name = "X-Installation-Id", in = ParameterIn.HEADER, required = true,
                    description = "인증할 설치 UUID", schema = @Schema(type = "string", format = "uuid")))
    @GetMapping("/api/v1/agreements/status")
    public ApiResponse<AgreementStatusResponse> status(@AuthenticationPrincipal InstallationPrincipal principal) {
        requireInstallation(principal);
        return ApiResponse.success(service.status(principal.installationId()));
    }

    @Operation(summary = "동의 이벤트 기록",
            description = "설치 토큰과 X-Installation-Id 헤더가 필요합니다. 요청 전체를 하나의 트랜잭션으로 저장하며 동일 재전송은 최초 기록을 반환합니다.",
            security = @SecurityRequirement(name = "Installation Token"),
            parameters = @Parameter(name = "X-Installation-Id", in = ParameterIn.HEADER, required = true,
                    description = "인증할 설치 UUID", schema = @Schema(type = "string", format = "uuid")))
    @PostMapping("/api/v1/agreements/consents")
    public ApiResponse<AgreementConsentResponse> record(
            @AuthenticationPrincipal InstallationPrincipal principal,
            @Valid @RequestBody AgreementConsentRequest request
    ) {
        requireInstallation(principal);
        return ApiResponse.success(service.record(principal.installationId(), request));
    }

    private void requireInstallation(InstallationPrincipal principal) {
        if (principal == null) {
            throw new GeneralException(ErrorCode.UNAUTHORIZED);
        }
    }
}
