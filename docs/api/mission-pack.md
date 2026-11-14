# 미션팩 조회 API와 연령대별 미션 13종

이 문서의 최신 연동 기준은 **DEFAULT v4 / schemaVersion 3**입니다. 미션 데이터는 [V10 시드](../../src/main/resources/db/migration/V10__seed_age_based_mission_pack.sql), 설정 형식은 [스키마 v3](../../src/main/resources/schema/mission-config-v3.json)를 기준으로 합니다. `version`은 콘텐츠 버전이고 `schemaVersion`은 설정 형식 버전입니다.

## 최신 미션팩 조회

```http
GET /api/v1/mission-packs/current?code=DEFAULT&supportedSchemaVersion=3
```

인증 없이 호출할 수 있습니다. 서버는 요청 코드의 PUBLISHED 팩 중 클라이언트와 서버가 모두 지원하는 스키마 이하인 가장 높은 콘텐츠 버전을 반환합니다. 서버의 지원 스키마 상한은 3입니다. ARCHIVED 팩과 비활성 미션은 새 다운로드에 포함되지 않으며, 미션은 코드 오름차순으로 정렬됩니다.

| 파라미터 | 기본값 | 제약과 의미 |
| --- | --- | --- |
| code | DEFAULT | 공백이 아닌 미션팩 코드 |
| supportedSchemaVersion | 1 | 1 이상의 정수, 클라이언트가 처리할 수 있는 최대 스키마 버전 |

현재 시드의 발행 상태를 기준으로 반환 버전은 다음과 같습니다. 신규 팩을 사용하려면 `supportedSchemaVersion=3`을 명시해야 합니다.

| 요청 스키마 | 콘텐츠 버전 | 응답 스키마 | 미션 수 |
| --- | --- | --- | --- |
| 생략 또는 1 | DEFAULT v1 | 1 | 4 |
| 2 | DEFAULT v3 | 2 | 10 |
| 3 이상 | DEFAULT v4 | 3 | 13 |

발행된 팩이 없으면 404 `MISSION_PACK_404`, 발행된 팩은 있으나 호환 스키마가 없으면 406 `MISSION_PACK_406`을 반환합니다. 비어 있는 code나 1 미만의 supportedSchemaVersion은 400입니다.

## 최신 응답 예시

아래는 공통 `ApiResponse`의 `data` 부분입니다. 실제 응답에는 `isSuccess`, 최상위 `code`, `message`도 포함됩니다. 설명을 위해 13종 중 SPEED_GUESS 한 종만 발췌했으며, publishedAt은 예시 시각입니다.

```json
{
  "code": "DEFAULT",
  "version": 4,
  "schemaVersion": 3,
  "publishedAt": "2026-11-15T00:00:00Z",
  "missions": [
    {
      "code": "SPEED_GUESS",
      "triggerType": "SPEED_STABLE",
      "config": {
        "title": "빠를까 느릴까",
        "sourceIds": [
          "F-06"
        ],
        "trigger": {
          "event": "SPEED_STABLE",
          "stableSeconds": 30,
          "toleranceKph": 5,
          "minSpeedKph": 20,
          "straightRoadRequired": true,
          "intervalSeconds": 900
        },
        "execution": {
          "validity": {
            "mode": "TIME",
            "durationMs": 30000
          },
          "cooldownSecondsByIntensity": {
            "LOW": 900,
            "NORMAL": 900,
            "HIGH": 900
          },
          "holdWhen": {
            "turnSignalOn": true,
            "naviSpeaking": true
          },
          "askWhen": "DRIVING",
          "noAnswerCompletes": false,
          "presentationOnly": false
        },
        "ageContent": {
          "AGE_5_6": {
            "question": "지금 차가 빨리 갈까, 천천히 갈까?",
            "answerKind": "FAST_SLOW",
            "expectedAnswer": "SPEED_THRESHOLD",
            "correctMessage": "맞아요! 지금은 시속 약 {speed}km예요!",
            "wrongMessage": "지금은 시속 약 {speed}km예요!",
            "thresholdKph": 60
          },
          "AGE_7_8": {
            "question": "창밖 나무가 휘휘 지나가요? 우리 차는 빠를까, 느릴까?",
            "answerKind": "FAST_SLOW",
            "expectedAnswer": "SPEED_THRESHOLD",
            "correctMessage": "맞아요! 지금은 시속 약 {speed}km예요!",
            "wrongMessage": "지금은 시속 약 {speed}km예요!",
            "thresholdKph": 60
          },
          "AGE_9_PLUS": {
            "question": "지금 속도가 50km보다 빠를까, 느릴까?",
            "answerKind": "FAST_SLOW",
            "expectedAnswer": "SPEED_THRESHOLD",
            "correctMessage": "맞아요! 지금은 시속 약 {speed}km예요!",
            "wrongMessage": "지금은 시속 약 {speed}km예요!",
            "thresholdKph": 50
          }
        }
      }
    }
  ]
}
```

