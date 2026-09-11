#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
seed_dir="${OUTPOST_SEED_DIR:-${repository_root}/local/seed_data}"
psql_bin="${OUTPOST_PSQL_BIN:-psql}"

if [[ "${OUTPOST_SKIP_FX_GENERATION:-0}" != "1" ]]; then
    (cd "${repository_root}" && java local/fx/GenerateFxSeed.java)
    seed_dir="${OUTPOST_SEED_DIR:-${repository_root}/local/seed_data}"
fi

if ! ls "${seed_dir}"/*.sql >/dev/null 2>&1; then
    echo "No non-enum seed input found in ${seed_dir}."
    exit 2
fi

for seed_file in "${seed_dir}"/*.sql; do
    echo "Running seed file: ${seed_file}"
    PGPASSWORD="${OUTPOST_DB_PASSWORD}" "${psql_bin}" \
        -h "${OUTPOST_DB_HOST:-localhost}" \
        -p "${OUTPOST_DB_PORT:-5432}" \
        -U "${OUTPOST_DB_USER:-outpost}" \
        -d outpost \
        -v ON_ERROR_STOP=1 \
        -v psp_simulator_base_url="${OUTPOST_PSP_SIMULATOR_BASE_URL:?OUTPOST_PSP_SIMULATOR_BASE_URL must be set}" \
        -v psp_simulator_api_key="${OUTPOST_PSP_SIMULATOR_API_KEY:?OUTPOST_PSP_SIMULATOR_API_KEY must be set}" \
        -v psp_simulator_hmac_secret="${OUTPOST_PSP_SIMULATOR_HMAC_SECRET:?OUTPOST_PSP_SIMULATOR_HMAC_SECRET must be set}" \
        -f "${seed_file}"
done

if [[ "${OUTPOST_SKIP_FX_GENERATION:-0}" != "1" ]]; then
    for seed_file in "${repository_root}/local/generated/fx"/*.sql; do
        echo "Running seed file: ${seed_file}"
        PGPASSWORD="${OUTPOST_DB_PASSWORD}" "${psql_bin}" -h "${OUTPOST_DB_HOST:-localhost}" -p "${OUTPOST_DB_PORT:-5432}" -U "${OUTPOST_DB_USER:-outpost}" -d outpost -v ON_ERROR_STOP=1 -v psp_simulator_base_url="${OUTPOST_PSP_SIMULATOR_BASE_URL:?OUTPOST_PSP_SIMULATOR_BASE_URL must be set}" -v psp_simulator_api_key="${OUTPOST_PSP_SIMULATOR_API_KEY:?OUTPOST_PSP_SIMULATOR_API_KEY must be set}" -v psp_simulator_hmac_secret="${OUTPOST_PSP_SIMULATOR_HMAC_SECRET:?OUTPOST_PSP_SIMULATOR_HMAC_SECRET must be set}" -f "${seed_file}"
    done
fi
