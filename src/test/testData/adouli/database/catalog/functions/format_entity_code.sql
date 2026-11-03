-- Prefixes are four ASCII letters; only the suffix excludes I and O.
CREATE OR REPLACE FUNCTION catalog.format_entity_code(p_prefix text, p_number bigint)
RETURNS text
LANGUAGE plpgsql
IMMUTABLE STRICT PARALLEL SAFE
SET search_path = ''
AS $function$
DECLARE
    alphabet constant text := '0123456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    remaining bigint := p_number::catalog.entity_code_number;
    encoded text := '';
    prefix text;
BEGIN
    IF p_prefix COLLATE "C" !~ '^[A-Za-z]{4}$' THEN
        RAISE EXCEPTION 'Entity code prefix must contain exactly four ASCII letters'
            USING ERRCODE = '22023';
    END IF;
    prefix := upper(p_prefix COLLATE "C");

    WHILE remaining > 0 LOOP
        encoded := substr(alphabet, (remaining % 34)::integer + 1, 1) || encoded;
        remaining := remaining / 34;
    END LOOP;

    encoded := lpad(encoded, 8, '0');
    RETURN prefix || '-' || substr(encoded, 1, 4) || '-' || substr(encoded, 5, 4);
END;
$function$;
