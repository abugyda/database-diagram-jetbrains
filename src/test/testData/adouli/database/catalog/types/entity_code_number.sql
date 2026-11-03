-- Numeric portion of a public entity code: eight base-34 digits, starting at 1.
-- Alphabet: 0123456789ABCDEFGHJKLMNPQRSTUVWXYZ (no I or O).
CREATE DOMAIN catalog.entity_code_number AS bigint
    CONSTRAINT entity_code_number_range
    CHECK (VALUE BETWEEN 1 AND 1785793904895);
