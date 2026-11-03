--
-- Name: listings; Type: TABLE; Schema: -; Owner: -
--

CREATE TABLE IF NOT EXISTS listings (
    id uuid,
    profile_id uuid NOT NULL,
    category_id uuid NOT NULL,
    name text NOT NULL,
    description text DEFAULT '' NOT NULL,
    price numeric(14,2) NOT NULL,
    currency text DEFAULT 'ETB' NOT NULL,
    status listing_status DEFAULT 'draft'::listing_status NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT listings_pkey PRIMARY KEY (id),
    CONSTRAINT listings_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE,
    CONSTRAINT listings_currency_check CHECK (char_length(currency) = 3),
    CONSTRAINT listings_name_check CHECK (char_length(TRIM(BOTH FROM name)) >= 1 AND char_length(TRIM(BOTH FROM name)) <= 160),
    CONSTRAINT listings_price_check CHECK (price >= 0::numeric)
);

--
-- Name: listings_category_id_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS listings_category_id_idx ON listings (category_id);

--
-- Name: listings_category_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS listings_category_idx ON listings (category_id);

--
-- Name: listings_profile_id_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS listings_profile_id_idx ON listings (profile_id);

--
-- Name: listings_profile_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS listings_profile_idx ON listings (profile_id);

--
-- Name: listings_public_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS listings_public_idx ON listings (status, created_at DESC);

--
-- Name: listings_status_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS listings_status_idx ON listings (status);

--
-- Name: listings; Type: RLS; Schema: -; Owner: -
--

ALTER TABLE listings ENABLE ROW LEVEL SECURITY;

--
-- Name: listings_touch_updated_at; Type: TRIGGER; Schema: -; Owner: -
--

CREATE OR REPLACE TRIGGER listings_touch_updated_at
    BEFORE UPDATE ON listings
    FOR EACH ROW
    EXECUTE FUNCTION touch_updated_at();
