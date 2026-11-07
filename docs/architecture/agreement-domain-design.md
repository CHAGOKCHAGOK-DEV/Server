# 동의 도메인 설계

## 책임과 관계

기존 6개 엔티티에 AgreementDocument와 AgreementHistory를 추가해 총 8개 엔티티로 구성합니다. S-03 부모 동의는 프로필 생성 이전에 진행하므로 이력은 Copilot이 아닌 Installation에 연결합니다. Installation과 AgreementDocument는 각각 AgreementHistory와 1:N 관계이며 Installation과 Copilot의 1:0..1 관계는 유지합니다.

## AgreementDocument

`agreement_documents`는 제시한 문서의 원문과 버전을 보관합니다.

| 컬럼 | PostgreSQL / Java | NULL | 기본값 / 설명 |
| --- | --- | --- | --- |
| id | BIGINT / Long | 불가 | IDENTITY PK |
| code | VARCHAR(50) / String | 불가 | 문서 종류 |
| version | INTEGER / Integer | 불가 | 양수, `(code, version)` UNIQUE |
| title | VARCHAR(150) / String | 불가 | 제목 |
| content | TEXT / String | 불가 | 실제 제시한 원문 |
| is_required | BOOLEAN / boolean | 불가 | DB 기본값 FALSE, 생성자에서 명시 |
| status | VARCHAR(20) / AgreementDocumentStatus | 불가 | DRAFT 기본값, PUBLISHED, ARCHIVED |
| effective_at | TIMESTAMPTZ / Instant | 허용 | 적용 시작 시각 |
| created_at | TIMESTAMPTZ / Instant | 불가 | 서버 생성 시각 |
| updated_at | TIMESTAMPTZ / Instant | 불가 | 서버 수정 시각 |

`code`는 실제 동의 대상이 확정되지 않아 문자열로 둡니다. `TERMS_OF_SERVICE`, `PRIVACY_COLLECTION_USE`, `AUDIO_RECORDING`은 가능한 예시이며 기본 데이터로 등록하지 않습니다. 개인정보처리방침 고지와 개인정보 수집·이용 동의는 목적을 구분합니다.

발행된 문서의 원문, 제목, 필수 여부 등 동의 조건은 보존하고 변경 시 새 버전을 생성합니다. 현재 엔티티에는 수정 메서드가 없지만 SQL UPDATE나 발행 이후 변경을 DB가 차단하지는 않습니다. 발행·보관 상태 전이, 적용 시각 검증과 발행 이후 수정 차단은 후속 관리 서비스에서 구현합니다.

## AgreementHistory

`agreement_histories`는 동의와 철회를 별도 이벤트로 보관합니다.

| 컬럼 | PostgreSQL / Java | NULL | 설명 |
| --- | --- | --- | --- |
| id | UUID / UUID | 불가 | Android가 생성하고 재전송 시 유지하는 이벤트 PK |
| installation_id | UUID / Installation | 불가 | installations.id FK |
| agreement_document_id | BIGINT / AgreementDocument | 불가 | agreement_documents.id FK |
| action | VARCHAR(20) / AgreementAction | 불가 | ACCEPTED 또는 WITHDRAWN |
| occurred_at | TIMESTAMPTZ / Instant | 불가 | 실제 동의·철회 시각 |
| created_at | TIMESTAMPTZ / Instant | 불가 | 서버 기록 시각 |
| verification_method | VARCHAR(50) / String | 허용 | 실제 법정대리인 확인 방법 |
| verified_at | TIMESTAMPTZ / Instant | 허용 | 실제 확인 완료 시각 |

두 FK는 LAZY 단방향 매핑이며 삭제를 전파하지 않습니다. `(installation_id, created_at DESC)`와 `(agreement_document_id)` 인덱스를 둡니다. 동의 후 철회하거나 다른 버전에 동의하면 기존 행을 덮어쓰지 않고 이벤트를 추가합니다. 동의 이력과 문서의 보존 기간, 개인정보 삭제 요청 처리 정책은 별도 확정이 필요합니다.

Hibernate `@Immutable`로 일반 엔티티 변경과 merge에 따른 기존 이력 UPDATE를 방지합니다. 이 설정은 SQL·벌크 수정이나 삭제까지 막는 DB 권한 장치가 아닙니다. Repository는 영속성 접근 수단이며 동의 처리 API가 아닙니다.

