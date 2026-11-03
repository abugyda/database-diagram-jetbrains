--
-- Name: demo_listings; Type: TABLE; Schema: -; Owner: -
--

CREATE TABLE IF NOT EXISTS demo_listings (
    id text,
    owner_profile_id text NOT NULL,
    name text NOT NULL,
    price double precision NOT NULL,
    currency text DEFAULT 'ETB' NOT NULL,
    status text NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT demo_listings_pkey PRIMARY KEY (id),
    CONSTRAINT demo_listings_price_check CHECK (price >= 0::double precision),
    CONSTRAINT demo_listings_status_check CHECK (status IN ('draft'::text, 'published'::text))
);

--
-- Name: demo_listings_owner_profile_id_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS demo_listings_owner_profile_id_idx ON demo_listings (owner_profile_id);

--
-- Name: demo_listings; Type: RLS; Schema: -; Owner: -
--

ALTER TABLE demo_listings ENABLE ROW LEVEL SECURITY;

--
-- Name: demo listings deletable; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY "demo listings deletable" ON demo_listings FOR DELETE TO PUBLIC USING (true);

--
-- Name: demo listings insertable; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY "demo listings insertable" ON demo_listings FOR INSERT TO PUBLIC WITH CHECK (true);

--
-- Name: demo listings readable; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY "demo listings readable" ON demo_listings FOR SELECT TO PUBLIC USING (true);

--
-- Name: demo listings updatable; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY "demo listings updatable" ON demo_listings FOR UPDATE TO PUBLIC USING (true) WITH CHECK (true);
