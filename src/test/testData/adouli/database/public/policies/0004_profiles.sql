--
-- Name: profiles_delete; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY profiles_delete ON profiles FOR DELETE TO PUBLIC USING (EXISTS ( SELECT 1 FROM profile_members m WHERE ((m.profile_id = profiles.id) AND (m.user_id = auth.uid()) AND (m.role = 'owner'::profile_role))));

--
-- Name: profiles_read; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY profiles_read ON profiles FOR SELECT TO authenticated USING (is_profile_member(id));

--
-- Name: profiles_select; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY profiles_select ON profiles FOR SELECT TO PUBLIC USING ((kind <> 'personal'::profile_kind) OR is_profile_member(id));

--
-- Name: profiles_update; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY profiles_update ON profiles FOR UPDATE TO PUBLIC USING (can_manage_profile(id)) WITH CHECK (can_manage_profile(id));
