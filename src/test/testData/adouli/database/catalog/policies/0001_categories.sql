--
-- Name: categories_delete; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY categories_delete ON catalog.categories FOR DELETE TO authenticated USING (public.can_manage_profile(profile_id));

--
-- Name: categories_insert; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY categories_insert ON catalog.categories FOR INSERT TO authenticated WITH CHECK (public.can_manage_profile(profile_id));

--
-- Name: categories_read; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY categories_read ON catalog.categories FOR SELECT TO anon, authenticated USING (is_active OR public.is_profile_member(profile_id));

--
-- Name: categories_select; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY categories_select ON catalog.categories FOR SELECT TO PUBLIC USING (is_active OR public.is_profile_member(profile_id));

--
-- Name: categories_update; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY categories_update ON catalog.categories FOR UPDATE TO authenticated USING (public.can_manage_profile(profile_id)) WITH CHECK (public.can_manage_profile(profile_id));
