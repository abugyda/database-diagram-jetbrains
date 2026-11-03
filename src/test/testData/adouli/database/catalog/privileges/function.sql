REVOKE EXECUTE ON FUNCTION catalog.format_entity_code(text, bigint) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION catalog.parse_entity_code(text, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION catalog.prevent_entity_code_change() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION catalog.next_entity_code(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION catalog.format_entity_code(text, bigint) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION catalog.parse_entity_code(text, text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION catalog.prevent_entity_code_change() TO service_role;
GRANT EXECUTE ON FUNCTION catalog.next_entity_code(text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION catalog.can_manage_catalog(uuid) TO anon, authenticated, service_role;
