--
-- Name: profile_members; Type: TABLE; Schema: -; Owner: -
--

CREATE TABLE IF NOT EXISTS profile_members (
    profile_id uuid,
    user_id uuid,
    role profile_role NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT profile_members_pkey PRIMARY KEY (profile_id, user_id),
    CONSTRAINT profile_members_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE
);

--
-- Name: profile_members_user_id_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS profile_members_user_id_idx ON profile_members (user_id);

--
-- Name: profile_members_user_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS profile_members_user_idx ON profile_members (user_id, profile_id);

--
-- Name: profile_members; Type: RLS; Schema: -; Owner: -
--

ALTER TABLE profile_members ENABLE ROW LEVEL SECURITY;

--
-- Name: profile_members_read; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY profile_members_read ON profile_members FOR SELECT TO authenticated USING (user_id = auth.uid());
