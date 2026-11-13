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

    @Test
    void productionAgreementMigrationContainsFinalDocumentsWithoutChangingTheirContent() throws IOException {
        String migration = new ClassPathResource("db/migration/V7__seed_production_agreement_documents.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("('TERMS_OF_SERVICE', 1, '꼬마 부기장 서비스 이용약관'")
                .contains("('PRIVACY_POLICY', 1, '꼬마 부기장 개인정보 처리방침'")
                .contains("('CHILD_PRIVACY_COLLECTION', 1, '꼬마 부기장 아동 개인정보 수집·이용 및 법정대리인 동의서'")
                .contains("$terms$# 꼬마 부기장 서비스 이용약관")
                .contains("$terms$, TRUE, 'PUBLISHED'")
                .contains("$privacy$# 꼬마 부기장 개인정보 처리방침")
                .contains("$privacy$, FALSE, 'PUBLISHED'")
                .contains("$child$# 꼬마 부기장 아동 개인정보 수집·이용 및 법정대리인 동의서")
                .contains("$child$, TRUE, 'PUBLISHED'")
                .contains("TIMESTAMPTZ '2026-11-15 00:00:00+09'")
                .doesNotContain("ON CONFLICT");
    }

    @Test
    void ageGroupMigrationUpdatesProfilesAndMissionContent() throws IOException {
        String migration = new ClassPathResource("db/migration/V5__rename_age_group_9_plus.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("SET age_group = 'AGE_9_PLUS'")
                .contains("WHERE age_group = 'AGE_9_10'")
                .contains("age_group IN ('AGE_5_6', 'AGE_7_8', 'AGE_9_PLUS')")
                .contains("config #- '{ageContent,AGE_9_10}'")
                .contains("'{ageContent,AGE_9_PLUS}'");
    }
}
