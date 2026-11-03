--
-- Name: sync_account_personal_profile(); Type: FUNCTION; Schema: -; Owner: -
--

CREATE OR REPLACE FUNCTION sync_account_personal_profile()
RETURNS trigger
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = ''
AS $$
begin
  update public.profiles p set name = trim(new.full_name)
  where p.kind = 'personal' and exists (
    select 1 from public.profile_members m
    where m.profile_id = p.id and m.user_id = new.user_id and m.role = 'owner'
  );
  return new;
end;
$$;
