# 미션팩 조회 API와 스키마 v1

`GET /api/v1/mission-packs/current?code=DEFAULT&supportedSchemaVersion=1`은 인증 없이 호출할 수 있습니다. 두 쿼리 파라미터는 생략할 수 있으며 표시한 값이 기본값입니다. 서버는 해당 코드의 발행된 미션팩 중 클라이언트와 서버가 모두 지원하는 스키마 버전 이하인 **가장 높은 콘텐츠 버전**을 반환합니다. 현재 서버가 검증·배포할 수 있는 스키마는 v1과 v2입니다. 보관된 팩과 비활성 미션은 새 다운로드에 포함되지 않습니다. 반환 미션은 코드 순으로 정렬됩니다.

성공 응답은 공통 `ApiResponse`의 `data`에 다음 값을 담습니다.

```json
{
  "code": "DEFAULT",
  "version": 1,
  "schemaVersion": 1,
  "publishedAt": "2026-11-09T00:00:00Z",
  "missions": [
    {
      "code": "BLINK",
      "triggerType": "TURN_SIGNAL_OFF",
      "config": {
        "trigger": {"event": "TURN_SIGNAL_OFF"},
        "completion": {"answerKind": "YES_NO", "expectedAnswer": "YES", "maxRetries": 1},
        "execution": {
          "validity": {"mode": "TIME", "durationMs": 30000},
          "cooldownSecondsByIntensity": {"LOW": 300, "NORMAL": 180, "HIGH": 120},
          "holdWhen": {"turnSignalOn": true, "naviSpeaking": true, "nextTurnWithinMeters": 300}
        },
        "ageContent": {
          "AGE_5_6": {"question": "...", "correctMessage": "...", "wrongMessage": "..."},
          "AGE_7_8": {"question": "...", "correctMessage": "...", "wrongMessage": "..."},
          "AGE_9_PLUS": {"question": "...", "correctMessage": "...", "wrongMessage": "..."}
        }
      }
    }
  ]
}
```

실제 응답에는 `isSuccess`, 최상위 `code`, `message`도 있습니다. 위 JSON은 `data`만 보여줍니다. 배포된 팩이 없으면 404 `MISSION_PACK_404`, 발행된 팩은 있지만 지원 버전 이하가 없으면 406 `MISSION_PACK_406`을 반환합니다. `supportedSchemaVersion`이 1 미만이거나 `code`가 비어 있으면 400입니다.

스키마 v1 원본은 [`mission-config-v1.json`](../../src/main/resources/schema/mission-config-v1.json)입니다. `trigger`는 차량·내비 이벤트와 BLINK의 30초 회고 구간, `completion`은 답변 종류와 정답 기준, `execution`은 유효 기간·강도별 간격·보류 조건·주행당 최대 횟수, `ageContent`는 연령별 음성 문구입니다. 신규 콘텐츠는 `AGE_5_6`, `AGE_7_8`, `AGE_9_PLUS` 키를 모두 포함해야 하며, `AGE_9_PLUS`는 9세 이상용 콘텐츠입니다. v1은 Android에 있는 BLINK, WIPER, NEXT_TURN, NEAR_DEST 코드만 허용합니다. LOW/NORMAL/HIGH는 Android의 RELAXED/NORMAL/ACTIVE와 각각 대응합니다. NEXT_TURN은 다음 회전까지 300m에 도달하면 폐기하고, NEAR_DEST는 남은 시간 300초 이하일 때만 발동하며 한 주행에서 한 번만 사용합니다. 남은 거리 기준은 Android에서 검증 전이라 v1에 넣지 않았습니다.

Flyway V3가 기본 팩 `DEFAULT` 버전 1과 네 미션을 발행합니다. 이미 같은 코드·버전이 있으면 내용을 덮어쓰지 않습니다. 발행 서비스는 활성 미션이 한 개 이상인지, 각 설정이 JSON Schema와 코드별 의미 규칙에 맞는지 검사합니다. Flyway V4는 발행된 콘텐츠의 DB 수정을 막고, 변경이 필요하면 새 `version`을 만들도록 합니다. 과거 주행 동기화는 당시 사용한 보관 버전을 계속 참조할 수 있습니다.

현재 Android에는 미션팩 다운로드·파싱 연결이 아직 없고 로컬 미션 정의를 실행합니다. 서버 응답을 실제 주행에 적용하려면 Android의 `missionPackSync` 구현, 스키마 버전 확인, 오프라인 캐시 및 실행 규칙 연결이 후속으로 필요합니다.

## 시연용 DEFAULT v2

Flyway V5는 기존 `DEFAULT` v1과 네 미션을 수정하지 않고 `DEFAULT` v2(`schemaVersion: 2`)에 10개를 추가합니다. `GET /api/v1/mission-packs/current?supportedSchemaVersion=2`로 조회합니다. 기본 요청과 스키마 v1 클라이언트는 계속 v1을 받습니다. v2 데이터는 [노션 미션 정리표](https://app.notion.com/p/2-4157e597ac2b80beb472ea2d57968a2d)의 추천 10칸에서 `T-07 창밖 찾기`를 빼고 `N-02 도착까지`를 넣은 것입니다. 추천표의 `N-09 도착! + T-06 안전운전 리포트`는 시연용 미션 하나(`N-09_T-06`)로 합쳤습니다.

| 코드 | 미션 | 이벤트 |
| --- | --- | --- |
| V-01 | 깜빡이 소리 | TURN_SIGNAL_OFF |
| V-02 | 깜빡이 배우기 | TURN_SIGNAL_OFF |
| V-03 | 깜빡이 방향 | TURN_SIGNAL_OFF |
| V-04 | 비 오는 날 | WIPER_ON |
| N-01 | 다음 회전 | NAVI_NEXT_TURN |
| N-02 | 도착까지 | NAVI_TRIP_INFO |
| T-01 | 부기장 수칙 | TRIP_STARTED |
| T-05 | 길 건너기 | MISSION_CORRECT |
| T-08 | 쉬어가기 | TRIP_PAUSED |
| N-09_T-06 | 도착! 안전운전 리포트 | DESTINATION_ARRIVED |

v2 스키마는 [`mission-config-v2.json`](../../src/main/resources/schema/mission-config-v2.json)입니다. `sourceIds`는 노션 원본 ID, `ageContent`는 연령별 질문·답변 방식·정답·피드백입니다. `NONE`은 음성 안내만 하고 답변을 기다리지 않습니다. `{remainingMinutes}`와 `{correctCount}`는 클라이언트가 실제 주행 값으로 바꾸는 템플릿 변수입니다. 숫자 음성 판정은 시연에서 제외해, 숫자 질문은 바로 아래 연령대의 안내나 질문으로 대체했습니다.

데이터가 배포되어 API에 보여도 Android가 v2를 내려받아 이벤트와 답변 규칙을 실행하도록 연결되기 전까지 주행 중 새 미션은 자동으로 실행되지 않습니다. 특히 `V-03` 방향값과 `N-01` 회전 정보는 SDK 필드 확인이 필요합니다. `N-02`는 내비의 남은 시간 값이 있어야 합니다.
