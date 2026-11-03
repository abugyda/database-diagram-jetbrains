-- Allocation is through the constrained SECURITY DEFINER generator only.
REVOKE ALL ON SEQUENCE catalog.categories_code_number_seq FROM PUBLIC, anon, authenticated, service_role;
