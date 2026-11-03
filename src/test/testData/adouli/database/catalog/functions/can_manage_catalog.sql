--
-- Name: can_manage_catalog(uuid); Type: FUNCTION; Schema: -; Owner: -
--

CREATE OR REPLACE FUNCTION catalog.can_manage_catalog(
    p_profile_id uuid
)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  select exists (
    select 1 from public.profile_members m
    join public.profiles p on p.id = m.profile_id
    where m.profile_id = p_profile_id
      and m.user_id = auth.uid()
      and p.kind in ('professional', 'business')
      and m.role in ('owner', 'admin', 'manager')
  );
$$;
