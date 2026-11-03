-- citext supplies case-insensitive equality and uniqueness. Cast to text so
-- validation remains strictly uppercase even though citext regexes ignore case.
CREATE DOMAIN catalog.entity_code AS extensions.citext COLLATE "C"
    CONSTRAINT entity_code_format CHECK (
        VALUE::text COLLATE "C" ~ '^[A-Z]{4}-[0-9A-HJ-NP-Z]{4}-[0-9A-HJ-NP-Z]{4}$'
        AND substring(VALUE::text FROM 6) <> '0000-0000'
    );
