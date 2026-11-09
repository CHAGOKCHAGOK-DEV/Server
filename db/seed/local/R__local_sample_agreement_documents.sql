-- 로컬 개발 전용 샘플이며 실제 약관 아님, jar와 이미지에 미포함
INSERT INTO agreement_documents (code, version, title, content, is_required, status, effective_at)
VALUES
    ('LOCAL_SAMPLE_REQUIRED', 1, '[로컬 샘플] 필수 동의', '로컬 개발용 샘플 원문입니다. 실제 약관이 아닙니다.', TRUE, 'PUBLISHED', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('LOCAL_SAMPLE_REQUIRED', 2, '[로컬 샘플] 필수 동의 개정 예정', '적용 예정 버전 확인용 샘플 원문입니다. 실제 약관이 아닙니다.', TRUE, 'PUBLISHED', TIMESTAMPTZ '2099-01-01 00:00:00+00'),
    ('LOCAL_SAMPLE_OPTIONAL', 1, '[로컬 샘플] 선택 동의', '선택 동의 확인용 샘플 원문입니다. 실제 약관이 아닙니다.', FALSE, 'PUBLISHED', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
    ('LOCAL_SAMPLE_DRAFT', 1, '[로컬 샘플] 작성 중 문서', '조회되지 않아야 하는 DRAFT 샘플입니다.', TRUE, 'DRAFT', NULL)
ON CONFLICT (code, version) DO NOTHING;
