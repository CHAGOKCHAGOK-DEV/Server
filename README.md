# 차곡차곡 Server

**차곡차곡**의 백엔드 서버입니다.

## 기술 스택

- Java 21
- Spring Boot 4.1
- Spring MVC, Spring Data JPA, Spring Security
- PostgreSQL 17, Flyway
- JWT (JJWT)
- SpringDoc OpenAPI
- Gradle, Docker, GitHub Actions

## 로컬 실행

### 1. 환경 변수 준비

```bash
cp .env.example .env
```

`.env`의 `DB_PASSWORD`와 `JWT_SECRET`을 로컬 환경에 맞게 설정합니다. JWT 비밀키는 충분히 긴 무작위 값을 사용하세요.

### 2. PostgreSQL 실행

```bash
docker compose -f docker-compose.local.yml up -d
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

Windows PowerShell에서는 `./gradlew` 대신 `./gradlew.bat`을 사용할 수 있습니다.

## 테스트

```bash
./gradlew test
```

주행 동기화의 PostgreSQL 통합 테스트는 기본 `test`에 포함되지 않으며, 전용 DB를 준비한 뒤 `./gradlew postgresTest`로 실행합니다. 환경 변수와 실행 절차는 [주행 동기화 API 문서](docs/api/trip-sync.md#검증-실행)를 참고하세요. CI에서는 빌드·일반 테스트와 PostgreSQL 통합 테스트를 모두 실행합니다.

## API 문서

로컬 서버 실행 후 아래 주소에서 확인할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health Check: `http://localhost:8080/api/v1/health` (서비스 상태), `http://localhost:8080/actuator/health` (DB 연결 포함)

## 프로젝트 구조

```text
src/main/java/com/chagokchagok/server
├── domain/   # 설문, 응답, 회원 등 비즈니스 도메인
└── global/   # 공통 설정, 예외, 응답, 보안, 로깅
```

새 기능은 `domain/{도메인명}` 아래에 controller, service, repository, entity, dto, exception 패키지로 나누어 작성합니다.

## 환경별 프로필

| 프로필 | 데이터베이스 | Flyway | JPA DDL |
| --- | --- | --- | --- |
| `local` | PostgreSQL | 사용 | validate |
| `test` | H2 | 미사용 | create-drop |
| `prod` | PostgreSQL | 사용 | validate |

운영 환경에서는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_ACCESS_TOKEN_EXPIRATION`, `JWT_REFRESH_TOKEN_EXPIRATION`을 주입해야 합니다.

## 배포

`develop`에 push되어 빌드·일반 테스트·PostgreSQL 통합 테스트가 모두 성공하면 CD 워크플로가 실행됩니다. Actions 탭에서 수동으로 실행할 수도 있습니다.

1. 해당 커밋으로 Docker 이미지를 빌드해 GitHub Container Registry(`ghcr.io`)에 푸시합니다.
2. SSH로 EC2에 접속해 [scripts/deploy.sh](scripts/deploy.sh)로 컨테이너를 교체합니다.
3. `/actuator/health`가 응답하지 않으면 직전 이미지로 되돌리고 워크플로를 실패 처리합니다.

### GitHub Secrets

| 이름 | 값 |
| --- | --- |
| `EC2_HOST` | EC2 공인 IP 또는 도메인 |
| `EC2_SSH_KEY` | EC2 접속용 개인 키(`.pem`) 전체 내용 |

### EC2 환경 변수

운영 환경 변수는 저장소나 GitHub에 두지 않고 EC2의 `~/chagokchagok/.env`에만 둡니다.

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<RDS 엔드포인트>:5432/chagokchagok?sslmode=require
DB_USERNAME=
DB_PASSWORD=
JWT_SECRET=
JWT_ACCESS_TOKEN_EXPIRATION=3600000
JWT_REFRESH_TOKEN_EXPIRATION=1209600000
TZ=Asia/Seoul
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50
```

배포 후 Swagger UI는 `http://<EC2_HOST>/swagger-ui.html`에서 확인할 수 있습니다.

## 협업 규칙

커밋, 브랜치, 이슈 및 PR 규칙은 [.github/CONVENTION.md](.github/CONVENTION.md)를 따릅니다.

저장소 코드의 사용 범위와 외부 공유 제한은 [NOTICE.md](NOTICE.md)를 확인해 주세요.
