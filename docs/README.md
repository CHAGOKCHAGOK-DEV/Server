# 프로젝트 문서

## 핵심 도메인

- [주행·미션 결과 동기화 API](./api/trip-sync.md): 설치 인증, 요청·응답, 멱등성·동시성·롤백 및 PostgreSQL 테스트

- [동의 API](./api/agreement.md): 현재 문서·동의 상태 조회, 동의 이벤트 기록, 멱등성과 발행 문서 불변성
- [동의 도메인과 화면 계약](./architecture/agreement-domain-design.md): 동의 문서·이력 명세, 서버 저장 전환과 후속 정책

- [핵심 도메인 설계](./architecture/core-domain-design.md): 엔티티 책임, 관계, DB 제약, 동기화와 확장 정책
- [핵심 도메인 ERD](./erd/core-domain.md): 테이블과 연관관계 시각화
- [핵심 도메인 식별자 설계](./erd/id-strategy.md): UUID와 BIGINT 선택 근거

구현과 문서가 다를 경우 Flyway 마이그레이션과 현재 엔티티 매핑을 먼저 확인하고, 의도된 변경이면 설계 문서도 함께 갱신합니다.
