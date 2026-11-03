--
-- Name: account_settings_timestamp(); Type: FUNCTION; Schema: -; Owner: -
--

CREATE OR REPLACE FUNCTION account_settings_timestamp()
RETURNS trigger
LANGUAGE plpgsql
VOLATILE
SET search_path = ''
AS $$
begin
  new.updated_at := now();
  return new;
end;
$$;
