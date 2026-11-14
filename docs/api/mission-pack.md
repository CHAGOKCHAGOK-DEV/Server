# 미션팩 조회 API와 스키마 v1

`GET /api/v1/mission-packs/current?code=DEFAULT&supportedSchemaVersion=1`은 인증 없이 호출할 수 있습니다. 두 쿼리 파라미터는 생략할 수 있으며 표시한 값이 기본값입니다. 서버는 해당 코드의 발행된 미션팩 중 클라이언트와 서버가 모두 지원하는 스키마 버전 이하인 **가장 높은 콘텐츠 버전**을 반환합니다. 현재 서버가 검증·배포할 수 있는 스키마는 v1, v2, v3입니다. 보관된 팩과 비활성 미션은 새 다운로드에 포함되지 않습니다. 반환 미션은 코드 순으로 정렬됩니다.

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

2026-11-15 확인한 Android에는 미션팩 다운로드·캐시가 구현되어 있지만 `MissionDefinitions`는 로컬 미션 정의를 실행합니다. `MissionPackRepository.cached()`에도 엔진 연결 TODO가 남아 있습니다. 서버 응답을 실제 주행에 적용하려면 지원 스키마 버전 변경 및 캐시된 설정과 실행 엔진 연결이 필요합니다.

## 시연용 DEFAULT v2

Flyway V6는 기존 `DEFAULT` v1과 네 미션을 수정하지 않고 `DEFAULT` v2(`schemaVersion: 2`)에 10개를 추가했습니다. 당시 추천 10칸에서 `T-07 창밖 찾기`를 빼고 `N-02 도착까지`를 넣었으나, 이후 추천 10개가 확정되어 아래 v3에서 정정했습니다. v2 행은 과거 버전으로 보존됩니다. 추천표의 `N-09 도착! + T-06 안전운전 리포트`는 시연용 미션 하나(`N-09_T-06`)로 합쳤습니다.

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

## 확정 시연용 DEFAULT v3

