-- Accept the canonical code or exactly XXXX-XXXX-XXXX, in either case.
CREATE OR REPLACE FUNCTION catalog.parse_entity_code(p_code text, p_prefix text)
RETURNS catalog.entity_code_number
LANGUAGE plpgsql
IMMUTABLE STRICT PARALLEL SAFE
SET search_path = ''
AS $function$
DECLARE
    alphabet constant text := '0123456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    normalized text;
    decoded bigint := 0;
    digit integer;
    position integer;
BEGIN
    IF p_prefix COLLATE "C" !~ '^[A-Za-z]{4}$' THEN
        RAISE EXCEPTION 'Entity code prefix must contain exactly four ASCII letters'
            USING ERRCODE = '22023';
    END IF;
    IF p_code COLLATE "C" !~ '^[A-Za-z]{4}[0-9A-HJ-NP-Za-hj-np-z]{8}$'
       AND p_code COLLATE "C" !~ '^[A-Za-z]{4}-[0-9A-HJ-NP-Za-hj-np-z]{4}-[0-9A-HJ-NP-Za-hj-np-z]{4}$' THEN
        RAISE EXCEPTION 'Invalid entity code format or base-34 alphabet'
            USING ERRCODE = '22023';
    END IF;

    normalized := upper(replace(p_code, '-', '') COLLATE "C");
    IF left(normalized, 4) <> upper(p_prefix COLLATE "C") THEN
        RAISE EXCEPTION 'Entity code prefix does not match expected prefix'
            USING ERRCODE = '22023';
    END IF;

    FOR position IN 5..12 LOOP
        digit := strpos(alphabet, substr(normalized, position, 1)) - 1;
        decoded := decoded * 34 + digit;
    END LOOP;
    RETURN decoded::catalog.entity_code_number;
END;
$function$;
