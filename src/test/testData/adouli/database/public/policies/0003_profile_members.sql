--
-- Name: profile_members_select; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY profile_members_select ON profile_members FOR SELECT TO PUBLIC USING ((user_id = auth.uid()) OR can_manage_profile(profile_id));
