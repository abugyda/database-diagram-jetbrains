--
-- Name: account_settings; Type: TABLE; Schema: -; Owner: -
--

CREATE TABLE IF NOT EXISTS account_settings (
    user_id uuid,
    full_name text NOT NULL,
    bio text DEFAULT '' NOT NULL,
    location text DEFAULT '' NOT NULL,
    date_of_birth date,
    gender public.gender DEFAULT 'unspecified' NOT NULL,
    photo_url text,
    notifications jsonb DEFAULT '{}' NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT account_settings_pkey PRIMARY KEY (user_id),
    CONSTRAINT account_notifications_boolean CHECK ((NOT notifications ? 'emailOrders'::text OR jsonb_typeof(notifications -> 'emailOrders'::text) = 'boolean'::text) AND (NOT notifications ? 'emailOffers'::text OR jsonb_typeof(notifications -> 'emailOffers'::text) = 'boolean'::text) AND (NOT notifications ? 'emailProducts'::text OR jsonb_typeof(notifications -> 'emailProducts'::text) = 'boolean'::text) AND (NOT notifications ? 'emailMarketing'::text OR jsonb_typeof(notifications -> 'emailMarketing'::text) = 'boolean'::text) AND (NOT notifications ? 'pushOrders'::text OR jsonb_typeof(notifications -> 'pushOrders'::text) = 'boolean'::text) AND (NOT notifications ? 'pushMessages'::text OR jsonb_typeof(notifications -> 'pushMessages'::text) = 'boolean'::text) AND (NOT notifications ? 'pushPrices'::text OR jsonb_typeof(notifications -> 'pushPrices'::text) = 'boolean'::text) AND (NOT notifications ? 'pushFollowers'::text OR jsonb_typeof(notifications -> 'pushFollowers'::text) = 'boolean'::text)),
    CONSTRAINT account_notifications_keys CHECK ((notifications - ARRAY['emailOrders'::text, 'emailOffers'::text, 'emailProducts'::text, 'emailMarketing'::text, 'pushOrders'::text, 'pushMessages'::text, 'pushPrices'::text, 'pushFollowers'::text]) = '{}'::jsonb),
    CONSTRAINT account_notifications_object CHECK (jsonb_typeof(notifications) = 'object'::text),
    CONSTRAINT account_settings_bio_check CHECK (char_length(bio) <= 300),
    CONSTRAINT account_settings_date_of_birth_check CHECK (date_of_birth >= '1900-01-01'::date AND date_of_birth <= CURRENT_DATE),
    CONSTRAINT account_settings_full_name_check CHECK (char_length(TRIM(BOTH FROM full_name)) >= 1 AND char_length(TRIM(BOTH FROM full_name)) <= 120),
    CONSTRAINT account_settings_location_check CHECK (char_length(location) <= 160),
    CONSTRAINT account_settings_photo_url_check CHECK (photo_url IS NULL OR char_length(photo_url) <= 2048 AND photo_url ~ '^https://'::text)
);

--
-- Name: account_settings; Type: RLS; Schema: -; Owner: -
--

ALTER TABLE account_settings ENABLE ROW LEVEL SECURITY;

--
-- Name: account_settings_insert; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY account_settings_insert ON account_settings FOR INSERT TO authenticated WITH CHECK (user_id = ( SELECT auth.uid() AS uid));

--
-- Name: account_settings_read; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY account_settings_read ON account_settings FOR SELECT TO authenticated USING (user_id = ( SELECT auth.uid() AS uid));

--
-- Name: account_settings_update; Type: POLICY; Schema: -; Owner: -
--

CREATE POLICY account_settings_update ON account_settings FOR UPDATE TO authenticated USING (user_id = ( SELECT auth.uid() AS uid)) WITH CHECK (user_id = ( SELECT auth.uid() AS uid));

--
-- Name: account_settings_sync_personal_profile; Type: TRIGGER; Schema: -; Owner: -
--

CREATE OR REPLACE TRIGGER account_settings_sync_personal_profile
    AFTER INSERT OR UPDATE OF full_name ON account_settings
    FOR EACH ROW
    EXECUTE FUNCTION sync_account_personal_profile();

--
-- Name: account_settings_timestamp; Type: TRIGGER; Schema: -; Owner: -
--

CREATE OR REPLACE TRIGGER account_settings_timestamp
    BEFORE UPDATE ON account_settings
    FOR EACH ROW
    EXECUTE FUNCTION account_settings_timestamp();
