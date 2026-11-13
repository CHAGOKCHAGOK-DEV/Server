-- Publish the confirmed Notion shortlist as a new content version; keep DEFAULT v2 immutable.
ALTER TABLE missions DROP CONSTRAINT ck_missions_trigger_type;
ALTER TABLE missions ADD CONSTRAINT ck_missions_trigger_type CHECK (trigger_type IN (
    'TURN_SIGNAL_OFF', 'WIPER_ON', 'LOW_BEAM_ON', 'NAVI_NEXT_TURN', 'NEAR_DESTINATION',
    'NAVI_TRIP_INFO', 'TRIP_STARTED', 'MISSION_CORRECT', 'TRIP_PAUSED',
    'NO_EVENT_TIMEOUT', 'DESTINATION_ARRIVED'
));

DO $$
DECLARE
    source_pack_id BIGINT;
    new_pack_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM mission_packs WHERE code = 'DEFAULT' AND version = 3) THEN
        RAISE EXCEPTION 'DEFAULT version 3 already exists; refusing to overwrite it';
    END IF;

    SELECT id INTO source_pack_id FROM mission_packs
    WHERE code = 'DEFAULT' AND version = 2 AND schema_version = 2
      AND status IN ('PUBLISHED', 'ARCHIVED');
    IF source_pack_id IS NULL
       OR (SELECT count(*) FROM missions WHERE mission_pack_id = source_pack_id AND enabled) <> 10
       OR NOT EXISTS (SELECT 1 FROM missions WHERE mission_pack_id = source_pack_id AND code = 'N-02')
       OR EXISTS (SELECT 1 FROM missions WHERE mission_pack_id = source_pack_id AND code = 'T-07') THEN
        RAISE EXCEPTION 'DEFAULT version 2 is not the expected 10-mission source pack';
    END IF;

    INSERT INTO mission_packs (code, version, schema_version, status)
    VALUES ('DEFAULT', 3, 2, 'DRAFT') RETURNING id INTO new_pack_id;

    INSERT INTO missions (mission_pack_id, code, trigger_type, config, enabled)
    SELECT new_pack_id, code, trigger_type, config, enabled
    FROM missions WHERE mission_pack_id = source_pack_id AND code <> 'N-02';

    INSERT INTO missions (mission_pack_id, code, trigger_type, config) VALUES
    (new_pack_id, 'T-07', 'NO_EVENT_TIMEOUT', $config$
{"title":"창밖 찾기","sourceIds":["T-07"],"trigger":{"event":"NO_EVENT_TIMEOUT","noEventMinutes":20,"straightRoadRequired":true},"execution":{"cooldownSecondsByIntensity":{"LOW":1200,"NORMAL":1200,"HIGH":1200},"holdWhen":{"turnSignalOn":true,"naviSpeaking":true,"nextTurnWithinMeters":300},"validity":{"mode":"TIME","durationMs":60000}},"ageContent":{"AGE_5_6":{"question":"창밖에 빨간 차를 찾아봐요! 찾으면 '찾았다!' 하고 외쳐요","answerKind":"CHOICE","expectedAnswer":"찾았다","correctMessage":"찾았군요! 잘 봤어요.","wrongMessage":"괜찮아요, 함께 찾아봐요!"},"AGE_7_8":{"question":"창밖에서 노란 차를 찾아봐요! 찾으면 '찾았다!' 하고 외쳐요","answerKind":"CHOICE","expectedAnswer":"찾았다","correctMessage":"찾았군요! 잘 봤어요.","wrongMessage":"괜찮아요, 함께 찾아봐요!"},"AGE_9_PLUS":{"question":"다음 1분 동안 창밖에서 트럭을 찾아봐요! 찾으면 '찾았다!' 하고 말해줘요","answerKind":"FREE","expectedAnswer":"ANY_SPOKEN","correctMessage":"잘 찾았어요!","wrongMessage":"괜찮아요, 함께 찾아봐요!"}}}$config$::jsonb);

    UPDATE mission_packs SET status = 'PUBLISHED', published_at = now() WHERE id = new_pack_id;
END $$;
