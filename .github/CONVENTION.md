# 설문고 협업 규칙

## 커밋

```text
<type>: <변경 내용> (#<issue-number>)
```

사용 가능한 type은 `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `ci`, `perf`, `style`, `infra`입니다.

예: `feat: 설문 생성 API 구현 (#12)`

## 브랜치

```text
<type>/#<issue-number>-<kebab-case-description>
```

예: `feature/#12-create-survey`

## 작업 단위

- 하나의 이슈와 브랜치는 하나의 명확한 작업을 다룹니다.
- PR에는 관련 없는 변경을 섞지 않습니다.
- 기능 동작, 테스트 결과, 마이그레이션 영향을 PR 본문에 기록합니다.
- 이슈를 종료하는 PR은 `Closes #번호`를 사용합니다.

