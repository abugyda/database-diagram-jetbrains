--
-- Name: catalog.categories; Type: TABLE; Schema: -; Owner: -
--

CREATE TABLE IF NOT EXISTS catalog.categories (
    id uuid,
    code catalog.entity_code DEFAULT catalog.next_entity_code('CATE') NOT NULL,
    profile_id uuid NOT NULL,
    name text NOT NULL,
    slug text NOT NULL,
    description text DEFAULT '' NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT categories_pkey PRIMARY KEY (id),
    CONSTRAINT categories_code_key UNIQUE (code),
    CONSTRAINT categories_code_prefix_check CHECK (left(code::text, 5) = 'CATE-'),
    CONSTRAINT categories_id_profile_id_key UNIQUE (id, profile_id),
    CONSTRAINT categories_profile_id_slug_key UNIQUE (profile_id, slug),
    CONSTRAINT categories_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profiles (id) ON DELETE CASCADE,
    CONSTRAINT categories_name_check CHECK (char_length(TRIM(BOTH FROM name)) >= 1 AND char_length(TRIM(BOTH FROM name)) <= 100)
);

ALTER SEQUENCE catalog.categories_code_number_seq OWNED BY catalog.categories.code;

CREATE OR REPLACE TRIGGER categories_prevent_code_change
    BEFORE UPDATE OF code ON catalog.categories
    FOR EACH ROW
    EXECUTE FUNCTION catalog.prevent_entity_code_change();

--
-- Name: categories_profile_id_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS categories_profile_id_idx ON catalog.categories (profile_id);

--
-- Name: categories_profile_idx; Type: INDEX; Schema: -; Owner: -
--

CREATE INDEX IF NOT EXISTS categories_profile_idx ON catalog.categories (profile_id);

--
-- Name: catalog.categories; Type: RLS; Schema: -; Owner: -
--

ALTER TABLE catalog.categories ENABLE ROW LEVEL SECURITY;

--
-- Name: categories_touch_updated_at; Type: TRIGGER; Schema: -; Owner: -
--

CREATE OR REPLACE TRIGGER categories_touch_updated_at
    BEFORE UPDATE ON catalog.categories
    FOR EACH ROW
    EXECUTE FUNCTION public.touch_updated_at();
