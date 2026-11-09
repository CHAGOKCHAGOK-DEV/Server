# 주행 이력·일일 미션 진행 조회

## 범위

저장된 Trip과 MissionResult만 읽는 조회 API입니다. 별도 Report나 DailyProgress 엔티티를 두지 않고 요청 시점에 집계하며, 경험치·레벨·배지와 일일 목표 달성 판정은 포함하지 않습니다. 모든 경로는 [주행·미션 결과 동기화 API](./trip-sync.md)와 같은 설치 인증을 요구하고 인증된 설치의 기록만 반환합니다.

| 경로 | 용도 |
| --- | --- |
| `GET /api/v1/trips` | 주행 목록, 시작 시각 최신순 커서 페이지 |
| `GET /api/v1/trips/{tripId}` | 주행 상세와 미션 결과 |
| `GET /api/v1/missions/daily-progress` | Asia/Seoul 날짜 기준 일일 미션 수행 현황 |

## 설치 인증

`Authorization: Bearer <설치 토큰 원문>`과 `X-Installation-Id` 헤더가 필요합니다. 설치 인증 필터와 설치 주체 인가 규칙은 동기화 경로와 위 세 경로에 같은 matcher로 적용됩니다. 회원 JWT만 전달하거나 인증 정보가 없으면 401 COMMON_401을 반환하며, `/api/v1/trip%73`처럼 인코딩한 동일 경로도 디코딩 경로 기준으로 판단하므로 같은 규칙을 따릅니다. 끝에 `/`를 붙인 경로는 매핑되지 않습니다. 설치 전용 엔드포인트를 새로 추가할 때는 `SecurityConfig`의 matcher 목록에도 등록해야 하며, 빠뜨리면 회원 JWT 요청이 설치 필터를 거치지 않습니다.

필터가 이미 ACTIVE 상태를 확인하므로 조회 트랜잭션에서는 동기화와 달리 설치 행을 잠그지 않습니다. 쓰기가 없으므로 인증 직후 REVOKED로 바뀌는 경합의 영향은 그 요청의 읽기 결과 하나로 한정됩니다.

## 주행 목록

| 파라미터 | 규칙 |
| --- | --- |
| `cursor` | 선택, 이전 응답의 `nextCursor`를 그대로 전달, 첫 페이지는 생략 |
| `size` | 선택, 기본 20, 1~100 |

```json
{
  "isSuccess": true,
  "code": "OK",
  "message": "요청에 성공했습니다.",
  "data": {
    "items": [
      {
        "tripId": "33333333-3333-4333-8333-333333333333",
        "missionPackCode": "DEFAULT",
        "missionPackVersion": 1,
        "status": "COMPLETED",
        "endReason": "MANUAL_STOP",
        "startedAt": "2026-11-07T01:00:00Z",
        "endedAt": "2026-11-07T01:10:00Z",
        "syncedAt": "2026-11-07T02:00:00Z",
        "summary": { "missionResultCount": 3, "participatedCount": 2, "completedCount": 2, "correctCount": 1 }
      }
    ],
    "nextCursor": "AQAF..."
  }
}
```

정렬은 `startedAt` 내림차순이고 같은 시각은 `tripId` 내림차순(PostgreSQL uuid 비교 순서)입니다. 동률 순서는 페이지 경계를 안정적으로 나누기 위한 것이므로 클라이언트는 `tripId` 순서에 의미를 두지 않습니다.

`nextCursor`가 null이면 마지막 페이지입니다. 커서는 마지막 항목의 시작 시각과 Trip UUID를 담은 불투명 문자열이며 클라이언트가 해석하거나 만들지 않습니다. 형식이 잘못되었거나 변조된 커서, 빈 문자열은 400 TRIP_400_2로 거부하므로 저장한 커서를 버리고 첫 페이지부터 다시 조회합니다. 커서에는 위치 정보만 있고 서명하지 않습니다. 임의의 값을 넣어도 조회 조건에 인증된 설치가 항상 포함되어 다른 설치의 기록에는 접근할 수 없기 때문입니다. 형식을 바꿀 때는 커서 앞의 버전 바이트를 올립니다.

offset 대신 `(startedAt, tripId)` keyset을 사용합니다. 오프라인 동기화로 과거 주행이 나중에 저장되어도 이미 읽은 항목이 다음 페이지로 밀리지 않으므로 중복이나 누락이 생기지 않습니다. 현재 커서보다 오래된 주행은 이후 페이지에 나타나고, 탐색을 시작한 뒤 저장된 더 최근 주행은 진행 중인 탐색에 나타나지 않으므로 첫 페이지를 다시 조회해 확인합니다. 다음 페이지 유무는 `size + 1`건을 조회해 판단하므로 마지막 페이지가 정확히 `size`건이어도 빈 페이지를 추가로 요청할 필요가 없습니다.

| 요약 필드 | 정의 |
| --- | --- |
| `missionResultCount` | 해당 주행의 전체 미션 결과 수, 동기화 응답의 같은 이름 필드와 동일한 의미 |
| `participatedCount` | `participated = true` 결과 수 |
| `completedCount` | 상태가 COMPLETED인 결과 수 |
| `correctCount` | `correct = true` 결과 수, 판정 없음(null)과 오답은 제외 |

## 주행 상세

목록 항목과 같은 필드와 요약에 `missionResults`를 더해 반환합니다. 결과는 `startedAt` 오름차순, 같은 시각은 `resultId` 순서입니다. 각 결과는 `resultId`, `missionCode`, `status`, `participated`, `correct`, `startedAt`, `endedAt`을 포함하고 `correct`는 null일 수 있습니다. 요약은 목록과 같은 SQL 조각으로 계산합니다.

