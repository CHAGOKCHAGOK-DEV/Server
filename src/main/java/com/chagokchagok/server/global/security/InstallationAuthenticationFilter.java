package com.chagokchagok.server.global.security;

import com.chagokchagok.server.domain.installation.entity.InstallationStatus;
import com.chagokchagok.server.domain.installation.repository.InstallationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class InstallationAuthenticationFilter extends OncePerRequestFilter {

    private final InstallationRepository installations;
    private final AuthenticationEntryPoint entryPoint;
    private final RequestMatcher target;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !target.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        var installation = authenticate(request);
        if (installation == null) {
            entryPoint.commence(request, response, new BadCredentialsException("Invalid installation credentials"));
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(installation, null, List.of()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private InstallationPrincipal authenticate(HttpServletRequest request) {
        String id = request.getHeader("X-Installation-Id");
        String authorization = request.getHeader("Authorization");
        if (id == null || authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7);
        if (token.isBlank() || token.length() > 512) {
            return null;
        }
        UUID installationId;
        try {
            installationId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        String expectedHash = hash;
        return installations.findById(installationId)
                .filter(value -> value.getStatus() == InstallationStatus.ACTIVE)
                .filter(value -> MessageDigest.isEqual(value.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                        expectedHash.getBytes(StandardCharsets.US_ASCII)))
                .map(value -> new InstallationPrincipal(value.getId())).orElse(null);
    }
}
