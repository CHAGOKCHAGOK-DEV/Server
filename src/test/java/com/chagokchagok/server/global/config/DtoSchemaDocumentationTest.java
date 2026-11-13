package com.chagokchagok.server.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DtoSchemaDocumentationTest {

    private static final List<String> DTO_SCHEMAS = List.of(
            "AgreementConsentRequest",
            "AgreementConsentEvent",
            "AgreementConsentResponse",
            "AgreementConsent",
            "AgreementStatusResponse",
            "AgreementStatusDocument",
            "AgreementCurrentResponse",
            "AgreementCurrentDocument",
            "InstallationRegistrationResponse",
            "CopilotRequest",
            "CopilotResponse",
            "DailyMissionProgressResponse",
            "MissionPackResponse",
            "MissionResponse",
            "TripSyncRequest",
            "TripSyncResult",
            "TripSyncResponse",
            "TripResultSummary",
            "TripMissionResultItem",
            "TripHistoryPageResponse",
            "TripHistoryItem",
            "TripDetailResponse"
    );

    @Autowired MockMvc mvc;

    @Test
    void exposesDescriptionsForAllDtoSchemas() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Map<String, Object>> schemas = JsonPath.read(body, "$.components.schemas");

        for (String schemaName : DTO_SCHEMAS) {
            assertThat(schemas)
                    .as("OpenAPI schema %s", schemaName)
                    .containsKey(schemaName);
            assertThat(schemas.get(schemaName).get("description"))
                    .as("OpenAPI schema description %s", schemaName)
                    .isInstanceOf(String.class)
                    .asString()
                    .isNotBlank();
        }
        Map<String, Map<String, Object>> properties = JsonPath.read(
                body, "$.components.schemas.AgreementStatusDocument.properties");
        assertThat(properties.get("state").get("description"))
                .isEqualTo("현재 약관 문서에 대한 동의 상태");
    }
}
