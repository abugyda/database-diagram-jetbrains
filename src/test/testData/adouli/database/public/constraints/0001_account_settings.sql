--
-- Name: account_settings_user_id_fkey; Type: CONSTRAINT; Schema: -; Owner: -
--

ALTER TABLE account_settings
ADD CONSTRAINT account_settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users (id) ON DELETE CASCADE;