다른 설치의 주행과 존재하지 않는 주행은 구분하지 않고 같은 404 TRIP_404 응답을 반환합니다. 다만 동기화 API는 다른 설치의 Trip UUID에 404를 반환하고 처음 보는 UUID는 저장하므로, 이 응답만으로 UUID 존재 여부가 완전히 숨겨지지는 않습니다. 클라이언트가 생성하는 무작위 UUID라 추측이 어렵다는 점에 기대고 있습니다. UUID 형식이 아니면 400 COMMON_400_3입니다. 이 매핑 때문에 `GET /api/v1/trips/sync`도 상세 경로로 해석되어 405 대신 400 COMMON_400_3을 반환합니다.

Trip과 결과는 동기화 트랜잭션 하나에서 함께 커밋되고 이후 수정되지 않으므로 상세 조회의 두 쿼리 사이에 일부 결과만 보이는 상태는 생기지 않습니다. 결과 조회 쿼리도 설치 조건을 함께 사용합니다.

## 일일 미션 진행

| 파라미터 | 규칙 |
| --- | --- |
| `date` | 선택, `YYYY-MM-DD`, 0001-01-01~9999-12-31, 생략 시 서버 시각 기준 Asia/Seoul 오늘 |

```json
{
  "isSuccess": true,
  "code": "OK",
  "message": "요청에 성공했습니다.",
  "data": {
    "date": "2026-11-08",
    "timeZone": "Asia/Seoul",
    "goal": 5,
    "contributingTripCount": 2,
    "missionResultCount": 4,
    "participatedCount": 3,
    "completedCount": 2,
    "expiredCount": 1,
    "cancelledCount": 1,
    "correctCount": 1
  }
}
```

각 미션 결과는 Trip의 날짜가 아니라 결과 자신의 `startedAt`을 Asia/Seoul로 변환한 날짜에 집계합니다. 자정을 걸친 결과는 시작한 날에 포함되고, 같은 날짜의 결과는 여러 주행에 걸쳐 합산합니다. `contributingTripCount`는 해당 날짜에 집계된 결과가 하나 이상 있는 주행 수입니다. 날짜 범위는 서버가 `[해당일 00:00, 다음날 00:00)` Asia/Seoul 시각으로 계산하므로 과거 일광절약시간처럼 하루가 24시간이 아닌 날도 정확히 나눕니다.

`goal`은 현재 5로 고정합니다. 어떤 결과를 달성으로 인정할지(COMPLETED, 참여, 정답 중 무엇을 기준으로 할지)는 아직 정해지지 않았으므로 서버는 달성 건수나 달성 여부를 계산하지 않고 원시 건수만 제공합니다. 클라이언트도 이 값들로 달성 여부를 임의로 판정하지 않으며, 정책이 확정되면 별도 필드로 추가합니다. 주행 요약과 일일 진행의 참여·완료·정답 건수는 같은 정의를 사용하므로 한쪽 정의를 바꿀 때는 다른 쪽도 함께 바꿉니다.

날짜 범위는 [동기화 API](./trip-sync.md#입력-규칙)가 받는 시각의 연도 범위(0001~9999)를 따릅니다. 날짜 형식이 틀리거나 존재하지 않는 날짜는 400 COMMON_400_3, 지원 범위를 벗어난 날짜는 400 COMMON_400입니다.

## 조회 쿼리와 인덱스

| 조회 | 사용 인덱스 | 비고 |
| --- | --- | --- |
| 목록 | `idx_trips_installation_started`, `idx_mission_results_trip` | `installation_id = ? AND started_at <= ?`가 인덱스 범위, 동률 `id` 조건은 필터, 확정된 페이지 행만 결과 요약 |
| 상세 | `pk_trips`, `idx_mission_results_trip` | Trip과 결과 조회 모두 설치 ID 조건 포함 |
| 일일 진행 | `idx_trips_installation_started`, `idx_mission_results_trip` | `started_at < 다음날 시작`이 인덱스 범위, 결과 시각 조건은 필터 |

같은 시작 시각의 주행은 드물어 동률 정렬용 `id`를 인덱스에 추가하지 않았습니다. 일일 진행 조회는 결과 시작 시각이 Trip 시작 이후라는 동기화 검증을 이용해 Trip 범위를 줄이지만 시작 시각 하한이 없어, 해당 설치에서 조회일 이전에 시작한 주행을 인덱스로 훑습니다. 설치당 주행 수가 적은 현재 규모에서는 허용하지만, 늘어나면 최대 주행 시간 정책에 따른 하한 조건이나 별도 인덱스를 검토합니다.

## 실패 응답

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | COMMON_400 | `size` 범위 또는 지원 범위 밖 날짜 |
| 400 | COMMON_400_3 | 숫자·UUID·날짜 형식 오류 |
| 400 | TRIP_400_2 | 잘못되었거나 변조된 커서 |
| 401 | COMMON_401 | 설치 인증 실패, REVOKED 또는 회원 JWT |
| 404 | TRIP_404 | 다른 설치의 주행 또는 존재하지 않는 주행 |
| 500 | COMMON_500 | 처리하지 못한 서버·DB 오류 |

## 검증 실행

커서 인코딩 단위 테스트는 `./gradlew test`에 포함됩니다. 조회·집계·접근 제어 통합 테스트(`TripHistoryPostgresTest`)는 [동기화 API 문서](./trip-sync.md#검증-실행)와 같은 방법으로 `postgresTest` 태스크에서 실행합니다.
