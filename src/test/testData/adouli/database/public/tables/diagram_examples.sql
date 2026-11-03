--- ERD feature gallery: records, a nested record, domain, range, arrays, JSON, defaults, indexes, and a self-reference.
CREATE TABLE public.diagram_examples (
    --- UUID primary key with an expression default.
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    --- Unique required label; resize the card to compare metadata and description truncation.
    code varchar(80) NOT NULL UNIQUE,
    --- Sequence-generated identity; I is distinct from a computed generated column.
    sequence_id bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
    --- Indexed foreign key to an account; several examples can share one owner.
    owner_user_id uuid REFERENCES public.account_settings (user_id),
    --- Nullable self-reference; move this card to inspect its outside relation loop.
    parent_id uuid REFERENCES public.diagram_examples (id),
    --- Record with street, city, and two-letter country_code attributes.
    shipping_address public.contact_address,
    --- Array of records; both REC and ARR badges apply.
    previous_addresses public.contact_address[],
    --- Array of enum labels; both ENM and ARR badges apply.
    gender_options public.gender[],
    --- Nested record with email, phone, and another contact_address record.
    contact public.contact_details,
    --- Domain over numeric(12,2); its definition disallows negative amounts.
    amount public.non_negative_money NOT NULL DEFAULT 0,
    --- Inline check and NN are independent indicators.
    quantity integer NOT NULL DEFAULT 1 CHECK (quantity > 0),
    --- Nonnegative unit price; also participates in the table-level check.
    unit_price numeric(12,2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0),
    --- Computed value changes with quantity or unit_price.
    line_total numeric(14,2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    --- Custom numeric range; the upper bound is excluded in this default.
    budget public.price_range DEFAULT '[0,1000)'::public.price_range,
    --- Built-in timestamp range for comparison with the custom numeric range.
    active_during tstzrange,
    --- Text array with an empty-array default and a GIN index.
    tags text[] NOT NULL DEFAULT '{}'::text[],
    --- JSONB default is intentionally long to demonstrate width-sensitive truncation.
    metadata jsonb NOT NULL DEFAULT '{"source":"diagram showcase","preferences":{"theme":"system","notifications":true}}'::jsonb,
    --- Boolean default; part of a partial index predicate.
    enabled boolean NOT NULL DEFAULT true,
    --- Expression default; second column of the owner/time composite index.
    created_at timestamptz NOT NULL DEFAULT now(),
    --- Optional field without a default, index, or relationship.
    description text,
    CONSTRAINT diagram_examples_total_limit CHECK (quantity * unit_price <= 1000000)
);

CREATE INDEX diagram_examples_owner_created_idx ON public.diagram_examples (owner_user_id, created_at);
CREATE INDEX diagram_examples_tags_idx ON public.diagram_examples USING gin (tags);
CREATE INDEX diagram_examples_metadata_idx ON public.diagram_examples USING gin (metadata);
CREATE INDEX diagram_examples_enabled_amount_idx ON public.diagram_examples (amount) WHERE enabled;
