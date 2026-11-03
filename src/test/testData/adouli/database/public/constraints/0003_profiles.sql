--
-- Name: profiles_created_by_fkey; Type: CONSTRAINT; Schema: -; Owner: -
--

ALTER TABLE profiles
ADD CONSTRAINT profiles_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth.users (id) ON DELETE CASCADE;
