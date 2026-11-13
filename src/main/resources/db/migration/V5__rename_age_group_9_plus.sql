ALTER TABLE copilots DROP CONSTRAINT IF EXISTS ck_copilots_age_group;

UPDATE copilots
SET age_group = 'AGE_9_PLUS'
WHERE age_group = 'AGE_9_10';

ALTER TABLE copilots
    ADD CONSTRAINT ck_copilots_age_group
    CHECK (age_group IN ('AGE_5_6', 'AGE_7_8', 'AGE_9_PLUS'));

ALTER TABLE missions DISABLE TRIGGER trg_protect_mission_content;

UPDATE missions
SET config = CASE
    WHEN config #> '{ageContent,AGE_9_PLUS}' IS NULL THEN
        jsonb_set(
            config #- '{ageContent,AGE_9_10}',
            '{ageContent,AGE_9_PLUS}',
            config #> '{ageContent,AGE_9_10}',
            true
        )
    ELSE config #- '{ageContent,AGE_9_10}'
END
WHERE config #> '{ageContent,AGE_9_10}' IS NOT NULL;

ALTER TABLE missions ENABLE TRIGGER trg_protect_mission_content;