## 미션 목록

`sourceIds`의 F-01~F-13은 개정 기획의 미션 번호입니다. 반환 순서는 아래 기획 번호 순서가 아닌 code 오름차순입니다.

| 기획 번호 | 코드 | 미션 | 이벤트 |
| --- | --- | --- | --- |
| F-01 | BLINK | 깜빡이 소리 | TURN_SIGNAL_OFF |
| F-02 | BLINK_LEARN | 깜빡이 배우기 | MISSION_COMPLETED |
| F-03 | WIPER | 비 오는 날 | WIPER_ON |
| F-04 | RAIN_STOP | 비가 그쳤어요 | WIPER_OFF |
| F-05 | NEXT_TURN | 다음 회전 | NAVI_NEXT_TURN |
| F-06 | SPEED_GUESS | 빠를까 느릴까 | SPEED_STABLE |
| F-07 | SIGNAL_WAIT | 신호 대기 | VEHICLE_STOPPED |
| F-08 | DEPART | 부기장 출동 | TRIP_STARTED |
| F-09 | BLINK_MEMORY | 깜빡이 기억 게임 | TURN_SIGNAL_SUMMARY |
| F-10 | WAIT_BET | 기다리기 내기 | VEHICLE_STOPPED |
| F-11 | SPEED_CHANGE | 빨라졌을까 느려졌을까 | SPEED_CHANGED |
| F-12 | NEAR_DEST | 곧 도착 | NEAR_DESTINATION |
| F-13 | TODAY_REPORT | 오늘의 리포트 | TRIP_ENDED |

## 설정 구조와 책임

서버는 미션 설정을 저장·제공하고 발행 시 JSON Schema와 코드-이벤트 일치를 검증합니다. 차량·내비 신호 감지, 트리거 실행, 음성 인식, 정답 판정, 재질문, 리포트 집계는 클라이언트가 처리합니다. JSON Schema 검증은 콘텐츠 형식 검사이며 아이의 답변을 서버에서 재판정하는 기능이 아닙니다.

| 필드 | 의미 |
| --- | --- |
| title | 미션 이름 |
| sourceIds | 기획 미션 번호 |
| trigger | 이벤트 및 발생 조건 |
| execution | 질문 가능한 상태, 유효성, 강도별 간격 및 실행 정책 |
| ageContent | 연령별 질문, 응답 형식, 정답 출처 및 피드백 |

`ageContent`는 `AGE_5_6`, `AGE_7_8`, `AGE_9_PLUS` 세 항목을 모두 포함합니다. 프로필 연령대 저장·조회에는 기존 부기장 API를 사용합니다.

### 응답 형식과 정답 출처

