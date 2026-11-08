package com.chagokchagok.server.domain.copilot.controller;

import com.chagokchagok.server.domain.copilot.dto.CopilotRequest;
import com.chagokchagok.server.domain.copilot.dto.CopilotResponse;
import com.chagokchagok.server.domain.copilot.service.CopilotService;
import com.chagokchagok.server.global.common.response.ApiResponse;
import com.chagokchagok.server.global.security.InstallationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/copilot")
@SecurityRequirement(name = "Installation Token")
public class CopilotController {

    private final CopilotService service;

    @Operation(summary = "부기장 프로필 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<CopilotResponse>> create(
            @AuthenticationPrincipal InstallationPrincipal principal, @Valid @RequestBody CopilotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(principal.installationId(), request)));
    }

    @Operation(summary = "부기장 프로필 조회")
    @GetMapping
    public ApiResponse<CopilotResponse> get(@AuthenticationPrincipal InstallationPrincipal principal) {
        return ApiResponse.success(service.get(principal.installationId()));
    }

    @Operation(summary = "부기장 프로필 수정")
    @PutMapping
    public ApiResponse<CopilotResponse> update(
            @AuthenticationPrincipal InstallationPrincipal principal, @Valid @RequestBody CopilotRequest request) {
        return ApiResponse.success(service.update(principal.installationId(), request));
    }
}
