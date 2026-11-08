# 핵심 도메인 ERD

전체 도메인 결정과 제약조건의 근거는 [핵심 도메인 설계](../architecture/core-domain-design.md)를 참고합니다. 식별자 타입과 생성 주체의 세부 근거는 [핵심 도메인 식별자 설계](./id-strategy.md)에 기록합니다.

```mermaid
erDiagram
    INSTALLATIONS ||--o| COPILOTS : owns
    INSTALLATIONS ||--o{ TRIPS : records
    INSTALLATIONS ||--o{ AGREEMENT_HISTORIES : records
    AGREEMENT_DOCUMENTS ||--o{ AGREEMENT_HISTORIES : referenced_by
    MISSION_PACKS ||--o{ MISSIONS : contains
    MISSION_PACKS ||--o{ TRIPS : used_by
    TRIPS ||--o{ MISSION_RESULTS : contains
    MISSIONS ||--o{ MISSION_RESULTS : produces

    AGREEMENT_DOCUMENTS {
        bigint id PK
        varchar code UK
        integer version UK
        varchar title
        text content
        boolean is_required
        varchar status
        timestamptz effective_at
        timestamptz created_at
        timestamptz updated_at
    }
    AGREEMENT_HISTORIES {
        uuid id PK
        uuid installation_id FK
        bigint agreement_document_id FK
        varchar action
        timestamptz occurred_at
        timestamptz created_at
        varchar verification_method
        timestamptz verified_at
    }
    INSTALLATIONS {
        uuid id PK
        char token_hash UK
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }
    COPILOTS {
        uuid installation_id PK,FK
        varchar nickname
        varchar age_group
        varchar mission_intensity
        timestamptz created_at
        timestamptz updated_at
    }
    MISSION_PACKS {
        bigint id PK
        varchar code UK
        integer version UK
        integer schema_version
        varchar status
        timestamptz published_at
        timestamptz created_at
        timestamptz updated_at
    }
    MISSIONS {
        bigint id PK
        bigint mission_pack_id FK,UK
        varchar code UK
        varchar trigger_type
        jsonb config
        boolean enabled
        timestamptz created_at
        timestamptz updated_at
    }
    TRIPS {
        uuid id PK
        uuid installation_id FK
        bigint mission_pack_id FK
        varchar status
        varchar end_reason
        timestamptz started_at
        timestamptz ended_at
        char payload_hash
        timestamptz created_at
    }
    MISSION_RESULTS {
        uuid id PK
        uuid trip_id FK
        bigint mission_id FK
        varchar status
        boolean participated
        boolean is_correct
        timestamptz started_at
        timestamptz ended_at
        timestamptz created_at
    }
```

`agreement_documents`의 `(code, version)`은 복합 UNIQUE 제약입니다. 동의 이력은 프로필 생성 전에도 남겨야 하므로 Copilot과 직접 연결하지 않습니다. 상세 명세와 미구현 정책은 [동의 도메인과 화면 계약](../architecture/agreement-domain-design.md)에 기록합니다.

`mission_packs`의 `(code, version)`과 `missions`의 `(mission_pack_id, code)`는 각각 복합 UNIQUE 제약조건입니다. `MissionVoiceResponse`는 음성 저장 정책이 확정된 뒤 `MissionResult`와 1:N 관계로 추가할 확장 대상이며 현재 스키마에는 포함하지 않습니다. 스키마는 미션이 없는 MissionPack을 허용하며, 발행 시 최소 미션 수는 [핵심 도메인 설계](../architecture/core-domain-design.md)의 후속 범위입니다.