`answerKind`는 `YES_NO`, `LEFT_RIGHT`, `FREE`, `NONE` 및 `BACKCAR_SKY`, `BEFORE_AFTER`, `WIPER_LIGHT`, `STOPPED_POURING`, `FAST_SLOW`, `STOP_GO`, `GREEN_RED`, `SOON_LONG`을 사용합니다. 두 단어 형식의 고정 정답 `OPTION_A`는 첫 선택지입니다.

| expectedAnswer | 클라이언트가 사용하는 값 |
| --- | --- |
| YES | 예 응답 |
| OPTION_A | 응답 형식의 첫 선택지 |
| ANY_SPOKEN | 자유 응답 참여 |
| TURN_SIGNAL_DIRECTION | 발생한 방향지시등 방향 |
| WIPER_AT_RESPONSE | 응답 시점 와이퍼 상태 |
| NAVI_DIRECTION | 내비 다음 회전 방향 |
| SPEED_THRESHOLD | 해당 연령의 thresholdKph와 실제 속도 비교 |
| TURN_SIGNAL_COUNTS | 주행 중 좌우 방향지시등 횟수 비교 |
| STOP_DURATION | 정차 시작부터 재출발까지 소요 시간 |
| SPEED_HISTORY | 해당 연령의 comparisonSeconds 이전 속도와 비교 |
| NONE | 발표 전용, 응답 대기 없음 |

**expectedAnswer가 SPEED_THRESHOLD이면 같은 연령 설정의 thresholdKph가 필수입니다.** 누락 시 서버 설정 검증이 실패합니다. thresholdKph는 0 이상의 정수이며 현재 SPEED_GUESS 시드는 5~8세 60km/h, 9세 이상 50km/h를 사용합니다.

### 실행과 유효성

`execution.askWhen`은 `DRIVING`, `STOPPED`, `TRIP_END`를 구분합니다. `validity.mode`는 `TIME`, `NAVIGATION_DISTANCE`, `ROUTE_ACTIVE`, `STOPPED`, `UNTIL_RESTART`, `TRIP_END`를 사용합니다. TIME은 durationMs, NAVIGATION_DISTANCE는 expireAtDistanceMeters를 포함합니다.

`cooldownSecondsByIntensity`는 LOW/NORMAL/HIGH별 간격입니다. 현재 기본 시드는 300/180/120초이며 SPEED_GUESS는 세 강도 모두 900초입니다. `holdWhen`은 방향지시등 사용 또는 내비 발화 중 질문 보류 여부입니다.

| 미션 | 주요 설정 |
| --- | --- |
| BLINK | 방향지시등 1초 이상 켜진 뒤 꺼짐 |
| BLINK_LEARN | BLINK 완료 후, 하루 최대 1회 |
| RAIN_STOP | 와이퍼 60초 이상 작동 후 꺼짐 |
| NEXT_TURN | 회전까지 300~500m 구간, 300m 도달 시 폐기 |
| SPEED_GUESS | 직진 중 30초 일정 속도, 최소 간격 900초 |
| SIGNAL_WAIT | 정차 10초 후, 정차 중 질문 |
| DEPART | 첫 출발, 주행당 1회 |
| BLINK_MEMORY | 방향지시등 합계 4회 이상, 동률 제외, 정차 중 주행당 1회 |
| WAIT_BET | 정차 5초 후 질문, 재출발 시 판정 |
| NEAR_DEST | ETA 300초 이하, 목적지까지 1km 밖, 주행당 1회 |
| TODAY_REPORT | 기어 P로 주행 종료 시 발표 |

WAIT_BET의 `execution.resolution`은 재출발 이벤트와 정차 시간 기준을 나타냅니다. 정차 시작부터 30초 이내 재출발이면 첫 선택지를 정답으로 보고, 주차하거나 600초를 초과하면 발표를 버립니다.

