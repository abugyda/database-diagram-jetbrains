--
-- Name: touch_updated_at(); Type: FUNCTION; Schema: -; Owner: -
--

CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
VOLATILE
AS $$
begin
  new.updated_at = now();
  return new;
end;
$$;
