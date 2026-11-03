--
-- Name: listings_delete; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY listings_delete ON listings FOR DELETE TO authenticated USING (can_manage_profile(profile_id));

--
-- Name: listings_insert; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY listings_insert ON listings FOR INSERT TO authenticated WITH CHECK (can_manage_profile(profile_id));

--
-- Name: listings_read; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY listings_read ON listings FOR SELECT TO anon, authenticated USING ((status = 'published'::listing_status) OR is_profile_member(profile_id));

--
-- Name: listings_select; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY listings_select ON listings FOR SELECT TO PUBLIC USING ((status = 'published'::listing_status) OR is_profile_member(profile_id));

--
-- Name: listings_update; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY listings_update ON listings FOR UPDATE TO authenticated USING (can_manage_profile(profile_id)) WITH CHECK (can_manage_profile(profile_id));
