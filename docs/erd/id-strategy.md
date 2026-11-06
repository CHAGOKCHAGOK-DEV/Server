# 핵심 도메인 식별자 설계

## 1. 목적

핵심 도메인의 PK를 하나의 타입으로 통일하지 않고, 식별자를 생성하는 주체와 오프라인 동기화 필요 여부에 따라 UUID와 BIGINT를 구분합니다.

이번 설계에서 가장 중요한 판단 기준은 엔티티가 외부에 노출되는지가 아니라 서버에 연결되지 않은 상태에서도 식별자를 생성해야 하는지입니다.

## 2. PK 구성

| 엔티티 | PK 타입 | 생성 주체 | 선택 이유 |
| --- | --- | --- | --- |
| Installation | UUID | 서버 | 클라이언트가 장기간 보관하는 설치 식별자 사용 |
| Copilot | UUID | Installation과 공유 | 독립 식별자 없이 설치당 프로필 최대 1개 보장 |
| MissionPack | BIGINT | DB | 서버 내부에서 관리하는 버전별 콘텐츠 식별 |
| Mission | BIGINT | DB | 서버 내부에서 관리하는 미션 정의 식별 |
| Trip | UUID | Android | 오프라인 상태에서 주행 시작과 동시에 식별자 생성 |
| MissionResult | UUID | Android | 오프라인 상태에서 각 미션 수행 건의 식별자 생성 |

UUID PK는 Installation, Copilot, Trip, MissionResult에 적용하고 BIGINT PK는 MissionPack과 Mission에 적용합니다. Copilot은 별도의 UUID를 생성하지 않고 Installation의 UUID를 PK이자 FK로 공유합니다.

## 3. Offline-first 동기화

Trip과 MissionResult는 차량 운행 중 네트워크 연결이 없더라도 Android에서 생성되어야 합니다. Android는 주행 시작 시 Trip UUID를 만들고, 미션이 수행될 때마다 MissionResult UUID를 만듭니다. 네트워크가 복구되면 생성한 UUID를 변경하지 않고 서버로 전송합니다.

```text
Android 오프라인 저장
  Trip A
    MissionResult B
    MissionResult C

네트워크 복구
  Android가 A, B, C를 서버로 전송
  서버가 동일한 UUID를 PostgreSQL PK로 저장
```

이 구조는 다음 요구사항을 충족합니다.

- 서버 연결 없이 주행과 미션 수행 건 식별
- 여러 MissionResult를 하나의 Trip에 연결
- 동일 미션의 반복 수행을 서로 다른 MissionResult UUID로 구분
- 동일 UUID 재전송 여부를 이용한 중복 동기화 판별
- 여러 설치에서 독립적으로 생성한 식별자의 충돌 가능성 최소화

UUID만으로 동기화 데이터의 동일성까지 보장할 수는 없습니다. 같은 Trip UUID가 기존 저장 내용과 다른 payload로 재전송되는 경우에는 동기화 서비스가 `payload_hash`를 비교해 동일 요청인지 충돌인지 판별해야 합니다.

## 4. Installation과 Copilot

Installation은 서버에서 생성하므로 기술적으로 BIGINT를 사용할 수도 있습니다. 현재는 클라이언트가 설치 식별자를 장기간 보관하고 향후 API 경계에서 사용할 가능성을 고려해 UUID를 사용합니다.

Installation UUID는 인증 수단이 아닙니다. 설치 인증은 원문을 저장하지 않는 별도의 설치 토큰과 `token_hash` 검증으로 처리해야 합니다.

Copilot은 Installation과 생명주기 및 소유 관계가 일치하고 독립적인 식별 요구가 없습니다. 따라서 `installation_id`를 PK와 FK로 함께 사용하는 공유 PK 구조를 적용합니다.

```text
installations
  id UUID PK

copilots
  installation_id UUID PK, FK -> installations.id
```

이 구조에서는 하나의 Installation에 둘 이상의 Copilot 행을 저장할 수 없습니다. Copilot에 독립적인 식별자가 필요해지는 요구사항이 생기면 별도 PK와 `installation_id UNIQUE` 구조를 다시 검토합니다.

## 5. MissionPack과 Mission

MissionPack과 Mission은 서버에서 작성하고 배포하는 내부 콘텐츠입니다. 오프라인 클라이언트가 새로운 콘텐츠 식별자를 생성할 필요가 없으므로 DB가 생성하는 BIGINT IDENTITY를 사용합니다.

MissionPack의 비즈니스 식별 기준은 PK가 아니라 `(code, version)` UNIQUE 제약입니다. Mission의 미션팩 내부 식별 기준은 `(mission_pack_id, code)` UNIQUE 제약입니다. BIGINT PK는 FK 연결과 내부 조회를 단순하게 유지하는 역할을 합니다.

## 6. UUID와 BIGINT의 선택 기준

| 판단 항목 | UUID | BIGINT |
| --- | --- | --- |
| 생성 위치 | 클라이언트와 서버 모두 가능 | 주로 DB 또는 서버 |
| 오프라인 생성 | 별도 서버 요청 없이 가능 | 별도 ID 발급 전략 필요 |
| 저장 크기 | 16바이트 | 8바이트 |
| 인덱스 크기 | 상대적으로 큼 | 상대적으로 작음 |
| 정렬과 로그 가독성 | 상대적으로 불편 | 단순함 |
| 분산 생성 | 충돌 가능성을 낮추기 쉬움 | 별도 생성 전략 필요 |

현재 규모에서는 UUID와 BIGINT의 미세한 성능 차이보다 생성 주체와 동기화 계약을 명확히 하는 것이 우선입니다.

## 7. 현재 범위와 후속 결정

현재 엔티티는 UUID 타입만 규정하며 UUID 버전을 강제하지 않습니다. Android와 서버가 사용할 UUID 버전을 확정하면 API 계약과 생성 정책에 명시해야 합니다. UUIDv7은 시간 순서에 가까운 인덱스 삽입 특성이 필요할 때 검토할 수 있지만 현재 구현의 전제는 아닙니다.

이번 설계에는 BIGINT PK와 별도의 `client_id UUID UNIQUE`를 함께 두지 않습니다. Trip과 MissionResult에서 서버 PK와 클라이언트 식별자를 분리해 얻는 이점이 현재 요구사항에는 없기 때문입니다.

## 8. 최종 결정

- Installation: 서버 생성 UUID 유지
- Copilot: Installation UUID 공유 PK 유지
- MissionPack: DB 생성 BIGINT 유지
- Mission: DB 생성 BIGINT 유지
- Trip: Android 생성 UUID 유지
- MissionResult: Android 생성 UUID 유지

Offline-first 데이터에는 클라이언트 생성 UUID를 사용하고 서버 관리 콘텐츠에는 BIGINT를 사용하는 혼합 전략을 유지합니다.
