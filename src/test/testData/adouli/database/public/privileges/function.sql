--
-- Name: account_settings_timestamp(); Type: REVOKED_DEFAULT_PRIVILEGE; Schema: revoked_default; Owner: -
--

REVOKE EXECUTE ON FUNCTION account_settings_timestamp() FROM PUBLIC;


--
-- Name: can_manage_profile(p_profile_id uuid); Type: REVOKED_DEFAULT_PRIVILEGE; Schema: revoked_default; Owner: -
--

REVOKE EXECUTE ON FUNCTION can_manage_profile(p_profile_id uuid) FROM PUBLIC;

--
-- Name: create_profile(p_name text, p_kind text, p_slug text); Type: REVOKED_DEFAULT_PRIVILEGE; Schema: revoked_default; Owner: -
--

REVOKE EXECUTE ON FUNCTION create_profile(p_name text, p_kind text, p_slug text) FROM PUBLIC;

--
-- Name: is_profile_member(p_profile_id uuid); Type: REVOKED_DEFAULT_PRIVILEGE; Schema: revoked_default; Owner: -
--

REVOKE EXECUTE ON FUNCTION is_profile_member(p_profile_id uuid) FROM PUBLIC;

--
-- Name: sync_account_personal_profile(); Type: REVOKED_DEFAULT_PRIVILEGE; Schema: revoked_default; Owner: -
--

REVOKE EXECUTE ON FUNCTION sync_account_personal_profile() FROM PUBLIC;

--
-- Name: account_settings_timestamp(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION account_settings_timestamp() TO anon;

--
-- Name: account_settings_timestamp(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION account_settings_timestamp() TO authenticated;

--
-- Name: account_settings_timestamp(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION account_settings_timestamp() TO service_role;

--
-- Name: can_manage_profile(p_profile_id uuid); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION can_manage_profile(p_profile_id uuid) TO anon;

--
-- Name: can_manage_profile(p_profile_id uuid); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION can_manage_profile(p_profile_id uuid) TO authenticated;

--
-- Name: can_manage_profile(p_profile_id uuid); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION can_manage_profile(p_profile_id uuid) TO service_role;

--
-- Name: create_personal_profile_for_user(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION create_personal_profile_for_user() TO anon;

--
-- Name: create_personal_profile_for_user(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION create_personal_profile_for_user() TO authenticated;

--
-- Name: create_personal_profile_for_user(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION create_personal_profile_for_user() TO service_role;

--
-- Name: create_profile(p_name text, p_kind text, p_slug text); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION create_profile(p_name text, p_kind text, p_slug text) TO anon;

--
-- Name: create_profile(p_name text, p_kind text, p_slug text); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION create_profile(p_name text, p_kind text, p_slug text) TO authenticated;

--
-- Name: create_profile(p_name text, p_kind text, p_slug text); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION create_profile(p_name text, p_kind text, p_slug text) TO service_role;

--
-- Name: is_profile_member(p_profile_id uuid); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION is_profile_member(p_profile_id uuid) TO anon;

--
-- Name: is_profile_member(p_profile_id uuid); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION is_profile_member(p_profile_id uuid) TO authenticated;

--
-- Name: is_profile_member(p_profile_id uuid); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION is_profile_member(p_profile_id uuid) TO service_role;

--
-- Name: sync_account_personal_profile(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION sync_account_personal_profile() TO anon;

--
-- Name: sync_account_personal_profile(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION sync_account_personal_profile() TO authenticated;

--
-- Name: sync_account_personal_profile(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION sync_account_personal_profile() TO service_role;

--
-- Name: touch_updated_at(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION touch_updated_at() TO anon;

--
-- Name: touch_updated_at(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION touch_updated_at() TO authenticated;

--
-- Name: touch_updated_at(); Type: PRIVILEGE; Schema: privileges; Owner: -
--

GRANT EXECUTE ON FUNCTION touch_updated_at() TO service_role;
