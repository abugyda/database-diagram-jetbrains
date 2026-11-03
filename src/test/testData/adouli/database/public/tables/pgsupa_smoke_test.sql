-- Disposable table for validating the Dart PGSupa plan/apply workflow.
CREATE TABLE pgsupa_smoke_test (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    message text NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pgsupa_smoke_test_pkey PRIMARY KEY (id)
);

ALTER TABLE pgsupa_smoke_test ENABLE ROW LEVEL SECURITY;

-- Retain administrative access while keeping this test table private to clients.
GRANT ALL PRIVILEGES ON TABLE pgsupa_smoke_test TO postgres, service_role;
REVOKE ALL PRIVILEGES ON TABLE pgsupa_smoke_test FROM anon, authenticated;
