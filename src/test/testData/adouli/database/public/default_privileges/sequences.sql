--
-- Name: postgres:SEQUENCES:anon; Type: DEFAULT_PRIVILEGE; Schema: default_privileges; Owner: -
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT, UPDATE, USAGE ON SEQUENCES TO anon;

--
-- Name: postgres:SEQUENCES:authenticated; Type: DEFAULT_PRIVILEGE; Schema: default_privileges; Owner: -
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT, UPDATE, USAGE ON SEQUENCES TO authenticated;

--
-- Name: postgres:SEQUENCES:postgres; Type: DEFAULT_PRIVILEGE; Schema: default_privileges; Owner: -
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT, UPDATE, USAGE ON SEQUENCES TO postgres;

--
-- Name: postgres:SEQUENCES:service_role; Type: DEFAULT_PRIVILEGE; Schema: default_privileges; Owner: -
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT, UPDATE, USAGE ON SEQUENCES TO service_role;
