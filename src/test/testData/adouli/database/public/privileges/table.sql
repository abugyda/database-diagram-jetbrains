--
-- Name: account_settings; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE account_settings FROM anon;

--
-- Name: account_settings; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, MAINTAIN, REFERENCES, TRIGGER, TRUNCATE ON TABLE account_settings FROM authenticated;

--
-- Name: account_settings; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE account_settings FROM postgres;

--
-- Name: demo_listings; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE demo_listings FROM postgres;

--
-- Name: listings; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, TRIGGER, TRUNCATE, UPDATE ON TABLE listings FROM anon;

--
-- Name: listings; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE MAINTAIN, REFERENCES, TRIGGER, TRUNCATE ON TABLE listings FROM authenticated;

--
-- Name: listings; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE listings FROM postgres;

--
-- Name: profile_members; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE profile_members FROM anon;

--
-- Name: profile_members; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, TRIGGER, TRUNCATE, UPDATE ON TABLE profile_members FROM authenticated;

--
-- Name: profile_members; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE profile_members FROM postgres;

--
-- Name: profiles; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE profiles FROM anon;

--
-- Name: profiles; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, TRIGGER, TRUNCATE, UPDATE ON TABLE profiles FROM authenticated;

--
-- Name: profiles; Type: PRIVILEGE; Schema: privileges; Owner: -
--

REVOKE DELETE, INSERT, MAINTAIN, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE ON TABLE profiles FROM postgres;
