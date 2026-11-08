package com.chagokchagok.server.global.config;

import com.chagokchagok.server.domain.installation.repository.InstallationRepository;
import com.chagokchagok.server.global.security.InstallationAuthenticationFilter;
import com.chagokchagok.server.global.security.InstallationPrincipal;
import com.chagokchagok.server.global.security.jwt.JwtAccessDeniedHandler;
import com.chagokchagok.server.global.security.jwt.JwtAuthenticationEntryPoint;
import com.chagokchagok.server.global.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    private static final String[] PUBLIC_ENDPOINTS = {
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/actuator/health",
        "/api/v1/health"
    };

    // 인코딩 경로 우회 방지를 위해 MVC와 같은 디코딩 경로 기준 매칭
    private static final RequestMatcher TRIP_SYNC = PathPatternRequestMatcher.pathPattern("/api/v1/trips/sync");

    // 설치 필터 변경이나 순서 오류 시에도 설치 주체만 허용
    static final AuthorizationManager<RequestAuthorizationContext> INSTALLATION_ONLY = (authentication, context) -> {
        var current = authentication.get();
        return new AuthorizationDecision(current != null && current.getPrincipal() instanceof InstallationPrincipal);
    };

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
        JwtAccessDeniedHandler jwtAccessDeniedHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, InstallationRepository installations) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .formLogin(formLogin -> formLogin.disable())
            .httpBasic(httpBasic -> httpBasic.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(TRIP_SYNC).access(INSTALLATION_ONLY)
                .anyRequest().authenticated()
            )

            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            )
            .addFilterAfter(
                new InstallationAuthenticationFilter(installations, jwtAuthenticationEntryPoint, TRIP_SYNC),
                JwtAuthenticationFilter.class
            )

            .build();
    }
}
