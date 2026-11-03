-- Register prefixes explicitly, with one bounded sequence for each entity.
-- Definer permissions allow allocation without giving clients sequence access.
CREATE OR REPLACE FUNCTION catalog.next_entity_code(p_prefix text)
RETURNS catalog.entity_code
LANGUAGE plpgsql
VOLATILE STRICT
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    number bigint;
BEGIN
    IF p_prefix COLLATE "C" !~ '^[A-Za-z]{4}$' THEN
        RAISE EXCEPTION 'Entity code prefix must contain exactly four ASCII letters'
            USING ERRCODE = '22023';
    END IF;

    CASE upper(p_prefix COLLATE "C")
        WHEN 'CATE' THEN
            number := nextval('catalog.categories_code_number_seq'::regclass);
        ELSE
            RAISE EXCEPTION 'Unregistered entity code prefix: %', p_prefix
                USING ERRCODE = '22023';
    END CASE;
    RETURN catalog.format_entity_code(p_prefix, number)::catalog.entity_code;
END;
$function$;
