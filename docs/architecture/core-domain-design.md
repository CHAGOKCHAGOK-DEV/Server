# 핵심 도메인 설계

## 1. 문서 목적

이 문서는 Installation, Copilot, MissionPack, Mission, Trip, MissionResult, AgreementDocument, AgreementHistory의 총 8개 엔티티 책임과 관계, 데이터베이스 제약조건을 선택한 이유를 기록합니다. 단순 컬럼 명세보다 변경 시 지켜야 할 불변식과 아직 서비스 계층에서 구현하지 않은 정책을 구분하는 데 목적이 있습니다.

관련 문서:

- [핵심 도메인 ERD](../erd/core-domain.md)
- [핵심 도메인 식별자 설계](../erd/id-strategy.md)
- [초기 Flyway 마이그레이션](../../src/main/resources/db/migration/V1__create_core_domain.sql)
- [동의 도메인과 화면 계약](./agreement-domain-design.md)

## 문서 구성

- [설계 원칙](#2-설계-원칙)
- [도메인 구성](#3-도메인-구성)
- [Installation과 Copilot](#4-installation과-copilot)
- [MissionPack과 Mission](#5-missionpack과-mission)
- [Trip과 MissionResult](#6-trip과-missionresult)
- [식별자 전략](#7-식별자-전략)
- [시각과 변경 이력](#8-시각과-변경-이력)
- [삭제와 보존 정책](#9-삭제와-보존-정책)
- [인덱스 정책](#10-인덱스-정책)
- [Flyway와 JPA 스키마 검증](#11-flyway와-jpa-스키마-검증)
- [향후 음성 응답 확장](#12-향후-음성-응답-확장)
- [현재 구현 범위에서 제외한 항목](#13-현재-구현-범위에서-제외한-항목)
- [변경 시 확인할 체크리스트](#14-변경-시-확인할-체크리스트)

## 결정 상태 요약

| 구분 | 현재 결정 | 상태 |
| --- | --- | --- |
| 설치 식별 | Installation을 차량이 아닌 앱 설치 단위로 사용 | 확정 |
| 단일 프로필 | Copilot이 Installation UUID를 공유 PK로 사용 | 확정 |
| 미션 콘텐츠 | MissionPack 버전과 Mission JSONB로 관리 | 확정 |
| 오프라인 기록 | Trip과 MissionResult에 Android 생성 UUID 사용 | 확정 |
| 동기화 충돌 | 동일 UUID의 payload 정규화 및 hash 비교 | 후속 구현 |
| 콘텐츠 불변성 | 발행된 MissionPack과 Mission의 수정 차단 | 후속 구현 |
| 비정상 종료 복구 | Android 동기화 계약에서 종료 시각과 사유의 복구 기준 확정 | 정책 확정 후 구현 |
| 음성 저장 | MissionVoiceResponse 별도 엔티티로 확장 | 정책 확정 후 구현 |
| 보상 기능 | 경험치, 배지 및 레벨 관련 모델 제외 | 현재 범위 제외 |

## 2. 설계 원칙

### 설치 단위를 주행 소유자로 사용

Installation은 실제 차량이나 회원 계정이 아니라 앱 설치 단위를 나타냅니다. 차량 변경, 프로필 수정 또는 초기화 여부와 관계없이 주행 기록의 소유 관계를 유지하기 위해 Trip은 Copilot이 아니라 Installation을 참조합니다.

### 서버 콘텐츠와 클라이언트 기록 분리

MissionPack과 Mission은 서버가 작성하고 배포하는 콘텐츠입니다. Trip과 MissionResult는 Android가 차량 운행 중 생성하고 네트워크 복구 후 서버에 동기화하는 기록입니다. 이 경계를 기준으로 식별자 생성 주체와 변경 정책을 구분합니다.

### DB와 서비스의 검증 책임 분리

NULL 여부, 참조 무결성, 단일 프로필, 코드 중복, Enum 허용값, 기본적인 기간 순서처럼 한 행 또는 FK만으로 판단할 수 있는 불변식은 DB 제약조건으로 보호합니다. 여러 엔티티의 상태와 요청 전체를 함께 확인해야 하는 규칙은 서비스 계층에서 검증합니다.

### 확정되지 않은 기능은 스키마에 미리 넣지 않음

경험치, 배지, 레벨, 가족 계정, 다중 프로필, 음성 파일 메타데이터는 현재 정책과 사용 흐름이 확정되지 않았으므로 테이블과 nullable 컬럼을 미리 추가하지 않습니다.

## 3. 도메인 구성

| 엔티티 | 역할 | 주요 관계 |
| --- | --- | --- |
| Installation | 앱 설치 식별과 설치 인증 상태 관리 | Copilot 1:0..1, Trip 1:N |
| Copilot | 설치 단위의 단일 부기장 프로필 | Installation 공유 PK |
| MissionPack | 미션 콘텐츠의 코드와 버전 및 배포 상태 관리 | Mission 1:N, Trip 1:N |
| Mission | 미션 발동 조건과 연령대별 콘텐츠 정의 | MissionPack N:1, MissionResult 1:N |
| Trip | Android가 생성한 종료 주행 기록 | Installation N:1, MissionPack N:1, MissionResult 1:N |
| MissionResult | 한 번의 미션 수행 결과 | Trip N:1, Mission N:1 |
| AgreementDocument | 사용자에게 제시한 약관·동의문 원문과 버전 관리 | AgreementHistory 1:N |
| AgreementHistory | 설치 단위의 동의·철회 이벤트 기록 | Installation N:1, AgreementDocument N:1 |

JPA 연관관계는 자식에서 부모를 참조하는 단방향 매핑을 기본으로 사용합니다. 부모 컬렉션을 항상 유지하지 않아도 FK로 관계를 표현할 수 있고, 불필요한 양방향 연관관계 관리와 대량 컬렉션 로딩을 피하기 위한 선택입니다.

## 4. Installation과 Copilot

### Installation은 차량 ID가 아님

Installation은 앱 설치를 식별합니다. 같은 차량이라도 앱을 재설치하면 다른 Installation이 될 수 있으며, 하나의 Installation을 여러 실제 차량과 연결하는 정책도 현재 모델에는 없습니다.

### 설치 토큰 원문을 저장하지 않음

`installations.token_hash`에는 설치 인증 토큰의 SHA-256 16진수 해시만 저장합니다. DB는 64자리 소문자 16진수 형식을 CHECK 제약으로 검증하며 UNIQUE 제약으로 동일한 해시의 중복 등록을 막습니다.

Installation UUID는 식별자일 뿐 인증 수단이 아닙니다. 실제 요청 인증에서는 제시된 토큰을 동일한 방식으로 해시한 뒤 저장된 값과 안전하게 비교해야 합니다. 토큰 만료, 회전 및 이력 정책은 현재 스키마 범위가 아닙니다.

### Copilot은 Installation 공유 PK 사용

`copilots.installation_id`를 PK와 FK로 함께 사용합니다. 별도 Copilot ID 없이 Installation 하나당 Copilot 행을 최대 하나만 저장하도록 DB에서 보장합니다.

연령대는 다음 세 값으로 제한합니다.

- `AGE_5_6`
- `AGE_7_8`
- `AGE_9_10`

미션 강도는 `LOW`, `NORMAL`, `HIGH`를 사용하며 기본값은 `NORMAL`입니다. 강도별 실제 실행 간격과 안전 조건 우선순위는 Android 및 미션 실행 정책에서 정의해야 합니다.

## 5. MissionPack과 Mission

### 콘텐츠 버전과 스키마 버전 분리

MissionPack의 `version`은 미션 콘텐츠 개정 번호이고 `schema_version`은 Android가 JSON 콘텐츠를 해석하는 규격 버전입니다. 문구나 미션 구성이 바뀌면 콘텐츠 버전을 올릴 수 있고, JSON 구조나 연산자 계약이 바뀌면 스키마 버전도 별도로 올릴 수 있습니다.

`(code, version)` UNIQUE 제약은 동일한 미션팩 버전이 중복 등록되는 것을 막습니다. `version`과 `schema_version`은 모두 양수만 허용합니다.

### 발행된 콘텐츠는 수정하지 않음

MissionPack 상태는 `DRAFT`, `PUBLISHED`, `ARCHIVED`입니다. 발행된 미션팩을 직접 수정하지 않고 변경이 필요하면 새로운 콘텐츠 버전을 만드는 것을 원칙으로 합니다.

현재 DB는 허용 상태값만 검증하며 발행 이후 UPDATE 자체를 막지는 않습니다. PUBLISHED 또는 ARCHIVED 상태의 MissionPack과 소속 Mission을 변경하지 못하게 하는 규칙은 콘텐츠 관리 서비스에서 구현해야 합니다.

### Mission 설정은 JSONB로 관리

미션의 발동 세부 조건, 완료 조건, 실행 간격, 유효 시간, Trip당 최대 횟수, 연령대별 질문과 정답 판정 방식은 `missions.config` JSONB에 저장합니다. DB는 최상위 값이 JSON 객체인지 검증합니다.

관계형 컬럼으로 분리하지 않은 이유는 연령대별 질문과 미션 종류별 조건 구조가 서로 다르고 초기 스키마가 아직 진화할 가능성이 크기 때문입니다. 반대로 JSONB 내부 값은 일반 FK나 CHECK 제약으로 충분히 보호하기 어려우므로 저장 전 애플리케이션의 `schema_version`별 검증이 필요합니다.

현재 JSON 내부 검색 요구사항이 없어 GIN 인덱스는 만들지 않습니다. 실제 조회 조건이 생기기 전에 인덱스를 추가하면 쓰기 비용과 관리 비용만 증가할 수 있습니다.

### Mission 코드는 미션팩 안에서 유일함

Mission의 `code`는 전역 식별자가 아닙니다. `(mission_pack_id, code)` UNIQUE 제약으로 같은 미션팩 버전 안에서만 유일하도록 관리합니다. 다른 MissionPack 버전에는 같은 코드를 다시 사용할 수 있습니다.

## 6. Trip과 MissionResult

### 종료된 Trip만 서버에 저장

현재 서버 모델은 Android에서 종료 또는 중단이 확정된 Trip을 동기화하는 구조입니다. 진행 중 상태는 Android가 관리하므로 TripStatus에는 `IN_PROGRESS`를 두지 않습니다.

Trip 상태는 `COMPLETED`, `INTERRUPTED`이고 종료 사유는 다음 값을 사용합니다.

- 정상 종료 후보: `PARKING_BRAKE`, `STOP_TIMEOUT`, `MANUAL_STOP`
- 중단 종료 후보: `APP_INTERRUPTED`, `SDK_DISCONNECTED`, `UNKNOWN`

DB는 각 Enum의 허용값만 검증합니다. `COMPLETED`와 중단 사유처럼 의미가 맞지 않는 조합은 주행 동기화 서비스에서 거부해야 합니다.

### 비정상 종료 Trip의 복구 기준은 미확정

`ended_at`과 `end_reason`이 필수이므로 앱 강제 종료 등으로 종료 시점을 남기지 못한 Trip은 Android가 두 값을 복구해야 동기화할 수 있습니다. 종료 사유는 기존 중단 사유 값을 사용하며, 어떤 값을 고를지와 `ended_at`을 어떻게 추정할지는 Android 동기화 계약에서 확정합니다.

복구 기준은 다음 조건을 만족해야 합니다.

- 복구한 값은 한 번 확정한 뒤 재전송할 때 바꾸지 않습니다. 재시도마다 값이 달라지면 같은 Trip이 충돌로 판정될 수 있습니다.
- `ended_at`은 `started_at` 이후이면서 소속 MissionResult의 수행 시간을 포함해야 합니다. 허용 오차는 [시간 범위 검증](#시간-범위-검증) 정책을 따릅니다.

### 사용한 MissionPack 버전을 Trip에 고정

Trip은 수행 당시 사용한 MissionPack을 NOT NULL FK로 참조합니다. 서버의 최신 미션팩이 바뀌어도 과거 주행이 어떤 콘텐츠 버전을 기준으로 실행됐는지 추적할 수 있습니다.

MissionResult의 Mission이 Trip의 MissionPack에 속하는지는 두 개의 독립 FK만으로 보장되지 않습니다. 동기화 서비스는 다음 관계가 동일한 MissionPack을 가리키는지 확인해야 합니다.

```text
MissionResult -> Mission -> MissionPack
MissionResult -> Trip -> MissionPack
```

### 동일 미션 반복 수행 허용

MissionResult PK는 Android가 생성한 UUID입니다. 한 Trip에서 같은 Mission을 여러 번 수행해도 각 수행 건이 서로 다른 UUID를 가지므로 별도의 `(trip_id, mission_id)` UNIQUE 제약을 두지 않습니다.

동일 MissionResult UUID의 재전송은 같은 수행 건의 중복 동기화로 판단할 수 있습니다. 그러나 UUID가 같아도 내용이 다르면 단순 성공 처리하면 안 되며 Trip의 `payload_hash`와 정규화된 요청 비교 정책으로 충돌을 판별해야 합니다.

### payload hash의 역할 제한

`trips.payload_hash`는 동일 Trip UUID로 받은 데이터가 기존 저장 내용과 같은지 판단하기 위한 SHA-256 해시입니다. 서로 다른 Trip이 같은 결과를 가질 수 있으므로 UNIQUE 제약을 두지 않습니다.

해시는 위변조 방지나 요청 인증 수단이 아닙니다. 동기화 API에서 Trip과 MissionResult 목록을 결정적인 순서와 표현으로 정규화한 뒤 서버가 계산해야 합니다. 구체적인 정규화 범위와 배열 정렬 규칙은 동기화 API 계약에서 확정합니다.

### 재전송 처리 주의사항

Trip과 MissionResult는 저장 뒤 수정하지 않는 모델이므로 재전송이 기존 행을 바꾸면 안 됩니다. 그런데 현재 매핑에서는 ID를 직접 할당하기 때문에 Spring Data `save()`가 INSERT가 아니라 merge로 동작하고, 같은 UUID로 호출하면 오류 없이 기존 행을 덮어씁니다.

| 엔티티 | 덮어쓰기로 바뀔 수 있는 값 | 위험 |
| --- | --- | --- |
| Trip | `installation_id`, `status`, `end_reason`, `payload_hash` 등 | 다른 설치가 기존 주행 기록을 가로챔 |
| MissionResult | `trip_id`, `mission_id`, `status` 등 | 수행 기록이 다른 Trip으로 옮겨감 |

동기화 서비스는 `save()`에 재전송 판별을 맡기지 않고 기존 행을 조회해 판정한 뒤 INSERT 전용 방식으로 저장해야 합니다. 판정할 때는 소유권 확인을 payload 비교보다 먼저 수행하며, MissionResult UUID는 Trip의 `payload_hash`와 별개로 기존 행의 `trip_id`를 확인합니다.

동기화 서비스 통합 테스트는 최소한 다음 경우를 검증합니다.

| 상황 | 기대 결과 |
| --- | --- |
| 같은 Installation이 같은 UUID와 같은 payload를 재전송 | 중복 저장 없이 성공 처리 |
| 같은 Installation이 같은 Trip UUID에 다른 payload를 전송 | 충돌로 처리하고 기존 행 유지 |
| 저장된 MissionResult UUID가 다른 Trip으로 전달 | 거부하고 기존 행 유지 |
| 다른 Installation이 저장된 Trip UUID를 전송 | payload가 같아도 거부하고 기존 행 유지 |
| 같은 Trip을 동시에 재전송 | 한 건만 저장하고 나머지는 위 규칙으로 판정 |
| Trip과 MissionResult 저장 중 일부 실패 | 일부만 남지 않도록 한 트랜잭션으로 저장 |

### 참여 여부와 정답 여부 분리

`participated`는 음성 응답 참여 여부이고 nullable `is_correct`는 정답 판정 결과입니다. `is_correct = NULL`은 오답이 아니라 정답 판정이 없거나 적용되지 않았다는 뜻입니다.

| 상황 | participated | is_correct |
| --- | --- | --- |
| 정답 응답 | true | true |
| 오답 응답 | true | false |
| 판정 없는 응답 | true | NULL |
| 무응답 또는 안내형 미션 | false | NULL |

DB는 `participated = false`인 경우 `is_correct`가 NULL이도록 제한합니다. 상태가 EXPIRED 또는 CANCELLED일 때의 참여 및 정답 조합처럼 더 구체적인 정책은 서비스 계층에서 검증합니다.

### 시간 범위 검증

Trip과 MissionResult 모두 `ended_at >= started_at`을 DB CHECK 제약으로 보장합니다. MissionResult 시간이 소속 Trip 범위 안에 있는지는 다른 행을 함께 확인해야 하므로 서비스 계층 검증 대상입니다. SDK 중단과 취소 기록 시각 사이의 허용 오차는 Android 계약이 확정된 뒤 정의합니다.

## 7. 식별자 전략

모든 PK를 같은 타입으로 통일하지 않습니다.

- Installation과 Copilot: UUID
- Trip과 MissionResult: Android 생성 UUID
- MissionPack과 Mission: DB 생성 BIGINT IDENTITY
- AgreementDocument: DB 생성 BIGINT IDENTITY
- AgreementHistory: Android 생성 UUID, 재전송에도 동일 이벤트 ID 유지

Trip과 MissionResult는 Offline-first 동기화를 위해 서버 연결 없이 식별자를 생성해야 합니다. MissionPack과 Mission은 서버 내부 콘텐츠이므로 BIGINT가 단순합니다. 세부 판단과 대안은 [핵심 도메인 식별자 설계](../erd/id-strategy.md)에 기록합니다.

## 8. 시각과 변경 이력

Java에서는 `Instant`, PostgreSQL에서는 `TIMESTAMPTZ`를 사용해 절대 시점을 저장합니다. 지역 날짜 기준 집계가 필요하면 조회 또는 집계 계층에서 Asia/Seoul 기준으로 변환합니다.

Installation, Copilot, MissionPack, Mission, AgreementDocument는 생성 및 수정 시각을 저장합니다. Trip, MissionResult, AgreementHistory는 서버에 최종 기록으로 저장한 뒤 수정하지 않는 모델이므로 생성 시각만 저장합니다. AgreementHistory의 `occurred_at`은 실제 행위 시각이고 `created_at`은 서버 기록 시각이므로 구분합니다. 정정 기능을 도입한다면 수정 허용 범위와 감사 이력을 먼저 설계해야 합니다.

JPA Auditing이 애플리케이션 INSERT와 UPDATE의 시각을 채우고 DB의 `now()` 기본값은 직접 SQL 입력에 대한 보조 장치로 사용합니다. DB 기본값만으로는 UPDATE 시각이 자동 갱신되지 않습니다.

## 9. 삭제와 보존 정책

초기 FK에는 `ON DELETE CASCADE`를 사용하지 않습니다. 부모 데이터 삭제가 과거 주행이나 미션 수행 기록까지 연쇄 삭제하는 것을 방지하기 위해 기본 RESTRICT 동작을 유지합니다.

MissionPack은 삭제 대신 ARCHIVED 상태를 사용합니다. Installation과 Copilot의 실제 삭제 및 보존 기간은 개인정보 처리 정책이 확정된 뒤 별도로 설계해야 합니다.

## 10. 인덱스 정책

PK와 UNIQUE 제약은 PostgreSQL이 고유 인덱스를 생성하므로 같은 컬럼 조합의 일반 인덱스를 중복 생성하지 않습니다. 현재 추가한 조회용 인덱스는 다음과 같습니다.

| 인덱스 | 목적 |
| --- | --- |
| `(mission_packs.status, version DESC)` | 배포 상태별 최신 버전 조회 |
| `(missions.mission_pack_id)` | 미션팩 소속 미션 조회 |
| `(trips.installation_id, started_at DESC)` | 설치별 최신 주행 이력 조회 |
| `(mission_results.trip_id)` | 주행 상세 결과 조회 |
| `(mission_results.mission_id)` | 미션별 수행 결과 조회 |
| `(agreement_histories.installation_id, created_at DESC)` | 설치별 최신 서버 기록 이력 조회 |
| `(agreement_histories.agreement_document_id)` | 문서 버전별 동의·철회 이력 조회 |

실제 Repository 조회 패턴이 달라지면 실행 계획과 데이터 분포를 확인한 뒤 인덱스를 조정합니다.

## 11. Flyway와 JPA 스키마 검증

운영 스키마의 기준은 Flyway 마이그레이션입니다. local과 prod에서는 Hibernate `ddl-auto=validate`로 엔티티 매핑과 실제 DB 타입이 일치하는지 확인하고 Hibernate가 운영 스키마를 자동 변경하지 않도록 합니다.

SHA-256 해시 컬럼은 PostgreSQL `CHAR(64)`를 사용합니다. Hibernate 7이 Java String을 기본적으로 VARCHAR로 판단하지 않도록 엔티티에는 `@JdbcTypeCode(SqlTypes.CHAR)`를 명시합니다.

테스트 프로필은 H2와 `create-drop`을 사용합니다. H2는 PostgreSQL JSONB를 그대로 지원하지 않으므로 테스트 전용 JSONB 도메인 별칭을 사용합니다. H2 Repository 테스트는 JPA 매핑과 일부 제약을 확인하지만 PostgreSQL 전용 정규식, JSONB 함수, 인덱스 문법의 완전한 대체 검증은 아닙니다. 마이그레이션 변경 시 실제 PostgreSQL 검증이 필요합니다.

## 12. 향후 음성 응답 확장

현재는 STT 원문과 음성 파일을 서버에 저장하지 않습니다. MissionResult에 `audio_url` 같은 nullable 컬럼을 미리 추가하지 않습니다.

한 미션 수행에서 인식 실패와 재질문으로 여러 음성 응답이 발생할 수 있으므로 저장 기능이 필요해지면 `MissionVoiceResponse`를 별도 엔티티로 추가하고 MissionResult와 1:N으로 연결합니다. 개별 응답에는 시도 순서, STT 상태, transcript, 객체 스토리지 키와 파일 메타데이터를 둘 수 있습니다.

음성 저장 기능을 도입하기 전에는 Android의 원본 오디오 접근 가능 여부, 업로드 실패 처리, 아동 음성의 수집 동의, 보관 기간 및 삭제 정책을 먼저 확정해야 합니다.

## 13. 현재 구현 범위에서 제외한 항목

- 경험치, 보상 원장, 배지 및 레벨
- 가족 계정과 다중 프로필
- 진행 중 Trip 서버 저장
- 음성 파일과 STT 원문 저장
- 설치 토큰 만료, 회전 및 이력
- MissionPack 발행 이후 불변성 서비스 로직
- Trip 동기화 payload 정규화와 해시 계산
- Trip 상태와 종료 사유 조합 검증
- Trip과 Mission의 MissionPack 일치 검증
- MissionResult와 Trip의 시간 범위 검증
- 비정상 종료 Trip의 종료 시각과 사유 복구 기준
- Trip과 MissionResult 재전송 처리와 통합 테스트
- MissionPack 발행 시 최소 미션 수 정책
- 동의 API, 발행 문서 수정 차단, 현재 동의 상태 판정과 이벤트 재전송 처리
- 실제 동의 대상 확정과 법정대리인 확인 절차

제외 항목은 스키마 누락이 아니라 정책 또는 API 흐름이 확정된 뒤 구현할 의도적인 후속 범위입니다.

## 14. 변경 시 확인할 체크리스트

- 엔티티 필드 타입과 Flyway 컬럼 타입이 일치하는가
- Java Enum 변경과 DB CHECK 허용값을 함께 갱신했는가
- Mission JSON 구조 변경 시 schema_version 호환성을 검토했는가
- 새로운 UNIQUE 제약이 동일 미션 반복 수행이나 오프라인 재전송을 막지 않는가
- FK 삭제 정책이 과거 주행 및 수행 기록을 보존하는가
- 새 인덱스가 실제 조회 조건과 정렬 순서를 지원하는가
- Android 생성 값에 대한 서버 검증과 소유권 확인 지점이 있는가
- 변경된 PostgreSQL 전용 기능을 실제 PostgreSQL에서 검증했는가
- ERD, 식별자 문서와 본 설계 문서를 함께 갱신했는가
