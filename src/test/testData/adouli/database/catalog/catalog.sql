CREATE SCHEMA IF NOT EXISTS catalog;
\i types/entity_code_number.sql
\i types/entity_code.sql
\i sequences/categories_code_number_seq.sql
\i functions/format_entity_code.sql
\i functions/next_entity_code.sql
\i functions/parse_entity_code.sql
\i functions/prevent_entity_code_change.sql
\i functions/can_manage_catalog.sql
\i tables/categories.sql
\i policies/0001_categories.sql
\i privileges/schema.sql
\i privileges/table.sql
\i privileges/function.sql
\i privileges/sequence.sql
