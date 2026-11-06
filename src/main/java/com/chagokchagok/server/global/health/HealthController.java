package com.chagokchagok.server.global.health;

import com.chagokchagok.server.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서버 상태 확인 API")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Operation(
        summary = "서버 상태 확인",
        description = "서버가 요청을 처리할 수 있는지 확인합니다. 인증 없이 호출할 수 있습니다."
    )
    @SecurityRequirements
    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(HealthResponse.up());
    }
}