DEPART와 NEAR_DEST의 `noAnswerCompletes=true`는 무응답에도 완료 처리하는 현재 시드 정책입니다. TODAY_REPORT는 `presentationOnly=true`, `answerKind=NONE`으로 발표만 하며 별도 MissionResult를 저장하지 않습니다. 기존 수행 결과 저장·동기화 및 서버 집계 API는 이번 미션팩 변경에서 수정하지 않았습니다.

### 속도 변화 기준

SPEED_CHANGE는 기획에 따라 **20초 동안 속도가 15km/h를 초과해 변할 때** 발동합니다. `trigger.windowSeconds=20`, `trigger.deltaKphExclusive=15`이며 변화량이 정확히 15km/h이면 발동하지 않습니다.

정답 비교 구간은 트리거 발생 구간과 별도로 연령별 `comparisonSeconds`를 사용합니다.

| 연령 | comparisonSeconds | 비교 기준 |
| --- | --- | --- |
| AGE_5_6 | 20 | 20초 전 속도 |
| AGE_7_8 | 20 | 20초 전 속도 |
| AGE_9_PLUS | 60 | 1분 전 속도 |

클라이언트는 기획 설정에 맞춰 실행·판정 코드를 적용해야 합니다. 서버는 정답을 별도로 재계산하지 않습니다.

### 음성 문구 치환

`{side}`, `{speed}`, `{more}`, `{less}`, `{other}`, `{sec}`, `{answer}`, `{change}`, `{diff}`, `{n}`, `{m}`, `{best}`는 클라이언트가 실제 주행·결과 값으로 치환합니다. 원본 플레이스홀더를 그대로 발화하지 않도록 처리해야 합니다.

## 클라이언트 연동 확인 사항

2026-11-15 로컬 Client 코드 확인 당시 MissionPackRepository는 supportedSchemaVersion=1로 다운로드·캐시하며, 캐시를 MissionDefinitions 실행 엔진에 적용하는 연결은 TODO였습니다. 이는 당시 확인한 상태이며 이후 클라이언트 변경을 보장하지 않습니다.

신규 팩 적용에는 schema 3 요청, 연령별 설정 선택, 트리거·정답 출처·플레이스홀더 처리, 캐시와 실행 엔진 연결이 필요합니다. 데이터가 서버 API에 존재하는 것과 앱에서 그 데이터를 실행하는 것은 별도 단계입니다.

## 이전 버전과 호환성 이력

발행된 팩은 Flyway V4의 DB 보호 트리거로 수정·삭제가 제한됩니다. 변경은 새 콘텐츠 버전으로 발행하고, 과거 주행 동기화는 당시 사용한 PUBLISHED 또는 ARCHIVED 팩을 참조할 수 있습니다.

### DEFAULT v1: 기본 4종

Flyway V3가 `DEFAULT v1 / schemaVersion 1`을 발행합니다. 스키마는 [mission-config-v1.json](../../src/main/resources/schema/mission-config-v1.json)이며 코드 BLINK, WIPER, NEXT_TURN, NEAR_DEST만 지원합니다. v1의 응답 형식·정답은 공통 completion에 있고, 최신 v3 스키마에서는 연령별 ageContent 안에 있습니다. v1의 NEAR_DEST는 ETA 기준만 사용하며 신규 v4의 거리 조건을 적용하지 않습니다.

### DEFAULT v2: 초기 시연용 10종

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

### DEFAULT v3: 확정 시연용 10종

Flyway V8은 발행된 v2를 유지하고 `DEFAULT v3 / schemaVersion 2`를 발행했습니다. `N-02 도착까지`를 제외하고 `T-07 창밖 찾기`를 포함합니다. `supportedSchemaVersion=2` 요청은 v3의 10종을 받습니다.

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

v2 설정 형식의 원본은 [mission-config-v2.json](../../src/main/resources/schema/mission-config-v2.json)입니다. 이전 버전 예시와 실행 설명은 해당 콘텐츠 버전에만 적용하며, 신규 클라이언트 연동은 이 문서 앞부분의 DEFAULT v4 기준을 사용합니다.
