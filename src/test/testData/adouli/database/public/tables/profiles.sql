--
-- Name: profiles; Type: TABLE; Schema: -; Owner: -
--

CREATE TABLE IF NOT EXISTS profiles (
    id uuid DEFAULT gen_random_uuid(),
    kind profile_kind NOT NULL,
    name text NOT NULL,
    slug text,
    created_by uuid NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT profiles_pkey PRIMARY KEY (id),
    CONSTRAINT profiles_slug_key UNIQUE (slug),
    CONSTRAINT profiles_name_check CHECK (char_length(TRIM(BOTH FROM name)) >= 1 AND char_length(TRIM(BOTH FROM name)) <= 120)
);

--
-- Name: profiles; Type: RLS; Schema: -; Owner: -
--

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

--
-- Name: profiles_touch_updated_at; Type: TRIGGER; Schema: -; Owner: -
--

CREATE OR REPLACE TRIGGER profiles_touch_updated_at
    BEFORE UPDATE ON profiles
    FOR EACH ROW
    EXECUTE FUNCTION touch_updated_at();
