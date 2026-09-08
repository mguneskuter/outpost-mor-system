#!/usr/bin/env bash
set -euo pipefail

# Create the non-superuser application role 'outpost' and make it the owner of
# the 'outpost' database so it can create the Flyway history table in 'public'.
# The bootstrap role remains the only superuser. The application role password
# matches the operator-supplied OUTPOST_DB_PASSWORD (exposed to the entrypoint
# bootstrap as POSTGRES_PASSWORD).
psql -v ON_ERROR_STOP=1 -U outpost_bootstrap -d postgres \
    -v outpost_password="$POSTGRES_PASSWORD" <<'SQL'
CREATE ROLE outpost LOGIN PASSWORD :'outpost_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
ALTER DATABASE outpost OWNER TO outpost;
GRANT ALL ON SCHEMA public TO outpost;
SQL