UUID PK는 중복 행을 막지만 재전송 내용의 일치 여부나 권한을 검증하지 않습니다. 후속 서비스는 인증된 Installation을 기준으로 소유권을 먼저 확인한 뒤 이벤트 ID와 문서·행위·발생 시각·확인 정보를 비교해야 합니다. 같은 이벤트의 동일 내용 재전송만 성공 처리하고 다른 내용은 충돌로 거부합니다. 새 이벤트는 INSERT 전용으로 기록하고 동시 요청의 PK 충돌도 같은 기준으로 재판정해야 합니다.

늦게 도착한 ACCEPTED가 이미 발생한 WITHDRAWN을 되돌리지 않도록 현재 동의 상태의 순서 정책을 별도로 확정해야 합니다. `created_at` 인덱스는 서버 수신 이력 조회용이며 현재 유효 동의를 판정하는 기준이 아닙니다. 클라이언트 `occurred_at`의 허용 오차, 동일 시각 이벤트 순서, 철회 범위와 재동의 정책은 아직 구현하지 않았습니다.

`verification_method`와 `verified_at`은 실제 확인 절차를 완료한 경우에만 서버에서 기록합니다. 단순 체크박스 선택을 법정대리인 확인 완료로 간주하거나 클라이언트가 보낸 확인 결과를 그대로 신뢰하지 않습니다. 이 스키마만으로 법정대리인 동의 확인 요건 충족을 주장하지 않습니다.

## 화면설계서에 반영할 계약

제공된 설명을 기준으로 정리한 변경 사항이며 원본 PDF를 직접 수정하거나 검증하지 않았습니다.

- S-03의 `consentAcceptedAt`, `consentVersion`, 체크 항목 로컬 저장은 동의 이벤트의 임시 보관과 재전송 용도로 정리하고, 최종 이력은 서버의 문서 버전 FK와 이벤트로 저장합니다. API와 동기화는 후속 구현입니다.
- “아이의 별명을 이용해요”, “목소리는 인식 후 바로 지워요”, “주행 기록은 가족 안에서만 써요”는 설명 문구이며 자동으로 문서 3개에 대응시키지 않습니다. 별도 동의가 필요한 처리 목적을 확정한 뒤 문서를 구분합니다.
- 별명과 주행·미션 기록의 서버 저장 범위를 안내 문구에 반영합니다. 음성 삭제 안내는 실제 STT 제공자의 처리·보관 정책을 확인한 뒤 확정합니다.
- 프로필은 서버 Copilot 저장과 `ageGroup` 필수 정책을 유지합니다. 화면의 연령대 입력 제외·로컬 전용 저장 설명은 이 정책에 맞춰 수정해야 합니다. 연령대 값은 AGE_5_6, AGE_7_8, AGE_9_10입니다.
- 동의 대상, 법정대리인 확인 방법, 철회 시 서비스 제한·삭제 처리, 보존 기간은 별도 기획 과제로 남깁니다. 구체적인 법령 해석은 이번 구현에서 확정하지 않습니다.

## 마이그레이션과 검증

아직 서버에 적용하지 않은 초기 스키마이므로 동의 테이블도 V1에 포함합니다. 빈 DB에서 V1 한 번으로 8개 테이블을 생성합니다. 이전 V1을 적용한 개인 로컬 DB는 체크섬이 달라지므로 보존할 데이터가 없는 개발 DB에 한해 재생성 후 적용해야 합니다. 이미 공유·운영 DB에 적용한 마이그레이션은 수정하지 않고 이후 버전을 추가하는 원칙을 따릅니다. 동의 이력 삭제를 자동 롤백으로 사용하지 않습니다.

H2 Repository 테스트는 문서 버전 UNIQUE, 두 FK와 이력 저장·불변성을 확인합니다. PostgreSQL의 CHECK, 기본값, 내림차순 인덱스와 Flyway 적용은 실제 PostgreSQL 검증이 별도로 필요합니다.

2026-11-07에는 Docker Compose의 별도 검증 DB에서 PostgreSQL 17.11로 V1 Flyway 적용과 Hibernate `validate`, 앱 Health `UP`을 확인했습니다. 실제 DB에서 문서 버전 UNIQUE, 양수 버전·상태·행위 CHECK, 이벤트 UUID 중복 거부, 두 FK, 참조 부모 삭제 제한, 시각·상태·필수 여부 기본값과 두 인덱스를 검증했고 검사 데이터는 트랜잭션 롤백으로 정리했습니다. 전체 자동 테스트 11개도 통과했습니다. 이 결과는 아직 구현하지 않은 동의 API나 법정대리인 확인 절차의 검증을 포함하지 않습니다.
