package com.chagokchagok.server.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.chagokchagok.server.global.security.InstallationPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class InstallationAuthorizationTest {

    private final RequestAuthorizationContext context = new RequestAuthorizationContext(new MockHttpServletRequest());

    @Test
    void grantsOnlyInstallationPrincipal() {
        assertThat(granted(new UsernamePasswordAuthenticationToken(
                new InstallationPrincipal(UUID.randomUUID()), null, List.of()))).isTrue();
        assertThat(granted(new UsernamePasswordAuthenticationToken(42L, null, List.of()))).isFalse();
        assertThat(granted(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")))).isFalse();
        assertThat(granted(null)).isFalse();
    }

    private boolean granted(Authentication authentication) {
        return SecurityConfig.INSTALLATION_ONLY.authorize(() -> authentication, context).isGranted();
    }
}
