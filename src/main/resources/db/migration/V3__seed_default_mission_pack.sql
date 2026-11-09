-- The first published pack mirrors the four mission codes and timing rules in the Android client.
-- An existing DEFAULT v1 pack is left untouched, including its status and content.
DO $$
DECLARE
    pack_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM mission_packs WHERE code = 'DEFAULT' AND version = 1) THEN
        RETURN;
    END IF;

    INSERT INTO mission_packs (code, version, schema_version, status)
    VALUES ('DEFAULT', 1, 1, 'DRAFT')
    RETURNING id INTO pack_id;

    INSERT INTO missions (mission_pack_id, code, trigger_type, config) VALUES
    (pack_id, 'BLINK', 'TURN_SIGNAL_OFF', $json$
    {
      "trigger":{"event":"TURN_SIGNAL_OFF","lookbackMs":30000},
      "completion":{"answerKind":"YES_NO","expectedAnswer":"YES","maxRetries":1},
      "execution":{"validity":{"mode":"TIME","durationMs":30000},"cooldownSecondsByIntensity":{"LOW":300,"NORMAL":180,"HIGH":120},"holdWhen":{"turnSignalOn":true,"naviSpeaking":true,"nextTurnWithinMeters":300}},
      "ageContent":{
        "AGE_5_6":{"question":"부기장님! 기장님이 깜빡이를 켰나요?","correctMessage":"정확해요! 기장님이 깜빡이를 켰어요.","wrongMessage":"앗, 기장님이 켰어요. 다음엔 더 잘 볼 수 있어요!"},
        "AGE_7_8":{"question":"기장님이 방향을 바꿀 때 깜빡이를 켰나요?","correctMessage":"맞아요! 깜빡이를 켰어요.","wrongMessage":"깜빡이를 켰어요. 다음엔 더 잘 관찰해 봐요!"},
        "AGE_9_10":{"question":"방금 기장님이 방향지시등을 사용했나요?","correctMessage":"정답이에요! 방향지시등을 켰어요.","wrongMessage":"방향지시등을 켰어요. 다음 기회에 다시 살펴봐요!"}
      }
    }
    $json$::jsonb),
    (pack_id, 'WIPER', 'WIPER_ON', $json$
    {
      "trigger":{"event":"WIPER_ON"},
      "completion":{"answerKind":"YES_NO","expectedAnswer":"YES","maxRetries":1},
      "execution":{"validity":{"mode":"TIME","durationMs":30000},"cooldownSecondsByIntensity":{"LOW":300,"NORMAL":180,"HIGH":120},"holdWhen":{"turnSignalOn":true,"naviSpeaking":true,"nextTurnWithinMeters":300}},
      "ageContent":{
        "AGE_5_6":{"question":"비가 오나 봐요! 와이퍼가 켜졌나요?","correctMessage":"맞아요! 비 오는 날엔 천천히 가요.","wrongMessage":"맞아요! 비 오는 날엔 천천히 가요."},
        "AGE_7_8":{"question":"앞유리의 빗물을 닦는 와이퍼가 켜졌나요?","correctMessage":"맞아요! 비 오는 날엔 천천히 가요.","wrongMessage":"와이퍼가 켜졌어요. 비 오는 날엔 천천히 가요."},
        "AGE_9_10":{"question":"지금 앞유리를 닦는 와이퍼가 작동하나요?","correctMessage":"정확해요! 비 오는 날엔 천천히 가요.","wrongMessage":"와이퍼가 작동해요. 비 오는 날엔 천천히 가요."}
      }
    }
    $json$::jsonb),
    (pack_id, 'NEXT_TURN', 'NAVI_NEXT_TURN', $json$
    {
      "trigger":{"event":"NAVI_NEXT_TURN"},
      "completion":{"answerKind":"LEFT_RIGHT","expectedAnswer":"NAVI_DIRECTION","maxRetries":1},
      "execution":{"validity":{"mode":"NAVIGATION_DISTANCE","expireAtDistanceMeters":300},"cooldownSecondsByIntensity":{"LOW":300,"NORMAL":180,"HIGH":120},"holdWhen":{"turnSignalOn":true,"naviSpeaking":true}},
      "ageContent":{
        "AGE_5_6":{"question":"다음엔 왼쪽일까요, 오른쪽일까요?","correctMessage":"맞혔어요! 곧 만나요.","wrongMessage":"곧 알 수 있어요, 같이 봐요!"},
        "AGE_7_8":{"question":"다음 길에서는 왼쪽과 오른쪽 중 어디로 갈까요?","correctMessage":"맞혔어요! 곧 만나요.","wrongMessage":"곧 알 수 있어요, 같이 봐요!"},
        "AGE_9_10":{"question":"내비게이션의 다음 회전 방향은 왼쪽인가요, 오른쪽인가요?","correctMessage":"정답이에요! 방향을 잘 봤어요.","wrongMessage":"곧 알 수 있어요. 다음 방향을 함께 살펴봐요!"}
      }
    }
    $json$::jsonb),
    (pack_id, 'NEAR_DEST', 'NEAR_DESTINATION', $json$
    {
      "trigger":{"event":"NEAR_DESTINATION","etaSecondsAtMost":300},
      "completion":{"answerKind":"FREE","expectedAnswer":"ANY_SPOKEN","maxRetries":1},
      "execution":{"validity":{"mode":"ROUTE_ACTIVE"},"cooldownSecondsByIntensity":{"LOW":300,"NORMAL":180,"HIGH":120},"maxPerTrip":1,"holdWhen":{"turnSignalOn":true,"naviSpeaking":true,"nextTurnWithinMeters":300}},
      "ageContent":{
        "AGE_5_6":{"question":"곧 도착이에요! 도착하면 기장님께 뭐라고 말할까요?","correctMessage":"멋진 말이에요! 기장님이 기뻐할 거예요.","wrongMessage":"괜찮아요, 도착하면 같이 말해 봐요!"},
        "AGE_7_8":{"question":"목적지에 거의 다 왔어요. 기장님께 어떤 말을 건넬까요?","correctMessage":"좋은 말이에요! 기장님이 기뻐할 거예요.","wrongMessage":"괜찮아요, 도착하면 같이 이야기해 봐요!"},
        "AGE_9_10":{"question":"곧 도착합니다. 운전한 기장님께 어떤 말을 전하고 싶나요?","correctMessage":"멋진 말이에요! 기장님이 기뻐할 거예요.","wrongMessage":"괜찮아요, 도착하면 기장님께 직접 말해 봐요!"}
      }
    }
    $json$::jsonb);

    UPDATE mission_packs SET status = 'PUBLISHED', published_at = now() WHERE id = pack_id;
END $$;
