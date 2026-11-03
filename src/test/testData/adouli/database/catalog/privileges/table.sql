REVOKE ALL ON TABLE catalog.categories FROM PUBLIC, anon, authenticated, service_role;
GRANT SELECT ON TABLE catalog.categories TO anon;
GRANT SELECT, DELETE ON TABLE catalog.categories TO authenticated;
GRANT SELECT, DELETE, REFERENCES, TRIGGER, TRUNCATE, MAINTAIN
    ON TABLE catalog.categories TO service_role;
-- Only the default may supply codes for client inserts; codes are immutable.
GRANT INSERT (id, profile_id, name, slug, description, is_active, created_at, updated_at)
    ON TABLE catalog.categories TO authenticated, service_role;
GRANT UPDATE (id, profile_id, name, slug, description, is_active, created_at, updated_at)
    ON TABLE catalog.categories TO authenticated, service_role;
