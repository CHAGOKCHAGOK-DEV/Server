package com.chagokchagok.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chagokchagok.server.domain.agreement.service.AgreementConsentGuard;
import com.chagokchagok.server.domain.installation.repository.InstallationRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InstallationCopilotApiTest {

    @Autowired MockMvc mvc;
    @Autowired InstallationRepository installations;
    @MockitoBean AgreementConsentGuard agreementConsentGuard;

    @Test
    void registrationProfileIsolationAndRevocation() throws Exception {
        Credential first = register();
        Credential second = register();
        assertThat(first.token()).isNotEqualTo(second.token());
        assertThat(installations.findById(first.id()).orElseThrow().getTokenHash())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(first.token().getBytes(StandardCharsets.UTF_8))));

        mvc.perform(get("/api/v1/copilot").header("X-Installation-Id", first.id())
                .header("Authorization", "Bearer " + first.token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COPILOT_404"));
        mvc.perform(post("/api/v1/copilot").header("X-Installation-Id", first.id())
                .header("Authorization", "Bearer " + first.token()).contentType(MediaType.APPLICATION_JSON)
                .content(profile("은우", "AGE_7_8", "NORMAL")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.nickname").value("은우"));
        mvc.perform(post("/api/v1/copilot").header("X-Installation-Id", first.id())
                .header("Authorization", "Bearer " + first.token()).contentType(MediaType.APPLICATION_JSON)
                .content(profile("다른아이", "AGE_5_6", "LOW")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPILOT_409"));

        mvc.perform(get("/api/v1/copilot").header("X-Installation-Id", second.id())
                .header("Authorization", "Bearer " + second.token()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/copilot").header("X-Installation-Id", first.id())
                .header("Authorization", "Bearer " + first.token()).contentType(MediaType.APPLICATION_JSON)
                .content(profile("새이름", "AGE_9_PLUS", "HIGH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ageGroup").value("AGE_9_PLUS"))
                .andExpect(jsonPath("$.data.missionIntensity").value("HIGH"));
        mvc.perform(get("/api/v1/copilot").header("X-Installation-Id", first.id())
                .header("Authorization", "Bearer " + first.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("새이름"));

        mvc.perform(delete("/api/v1/installations/me").header("X-Installation-Id", first.id())
                .header("Authorization", "Bearer " + first.token())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/copilot").header("X-Installation-Id", first.id())
                .header("Authorization", "Bearer " + first.token())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/copilot").header("X-Installation-Id", second.id())
                .header("Authorization", "Bearer " + second.token())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingOrWrongCredentialsAndInvalidProfile() throws Exception {
        Credential credential = register();
        mvc.perform(get("/api/v1/copilot")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/copilot").header("X-Installation-Id", credential.id())
                .header("Authorization", "Bearer wrong-token")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/copilot").header("X-Installation-Id", credential.id())
                .header("Authorization", "Bearer " + credential.token()).contentType(MediaType.APPLICATION_JSON)
                .content(profile("bad name", "AGE_7_8", "NORMAL")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
        mvc.perform(post("/api/v1/copilot").header("X-Installation-Id", credential.id())
                .header("Authorization", "Bearer " + credential.token()).contentType(MediaType.APPLICATION_JSON)
                .content(profile("부기장", "AGE_9_10", "NORMAL")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400_1"));
    }

    private Credential register() throws Exception {
        String body = mvc.perform(post("/api/v1/installations"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.installationId").exists())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn().getResponse().getContentAsString();
        return new Credential(UUID.fromString(JsonPath.read(body, "$.data.installationId")),
                JsonPath.read(body, "$.data.token"));
    }

    private String profile(String nickname, String ageGroup, String intensity) {
        return "{\"nickname\":\"" + nickname + "\",\"ageGroup\":\"" + ageGroup
                + "\",\"missionIntensity\":\"" + intensity + "\"}";
    }

    private record Credential(UUID id, String token) {}
}
