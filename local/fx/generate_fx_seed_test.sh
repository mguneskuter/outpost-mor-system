#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_dir}/../.." && pwd)"
temporary_dir="$(mktemp -d "${TMPDIR:-/tmp}/outpost-fx-seed-test.XXXXXX")"
trap 'rm -rf "${temporary_dir}"' EXIT

cd "${repository_root}"
java local/fx/GenerateFxSeed.java
sha256sum local/generated/fx/fx_rate.sql local/generated/fx/fx_fee.sql >"${temporary_dir}/first.sha"
cp -R local/generated/fx "${temporary_dir}/first"
java local/fx/GenerateFxSeed.java
sha256sum local/generated/fx/fx_rate.sql local/generated/fx/fx_fee.sql >"${temporary_dir}/second.sha"
cmp "${temporary_dir}/first.sha" "${temporary_dir}/second.sha"
[[ "$(grep -c '^INSERT INTO fx_rate' local/generated/fx/fx_rate.sql)" -eq 864 ]]
[[ "$(grep -c '^INSERT INTO fx_fee' local/generated/fx/fx_fee.sql)" -eq 72 ]]
equals="$(sed -nE 's/^INSERT INTO fx_rate .*VALUES \([0-9]+, ([0-9]+), ([0-9]+), DATE.*/\1 \2/p' \
    local/generated/fx/fx_rate.sql | awk '$1 == $2 { bad = 1 } END { print bad + 0 }')"
[[ "${equals}" -eq 0 ]]
non_standard="$(sed -nE 's/^INSERT INTO fx_fee .*VALUES \([0-9]+, [0-9]+, [0-9]+, ([0-9]+)\).*/\1/p' \
    local/generated/fx/fx_fee.sql | awk '$1 != 100 { bad = 1 } END { print bad + 0 }')"
[[ "${non_standard}" -eq 0 ]]
echo "FX seed generation tests passed"
