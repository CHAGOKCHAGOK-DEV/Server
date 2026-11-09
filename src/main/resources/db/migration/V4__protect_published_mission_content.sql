CREATE OR REPLACE FUNCTION protect_mission_pack_content() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status IN ('PUBLISHED', 'ARCHIVED') THEN
            RAISE EXCEPTION 'Published mission packs cannot be deleted';
        END IF;
        RETURN OLD;
    END IF;

    IF TG_OP = 'UPDATE' AND OLD.status IN ('PUBLISHED', 'ARCHIVED') THEN
        IF OLD.status = 'PUBLISHED' AND NEW.status = 'ARCHIVED'
           AND ROW(OLD.code, OLD.version, OLD.schema_version, OLD.published_at)
               IS NOT DISTINCT FROM ROW(NEW.code, NEW.version, NEW.schema_version, NEW.published_at) THEN
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'Published mission packs cannot be modified';
    END IF;

    IF TG_OP = 'UPDATE' AND OLD.status = 'DRAFT' AND NEW.status = 'PUBLISHED' THEN
        IF NEW.published_at IS NULL OR NOT EXISTS (
            SELECT 1 FROM missions WHERE mission_pack_id = NEW.id AND enabled
        ) THEN
            RAISE EXCEPTION 'Publishing requires a timestamp and an enabled mission';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_mission_pack_content
BEFORE UPDATE OR DELETE ON mission_packs
FOR EACH ROW EXECUTE FUNCTION protect_mission_pack_content();

CREATE OR REPLACE FUNCTION protect_mission_content() RETURNS trigger AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') AND EXISTS (
        SELECT 1 FROM mission_packs
        WHERE id = OLD.mission_pack_id AND status IN ('PUBLISHED', 'ARCHIVED')
    ) THEN
        RAISE EXCEPTION 'Published missions cannot be modified';
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') AND EXISTS (
        SELECT 1 FROM mission_packs
        WHERE id = NEW.mission_pack_id AND status IN ('PUBLISHED', 'ARCHIVED')
    ) THEN
        RAISE EXCEPTION 'Missions cannot be added to a published pack';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_mission_content
BEFORE INSERT OR UPDATE OR DELETE ON missions
FOR EACH ROW EXECUTE FUNCTION protect_mission_content();
