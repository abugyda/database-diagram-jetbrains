CREATE OR REPLACE FUNCTION catalog.prevent_entity_code_change()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $function$
BEGIN
    IF NEW.code::text IS DISTINCT FROM OLD.code::text THEN
        RAISE EXCEPTION 'Entity codes cannot be changed'
            USING ERRCODE = '22023';
    END IF;
    RETURN NEW;
END;
$function$;
