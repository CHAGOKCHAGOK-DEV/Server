package com.chagokchagok.server.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CoreDomainMigrationTest {

    @Test
    void migrationContainsRequiredTablesAndConstraints() throws IOException {
        String migration = new ClassPathResource("db/migration/V1__create_core_domain.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("CREATE TABLE installations")
                .contains("CREATE TABLE copilots")
                .contains("CREATE TABLE mission_packs")
                .contains("CREATE TABLE missions")
                .contains("CREATE TABLE trips")
                .contains("CREATE TABLE mission_results")
                .contains("CREATE TABLE agreement_documents")
                .contains("CREATE TABLE agreement_histories")
                .contains("CONSTRAINT uk_agreement_documents_code_version UNIQUE (code, version)")
                .contains("CONSTRAINT ck_agreement_documents_version CHECK (version > 0)")
                .contains("status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')")
                .contains("CONSTRAINT fk_agreement_histories_installation FOREIGN KEY (installation_id) REFERENCES installations (id)")
                .contains("CONSTRAINT fk_agreement_histories_document FOREIGN KEY (agreement_document_id) REFERENCES agreement_documents (id)")
                .contains("action IN ('ACCEPTED', 'WITHDRAWN')")
                .contains("ON agreement_histories (installation_id, created_at DESC)")
                .contains("ON agreement_histories (agreement_document_id)")
                .contains("age_group IN ('AGE_5_6', 'AGE_7_8', 'AGE_9_10')")
                .contains("CONSTRAINT uk_mission_packs_code_version UNIQUE (code, version)")
                .contains("CONSTRAINT uk_missions_pack_code UNIQUE (mission_pack_id, code)")
                .contains("CONSTRAINT ck_missions_config_object CHECK (jsonb_typeof(config) = 'object')")
                .contains("CONSTRAINT ck_trips_period CHECK (ended_at >= started_at)")
                .contains("CONSTRAINT ck_mission_results_period CHECK (ended_at >= started_at)");
    }
}
