# 동의 API

## 개요

S-03 부모 동의 화면을 위한 설치 인증 API입니다. 현재 적용 중인 문서 조회, 설치별 동의 상태 조회, 동의 이벤트 기록을 제공합니다. 이 API의 동의 기록은 화면에서 동의 항목을 선택한 사실이며 법정대리인 확인 완료를 의미하지 않습니다. `verification_method`와 `verified_at`은 요청에서 받지 않고 이 API에서 기록하지 않습니다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/v1/agreements/current` | 문서 코드별 현재 적용 문서 |
| GET | `/api/v1/agreements/status` | 현재 문서별 이 설치의 동의 상태와 `consentRequired` |
| POST | `/api/v1/agreements/consents` | 동의 이벤트 기록 |

## 설치 인증

세 경로 모두 `Authorization: Bearer <설치 토큰 원문>`과 `X-Installation-Id`가 필요하며 [주행·미션 결과 동기화 API](./trip-sync.md)의 설치 인증 규칙을 그대로 따릅니다. 회원 JWT는 인증 수단이 아니며 `/api/v1/agreements/%73tatus`처럼 인코딩한 동일 경로에도 설치 인증이 필요합니다. 끝에 `/`를 붙인 경로는 매핑되지 않습니다. 기록 트랜잭션에서는 설치의 ACTIVE 상태를 다시 확인하고 공유 잠금을 유지합니다.

## 현재 문서

현재 문서는 코드별로 `status = PUBLISHED`이고 `effective_at`이 서버 현재 시각 이하인 버전 중 가장 높은 버전입니다. 적용 시각이 미래인 PUBLISHED 버전은 예약 버전으로 취급하며 그 시각이 될 때까지 이전 버전이 현재 문서로 유지됩니다. DRAFT, ARCHIVED, 모든 버전이 ARCHIVED인 코드는 반환하지 않습니다. 발행된 문서가 하나도 없으면 빈 목록을 반환합니다. 목록은 `code` 오름차순입니다.

```json
{
  "isSuccess": true,
  "code": "OK",
  "message": "요청에 성공했습니다.",
  "data": {
    "documents": [
      {
        "documentId": 2,
        "code": "TERMS_OF_SERVICE",
        "version": 2,
        "title": "서비스 이용약관",
        "content": "...",
        "required": true,
        "effectiveAt": "2026-11-01T00:00:00Z"
      }
    ]
  }
}
```

## 동의 상태

| state | 의미 |
| --- | --- |
| ACCEPTED | 현재 버전에 대한 ACCEPTED 이벤트가 있음 |
| OUTDATED | 같은 코드의 다른 버전에만 ACCEPTED 이벤트가 있음 |
| NOT_ACCEPTED | 같은 코드의 ACCEPTED 이벤트가 없음 |

`consentRequired`는 필수 현재 문서 중 하나라도 ACCEPTED가 아니면 true입니다. 선택 문서의 미동의는 영향을 주지 않습니다. 상태는 저장하지 않고 조회할 때마다 이력과 현재 문서로 계산하므로 새 버전이 적용되면 별도 처리 없이 OUTDATED로 바뀝니다.

서버는 어떤 코드가 필수인지 별도로 알지 못하고 현재 문서 목록만 평가합니다. 따라서 현재 문서가 없는 코드는 `consentRequired` 판정에서 빠지며, 발행된 필수 문서가 없으면 `consentRequired`는 false입니다. 이 fail-open 동작은 동의 대상이 확정되기 전까지 수용한 정책이며, Android 출시 전에 필수 문서를 발행하고 아래 문서 교체 절차를 지켜야 합니다. trip sync 등 다른 API는 동의 여부를 검사하지 않습니다.

```json
{
  "data": {
    "consentRequired": true,
    "documents": [
      { "documentId": 3, "code": "TERMS_OF_SERVICE", "version": 3, "required": true, "state": "OUTDATED" }
    ]
  }
}
```

## 동의 기록

```json
{
  "events": [
    {
      "eventId": "55555555-5555-4555-8555-555555555555",
      "code": "TERMS_OF_SERVICE",
      "version": 2,
      "action": "ACCEPTED",
      "occurredAt": "2026-11-08T01:00:00Z"
    }
  ]
}
```

```json
{
  "data": {
    "consents": [
      {
        "eventId": "55555555-5555-4555-8555-555555555555",
        "code": "TERMS_OF_SERVICE",
        "version": 2,
        "action": "ACCEPTED",
        "occurredAt": "2026-11-08T01:00:00Z",
        "recordedAt": "2026-11-08T01:00:03.120394Z"
      }
    ]
  }
}
```

문서는 환경마다 달라질 수 있는 `documentId`가 아니라 `(code, version)`으로 지정합니다. 응답은 eventId 문자열 오름차순이며 저장된 값만 담으므로 재전송과 순서를 바꾼 재전송에도 같은 본문을 반환합니다. `recordedAt`은 최초 저장한 서버 기록 시각입니다. 동의 결과 상태는 응답에 포함하지 않으므로 필요하면 상태 API를 다시 조회합니다.

| 항목 | 규칙 |
| --- | --- |
| events | 필수, 1~20건, null 요소 불가 |
| eventId | 필수 UUID, Android가 생성하고 재전송 시 유지, 요청 내 중복 불가 |
| code, version | 코드 최대 50자, 버전 양수, 요청 내 같은 (code, version) 중복 불가, 같은 코드의 다른 버전은 허용 |
| 문서 | 신규 이벤트는 PUBLISHED 또는 ARCHIVED이고 적용 시각이 지난 문서만 허용, DRAFT·미존재·예약 문서는 거부 |
| action | 필수, 현재 ACCEPTED만 허용, WITHDRAWN은 후속 범위로 거부 |
| occurredAt | 필수, 소수점 이하 최대 6자리, 신규 이벤트는 서버 현재 시각 + 5분 이하이고 문서 적용 시각 - 5분 이상 |

`occurredAt`은 오프셋을 포함한 ISO-8601 문자열도 받으며 UTC로 저장합니다. 이 값은 클라이언트가 주장하는 동의 시각이므로 감사나 분쟁 대응에서 서버가 보증하는 시각은 `recordedAt`입니다. 오프라인에서 이전 버전에 동의한 뒤 새 버전이 발행되어도 이전 버전 동의를 실제 이력으로 기록하며, 이 이벤트는 현재 버전 요건을 충족하지 않으므로 상태는 OUTDATED입니다. 기기 시계가 5분 넘게 앞서 있으면 신규 동의가 거부되므로 Android는 오류 시 기기 시각 확인을 안내해야 합니다.

## 멱등성과 동시성

요청 전체를 하나의 트랜잭션으로 처리하며 한 이벤트라도 실패하면 모든 이벤트를 롤백합니다. 처리 순서는 다음과 같습니다.

1. 요청 내 중복, 행위, 시각 정밀도를 검사합니다.
2. eventId 순서로 기존 이벤트를 조회합니다. 이미 있으면 소유 설치를 먼저 확인하고 코드, 버전, 행위, 발생 시각을 비교합니다. 이때 시각 범위와 문서 상태는 다시 검사하지 않으므로, 커밋된 이벤트의 재전송은 서버 시계 조정이나 문서 보관 처리와 무관하게 같은 결과를 반환합니다.
3. 신규 이벤트의 문서와 시각 범위를 모두 검사한 뒤에만 쓰기를 시작합니다.
4. 신규 이벤트를 eventId 순서로 `INSERT ON CONFLICT (id) DO NOTHING`한 뒤 같은 ID를 다시 조회해 2와 같은 기준으로 판정합니다. 동시 요청의 PK 충돌은 DB가 승자 트랜잭션 완료를 기다린 뒤 이 단계에서 재판정합니다.

할당 UUID의 `JpaRepository.save()`는 merge로 동작할 수 있어 사용하지 않습니다.

첫 요청이 커밋되기 전에 같은 이벤트를 재시도하면 재시도는 2에서 기존 이벤트를 보지 못하고 3의 신규 검증을 받습니다. 이때 재시도를 처리한 서버의 시계가 5분 넘게 늦으면, 첫 요청은 커밋되는데 재시도만 AGREEMENT_400 또는 AGREEMENT_400_1을 받을 수 있습니다. 저장 상태는 올바르고 이후 재시도는 200이므로 이 동작은 수용한 범위입니다. 따라서 재전송에 대한 400은 이벤트가 저장되지 않았다는 보장이 아니며, Android는 대기 이벤트를 바로 삭제하지 말고 상태 API를 다시 조회하거나 나중에 재전송해야 합니다.

| 상황 | 처리 |
| --- | --- |
| 같은 설치·eventId·내용 | 최초 기록 반환 |
| 같은 설치·eventId·다른 코드, 버전, 행위 또는 시각 | 409, 기존 기록 유지, 요청 전체 롤백 |
| 다른 설치의 eventId | 내용 비교 전에 404 |
| 같은 문서에 새 eventId | 별도 이벤트로 추가 |
| 저장 중 DB 오류 | 전체 롤백, 같은 eventId로 재시도 가능 |

한 요청에 여러 오류가 섞이면 기존 이벤트 판정(404, 409)이 신규 이벤트 검증(AGREEMENT_400, AGREEMENT_400_1)보다 먼저이며, 같은 단계 안에서는 eventId 오름차순으로 처음 만난 오류를 반환합니다. 오류 응답은 원인 eventId를 담지 않습니다.

다른 설치의 eventId에 404를 반환하는 것은 Android가 새 설치로 등록한 뒤 이전 설치의 대기 이벤트를 다시 보내는 경우에도 같습니다. 오류가 원인 이벤트를 알려 주지 않으므로 Android는 AGREEMENT_404를 받으면 이전 설치에서 만든 대기 이벤트 전체를 새 eventId로 다시 기록해야 합니다.

## 문서 불변성과 교체 절차

동의 문서는 관리 API 없이 SQL로 관리합니다. V2 마이그레이션의 트리거는 PUBLISHED·ARCHIVED 문서의 ID, 코드, 버전, 제목, 원문, 필수 여부, 적용 시각, 생성 시각 변경과 삭제를 거부합니다. 상태 전이는 DRAFT에서 PUBLISHED, PUBLISHED에서 ARCHIVED만 허용하며 DRAFT가 아닌 문서에는 적용 시각이 필요합니다. 동의 조건을 담는 컬럼을 추가하면 트리거 함수의 보호 컬럼 목록도 함께 갱신해야 합니다. 이 트리거는 실수 방지 장치이며 TRUNCATE나 테이블 소유자의 트리거 비활성화까지 막지는 않습니다.

문서 내용을 바꿀 때는 기존 행을 수정하지 않고 더 높은 version의 새 행을 PUBLISHED로 추가합니다. 높은 버전이 적용 시각부터 자동으로 현재 문서가 되므로 이전 버전을 ARCHIVED로 바꿀 필요는 없습니다. 보관 처리를 하더라도 새 버전의 적용 시각이 지난 뒤에 실행해야 하며, 그 전에 이전 버전을 보관하면 그 코드의 현재 문서가 사라져 `consentRequired`가 false가 됩니다. 보관 처리 시 `updated_at`을 함께 갱신합니다.

## 로컬 샘플 문서

local 프로필에서만 저장소 루트의 `db/seed/local` Repeatable 마이그레이션으로 `LOCAL_SAMPLE_*` 문서를 등록합니다. 이 파일은 jar와 컨테이너 이미지에 포함되지 않고 prod 프로필의 Flyway 위치에도 없습니다. 저장소 루트에서 실행해야 적용되며 기존 행은 변경하지 않습니다.

샘플을 적용한 DB를 시드 위치 없이 기동하면 Flyway validate가 `Detected applied migration not resolved locally`로 실패합니다(2026-11-09 확인). 저장소 루트가 아닌 작업 디렉터리에서 실행할 때도 같습니다. local 프로필의 DB 접속 정보는 공유·운영 DB를 가리키면 안 됩니다. 운영 DB에 샘플이 적용되면 가짜 필수 문서가 현재 문서가 되고, 트리거 때문에 삭제할 수 없으며, 운영 기동의 Flyway validate도 실패합니다.

## 실패 응답

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | COMMON_400 | 필수 필드, 목록 크기 또는 형식 검증 실패 |
| 400 | COMMON_400_1 | JSON 또는 Enum 값 파싱 실패 |
| 400 | AGREEMENT_400 | 지원하지 않는 행위, 시각 범위·정밀도, 요청 내 중복 |
| 400 | AGREEMENT_400_1 | 동의할 수 없는 문서 코드·버전 |
| 401 | COMMON_401 | 설치 인증 실패, REVOKED 또는 회원 JWT |
| 404 | AGREEMENT_404 | 다른 설치의 eventId |
| 409 | AGREEMENT_409 | 저장된 eventId의 내용 충돌 |
| 409 | COMMON_409 | 그 밖의 DB 무결성 위반 |
| 500 | COMMON_500 | 처리하지 못한 서버·DB 오류 |

## 검증 실행

payload 검증과 서비스 판정 단위 테스트는 `./gradlew test`에 포함됩니다. API·트리거·동시성 통합 테스트(`AgreementPostgresTest`)는 [주행·미션 결과 동기화 API](./trip-sync.md#검증-실행)와 같은 방법으로 `postgresTest` 태스크에서 실행합니다.

## 후속 범위

- WITHDRAWN 이벤트와 늦게 도착한 이벤트의 순서 정책, 철회 후 서비스 제한
- 실제 동의 대상 문서 확정과 운영 발행, 필수 코드 기반 fail-closed 판정 검토
- trip sync 등 서버 측 동의 강제 여부
- 법정대리인 확인 절차
- 문서 관리 API와 보관 시각 기록
- 동의 이력 보존 기간과 삭제 요청 처리(삭제 도입 시 재조회 판정 경로 재검토)
