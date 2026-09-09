#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "${OUTPOST_FAKE_PSQL_LOG}"

if [[ "${OUTPOST_FAKE_PSQL_FAILURE:-0}" == "1" ]]; then
    exit 7
fi
