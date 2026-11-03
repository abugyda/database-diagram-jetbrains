--
-- Name: profile_members_user_id_fkey; Type: CONSTRAINT; Schema: -; Owner: -
--

ALTER TABLE profile_members
ADD CONSTRAINT profile_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users (id) ON DELETE CASCADE;
