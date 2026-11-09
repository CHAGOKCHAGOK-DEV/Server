ALTER TABLE agreement_documents
    ADD CONSTRAINT ck_agreement_documents_effective_at CHECK (status = 'DRAFT' OR effective_at IS NOT NULL);

-- 문서가 SQL로만 관리되어 발행 이후 동의 조건 변경과 삭제를 DB에서 차단
CREATE FUNCTION protect_agreement_documents() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'agreement document % is % and cannot be deleted', OLD.id, OLD.status
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN OLD;
    END IF;
    -- 동의 조건을 담는 컬럼 추가 시 이 목록에도 추가 필요
    IF OLD.status <> 'DRAFT'
            AND (NEW.id, NEW.code, NEW.version, NEW.title, NEW.content, NEW.is_required, NEW.effective_at, NEW.created_at)
            IS DISTINCT FROM
            (OLD.id, OLD.code, OLD.version, OLD.title, OLD.content, OLD.is_required, OLD.effective_at, OLD.created_at) THEN
        RAISE EXCEPTION 'agreement document % is % and its terms cannot be changed', OLD.id, OLD.status
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status
            AND NOT (OLD.status = 'DRAFT' AND NEW.status = 'PUBLISHED')
            AND NOT (OLD.status = 'PUBLISHED' AND NEW.status = 'ARCHIVED') THEN
        RAISE EXCEPTION 'agreement document % cannot change status from % to %', OLD.id, OLD.status, NEW.status
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_agreement_documents_protect
    BEFORE UPDATE OR DELETE ON agreement_documents
    FOR EACH ROW EXECUTE FUNCTION protect_agreement_documents();
