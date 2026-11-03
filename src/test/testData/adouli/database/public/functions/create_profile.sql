--
-- Name: create_profile(text, text, text); Type: FUNCTION; Schema: -; Owner: -
--

CREATE OR REPLACE FUNCTION create_profile(
    p_name text,
    p_kind text,
    p_slug text DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = ''
AS $$
declare created_id uuid;
declare normalized_slug text;
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if p_kind not in ('professional','business') then
    raise exception 'Only professional and business profiles may be created';
  end if;
  if p_name is null or char_length(trim(p_name)) not between 1 and 120 then
    raise exception 'Profile name must contain 1–120 characters';
  end if;
  normalized_slug := nullif(lower(trim(p_slug)), '');
  if normalized_slug is not null and normalized_slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$' then
    raise exception 'Invalid profile slug';
  end if;
  insert into public.profiles (name, kind, slug)
  values (trim(p_name), p_kind, normalized_slug)
  returning id into created_id;
  insert into public.profile_members(profile_id, user_id, role)
  values (created_id, auth.uid(), 'owner');
  return created_id;
end;
$$;
