#!/usr/bin/env bash
set -euo pipefail
# Run with `make seed-test` to verify seed ordering and failure handling.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
seed_script="${script_dir}/seed.sh"
fake_psql="${script_dir}/test-fixtures/fake-psql.sh"
temporary_dir="$(mktemp -d "${TMPDIR:-/tmp}/outpost-seed-test.XXXXXX")"
trap 'rm -rf "${temporary_dir}"' EXIT

export OUTPOST_DB_PASSWORD=test
export OUTPOST_PSP_SIMULATOR_BASE_URL=http://localhost:8081
export OUTPOST_PSP_SIMULATOR_API_KEY=demo-outpost-api-key
export OUTPOST_PSP_SIMULATOR_HMAC_SECRET=demo-hmac-secret
export OUTPOST_PSQL_BIN="${fake_psql}"
export OUTPOST_FAKE_PSQL_LOG="${temporary_dir}/psql.log"

empty_seed_dir="${temporary_dir}/empty"
mkdir -p "${empty_seed_dir}"
if OUTPOST_SEED_DIR="${empty_seed_dir}" "${seed_script}" \
    >"${temporary_dir}/missing.log" 2>&1; then
    echo "seed runner accepted an empty seed directory" >&2
    exit 1
fi
grep -q "No non-enum seed input" "${temporary_dir}/missing.log"

seed_dir="${temporary_dir}/seed"
mkdir -p "${seed_dir}"
printf '%s\n' 'SELECT 1;' >"${seed_dir}/002-second.sql"
printf '%s\n' 'SELECT 1;' >"${seed_dir}/001-first.sql"

: >"${OUTPOST_FAKE_PSQL_LOG}"
OUTPOST_SEED_DIR="${seed_dir}" "${seed_script}" >/dev/null
seed_call_count="$(grep -c -- '-f ' "${OUTPOST_FAKE_PSQL_LOG}")"
[[ "${seed_call_count}" -eq 4 ]]
first_seed_call="$(grep -- '-f ' "${OUTPOST_FAKE_PSQL_LOG}" | sed -n '1p')"
second_seed_call="$(grep -- '-f ' "${OUTPOST_FAKE_PSQL_LOG}" | sed -n '2p')"
[[ "${first_seed_call}" == *"001-first.sql"* ]]
[[ "${second_seed_call}" == *"002-second.sql"* ]]
grep -q 'fx_rate.sql' "${OUTPOST_FAKE_PSQL_LOG}"
grep -q 'fx_fee.sql' "${OUTPOST_FAKE_PSQL_LOG}"

: >"${OUTPOST_FAKE_PSQL_LOG}"
if OUTPOST_SEED_DIR="${seed_dir}" \
    OUTPOST_FAKE_PSQL_FAILURE=1 "${seed_script}" \
    >"${temporary_dir}/failure.log" 2>&1; then
    echo "seed runner ignored a psql failure" >&2
    exit 1
fi
grep -q "Running seed file: .*001-first.sql" "${temporary_dir}/failure.log"

echo "seed runner fixture tests passed"
