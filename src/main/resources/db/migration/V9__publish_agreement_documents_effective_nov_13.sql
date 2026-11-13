-- Published agreement documents are immutable. Preserve version 1 and publish
-- version 2 with the effective date and the date in the legal text aligned.
DO $$
BEGIN
    IF (
        SELECT count(*)
        FROM agreement_documents
        WHERE code IN ('TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'CHILD_PRIVACY_COLLECTION')
          AND version = 1
          AND status = 'PUBLISHED'
          AND effective_at = TIMESTAMPTZ '2026-11-15 00:00:00+09'
          AND content LIKE '%2026년 11월 15일%'
    ) <> 3 THEN
        RAISE EXCEPTION 'Expected all three published version 1 agreement documents effective 2026-11-15';
    END IF;

    INSERT INTO agreement_documents (code, version, title, content, is_required, status, effective_at)
    SELECT code, 2, title,
           replace(content, '2026년 11월 15일', '2026년 11월 13일'),
           is_required, 'PUBLISHED', TIMESTAMPTZ '2026-11-13 00:00:00+09'
    FROM agreement_documents
    WHERE code IN ('TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'CHILD_PRIVACY_COLLECTION')
      AND version = 1;
END
$$;
