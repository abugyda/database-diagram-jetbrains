-- Listing API adapter: PostgREST does not infer embeds across schemas.
-- Preserve select('..., categories(...)') while the table lives in catalog.
CREATE OR REPLACE FUNCTION public.categories(p_listing public.listings)
RETURNS SETOF catalog.categories
LANGUAGE sql
STABLE STRICT
ROWS 1
SECURITY INVOKER
SET search_path = ''
AS $function$
    SELECT c.*
    FROM catalog.categories c
    WHERE c.id = (p_listing).category_id
      AND c.profile_id = (p_listing).profile_id;
$function$;

REVOKE EXECUTE ON FUNCTION public.categories(public.listings) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.categories(public.listings) TO anon, authenticated, service_role;
