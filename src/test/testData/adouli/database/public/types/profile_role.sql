--
-- Name: profile_role; Type: TYPE; Schema: -; Owner: -
--

CREATE TYPE profile_role AS ENUM (
    'owner',
    'admin',
    'manager',
    'member'
);
