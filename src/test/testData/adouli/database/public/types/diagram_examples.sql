--- Named composite (record) with individually typed address fields.
CREATE TYPE public.contact_address AS (
    street text,
    city text,
    country_code varchar(2)
);

--- Nested record: a composite attribute can itself contain another composite.
CREATE TYPE public.contact_details AS (
    email text,
    phone text,
    address public.contact_address
);

--- Domain: numeric values with a reusable nonnegative constraint.
CREATE DOMAIN public.non_negative_money AS numeric(12,2) CHECK (VALUE >= 0);

--- Custom continuous numeric range; bounds can be inclusive or exclusive.
CREATE TYPE public.price_range AS RANGE (subtype = numeric);
