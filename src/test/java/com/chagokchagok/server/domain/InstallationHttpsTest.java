package com.chagokchagok.server.domain;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "installation.require-https=true",
        "server.forward-headers-strategy=framework"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InstallationHttpsTest {

    @Autowired MockMvc mvc;

    @Test
    void registrationRequiresHttpsWhenEnabled() throws Exception {
        mvc.perform(post("/api/v1/installations"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSTALLATION_403"));
        mvc.perform(post("/api/v1/installations").secure(true))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/copilot"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSTALLATION_403"));
        mvc.perform(get("/api/v1/copilot").secure(true))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forwardedHttpsIsAcceptedAndOtherInstallationApisStayProtected() throws Exception {
        mvc.perform(post("/api/v1/installations").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/trips"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSTALLATION_403"));
        mvc.perform(get("/api/v1/trips").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/agreements/current"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSTALLATION_403"));
        mvc.perform(get("/api/v1/missions/daily-progress"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSTALLATION_403"));
    }
}