Flyway V8은 발행된 v2를 변경하지 않고 새 콘텐츠 버전 `DEFAULT` v3(`schemaVersion: 2`)을 발행합니다. [노션의 확정 추천 10개](https://app.notion.com/p/2-4157e597ac2b80beb472ea2d57968a2d)에 맞춰 `N-02 도착까지`를 제외하고 `T-07 창밖 찾기`를 포함합니다. `GET /api/v1/mission-packs/current?supportedSchemaVersion=2`는 v3의 10개를 반환하며, 기본 요청과 스키마 v1 클라이언트는 계속 v1의 네 미션을 받습니다.

| 코드 | 미션 | 이벤트 |
| --- | --- | --- |
| V-01 | 깜빡이 소리 | TURN_SIGNAL_OFF |
| V-02 | 깜빡이 배우기 | TURN_SIGNAL_OFF |
| V-03 | 깜빡이 방향 | TURN_SIGNAL_OFF |
| V-04 | 비 오는 날 | WIPER_ON |
| N-01 | 다음 회전 | NAVI_NEXT_TURN |
| T-01 | 부기장 수칙 | TRIP_STARTED |
| T-05 | 길 건너기 | MISSION_CORRECT |
| T-07 | 창밖 찾기 | NO_EVENT_TIMEOUT |
| T-08 | 쉬어가기 | TRIP_PAUSED |
| N-09_T-06 | 도착! 안전운전 리포트 | DESTINATION_ARRIVED |

`T-07`은 앱 내부에서 다른 이벤트 없이 20분이 지나고 직진 대기 중일 때 사용하는 `NO_EVENT_TIMEOUT` 이벤트입니다. 5~6세는 빨간 차, 7~8세는 노란 차를 찾고 “찾았다”라고 말합니다. 9세 이상은 트럭 찾기에 참여하며 숫자를 세어 정답을 판정하지 않습니다. Android가 이 신규 이벤트와 v2 미션팩을 처리하도록 연결해야 실제 주행에서 실행됩니다.

v2 스키마는 [`mission-config-v2.json`](../../src/main/resources/schema/mission-config-v2.json)입니다. `sourceIds`는 노션 원본 ID, `ageContent`는 연령별 질문·답변 방식·정답·피드백입니다. `NONE`은 음성 안내만 하고 답변을 기다리지 않습니다. `{remainingMinutes}`와 `{correctCount}`는 클라이언트가 실제 주행 값으로 바꾸는 템플릿 변수입니다. 숫자 음성 판정은 시연에서 제외해, 숫자 질문은 바로 아래 연령대의 안내나 질문으로 대체했습니다.

데이터가 배포되어 API에 보여도 Android가 v2를 내려받아 이벤트와 답변 규칙을 실행하도록 연결되기 전까지 주행 중 새 미션은 자동으로 실행되지 않습니다. 특히 `V-03` 방향값과 `N-01` 회전 정보는 SDK 필드 확인이 필요합니다. `N-02`는 내비의 남은 시간 값이 있어야 합니다.

## 연령대별 13종 DEFAULT v4

Flyway V10은 기존 v1~v3을 유지하고 DEFAULT v4(schemaVersion 3)를 발행합니다. 신규 응답 형식과 이벤트는 기존 스키마와 구분합니다. supportedSchemaVersion=3 이상 요청은 v4의 13개, 2는 v3의 10개, 기본값 1은 v1의 4개를 받습니다. 새 스키마는 [mission-config-v3.json](../../src/main/resources/schema/mission-config-v3.json)입니다.

| 코드 | 미션 | 이벤트 |
| --- | --- | --- |
| BLINK | 깜빡이 소리 | TURN_SIGNAL_OFF |
| BLINK_LEARN | 깜빡이 배우기 | MISSION_COMPLETED |
| WIPER | 비 오는 날 | WIPER_ON |
| RAIN_STOP | 비가 그쳤어요 | WIPER_OFF |
| NEXT_TURN | 다음 회전 | NAVI_NEXT_TURN |
| SPEED_GUESS | 빠를까 느릴까 | SPEED_STABLE |
| SIGNAL_WAIT | 신호 대기 | VEHICLE_STOPPED |
| DEPART | 부기장 출동 | TRIP_STARTED |
| BLINK_MEMORY | 깜빡이 기억 게임 | TURN_SIGNAL_SUMMARY |
| WAIT_BET | 기다리기 내기 | VEHICLE_STOPPED |
| SPEED_CHANGE | 빨라졌을까 느려졌을까 | SPEED_CHANGED |
| NEAR_DEST | 곧 도착 | NEAR_DESTINATION |
| TODAY_REPORT | 오늘의 리포트 | TRIP_ENDED |

서버는 설정을 저장·제공하고, 차량 신호·트리거·음성 인식·정답 판정·재질문·리포트 집계는 클라이언트가 처리합니다. 서버의 JSON Schema 검증은 발행 데이터의 형식 확인이며 아이의 답변을 판정하는 기능이 아닙니다. 주행 동기화 및 집계 로직은 이번 변경에서 수정하지 않습니다. 연령대 저장·조회는 기존 부기장 API의 AGE_5_6, AGE_7_8, AGE_9_PLUS를 그대로 사용합니다.

answerKind는 Android AnswerKind의 YES_NO, LEFT_RIGHT, FREE와 BACKCAR_SKY, BEFORE_AFTER, WIPER_LIGHT, STOPPED_POURING, FAST_SLOW, STOP_GO, GREEN_RED, SOON_LONG을 사용합니다. 고정 선택형 정답 OPTION_A는 첫 선택지입니다. expectedAnswer의 TURN_SIGNAL_DIRECTION, WIPER_AT_RESPONSE, NAVI_DIRECTION, SPEED_THRESHOLD, TURN_SIGNAL_COUNTS, STOP_DURATION, SPEED_HISTORY는 클라이언트가 실제 신호로 계산하는 정답 출처를 나타냅니다. FREE의 ANY_SPOKEN은 참여 응답입니다. TODAY_REPORT는 presentationOnly=true, answerKind=NONE으로 발표만 하며 별도 MissionResult를 저장하지 않습니다. Android 로컬 리포트는 FREE 스크립트를 사용하지만 엔진이 질문·판정·저장 경로를 거치지 않으므로 서버 설정은 NONE으로 명시합니다.

execution.askWhen은 DRIVING/STOPPED/TRIP_END를 구분하고, validity는 시간·내비 거리·정차 상태·재출발·주행 종료 유효성을 나타냅니다. WAIT_BET은 정차 시작부터 30초 이내 재출발이면 첫 선택지를 정답으로 보고, 주차 또는 600초 초과 시 발표를 버립니다. DEPART와 NEAR_DEST의 noAnswerCompletes=true는 현재 Android와 동일합니다. 쿨다운은 기존 서버 LOW/NORMAL/HIGH 설정을 유지하되 SPEED_GUESS는 모두 900초입니다.

속도 기준은 5~8세 60km/h, 9세 이상 50km/h입니다. SPEED_CHANGE는 현재 Android 판정에 맞춰 모든 연령의 comparisonSeconds=20, 변화량 15km/h 이상으로 제공합니다. 기획의 '15km/h 초과', 9세 이상 질문의 '1분 전'과 실제 클라이언트 판정에 차이가 있으므로 질문 또는 앱 판정 기준을 팀에서 확정해야 합니다. 서버가 별도로 정답을 재계산하지 않습니다. {side}, {speed}, {more}, {less}, {other}, {sec}, {answer}, {change}, {diff}, {n}, {m}, {best}는 Android에서 실제 값으로 치환합니다.

현재 로컬 Client의 MissionPackRepository.SUPPORTED_SCHEMA_VERSION은 1이고 다운로드된 팩의 엔진 적용은 TODO입니다. 따라서 서버 변경만으로 앱 실행 미션이 바뀌지는 않습니다. 앱은 이미 로컬 13종을 실행하며, 서버 팩을 사용하려면 schema 3 선택과 설정 적용을 별도 연결해야 합니다. Client 저장소는 이번 작업에서 수정하지 않았습니다.
