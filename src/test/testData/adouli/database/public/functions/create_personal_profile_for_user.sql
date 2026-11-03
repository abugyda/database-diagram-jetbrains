--
-- Name: create_personal_profile_for_user(); Type: FUNCTION; Schema: -; Owner: -
--

CREATE OR REPLACE FUNCTION create_personal_profile_for_user()
RETURNS trigger
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = ''
AS $$
declare new_profile_id uuid;
begin
  insert into public.profiles (name, kind)
  values (
    coalesce(nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''), 'Personal'),
    'personal'
  ) returning id into new_profile_id;
  insert into public.profile_members(profile_id, user_id, role)
  values (new_profile_id, new.id, 'owner');
  return new;
end;
$$;
