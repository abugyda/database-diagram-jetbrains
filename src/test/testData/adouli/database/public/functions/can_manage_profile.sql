--
-- Name: can_manage_profile(uuid); Type: FUNCTION; Schema: -; Owner: -
--

CREATE OR REPLACE FUNCTION can_manage_profile(
    p_profile_id uuid
)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
  select auth.uid() is not null and exists (
    select 1 from public.profile_members m
    where m.profile_id = p_profile_id and m.user_id = auth.uid()
      and m.role in ('owner','admin','manager')
  );
$$;
