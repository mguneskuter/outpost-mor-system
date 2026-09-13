#!/usr/bin/env bash
set -euo pipefail

# Runs every journal integrity control in outpost/db/control/ against the Outpost database, then
# prints the control totals. Exits 1 when any control returns a row. Each session is read-only, so
# a control can never change accounting history.

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
control_directory="${repository_root}/outpost/db/control"

run_read_only() {
    docker run --rm -i \
        --add-host=host.docker.internal:host-gateway \
        -e PGPASSWORD="${OUTPOST_DB_PASSWORD}" \
        -e PGOPTIONS="-c default_transaction_read_only=on" \
        postgres:18 \
        psql --no-psqlrc --quiet --no-align --field-separator='|' -P footer=off \
        --set=ON_ERROR_STOP=1 \
        --host="${OUTPOST_DB_HOST:-host.docker.internal}" \
        --port="${OUTPOST_DB_PORT:-5432}" \
        --username="${OUTPOST_DB_USER:-outpost}" \
        --dbname="${OUTPOST_DB_NAME:-outpost}"
}

failed=0
for control in "${control_directory}"/*.sql; do
    name="$(basename "${control}" .sql)"
    output="$(run_read_only <"${control}")"
    # Unaligned output is one header line followed by one line per row.
    rows=$(($(printf '%s\n' "${output}" | wc -l) - 1))
    if ((rows > 0)); then
        echo "FAILED ${name}: ${rows} row(s)"
        printf '%s\n' "${output}"
        failed=1
    else
        echo "PASSED ${name}"
    fi
done

echo "Control totals"
run_read_only <"${control_directory}/totals/control_totals.sql"

exit "${failed}"
