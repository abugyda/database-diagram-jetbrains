REVOKE CREATE ON SCHEMA catalog FROM PUBLIC, anon, authenticated, service_role;
GRANT USAGE ON SCHEMA catalog TO anon, authenticated, service_role;
