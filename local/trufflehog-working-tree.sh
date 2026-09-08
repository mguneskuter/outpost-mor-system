#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"

scan_paths=()
while IFS= read -r -d '' path; do
    if [[ -e "$path" && ! -L "$path" ]]; then
        scan_paths+=("$path")
    fi
done < <(git ls-files --cached --others --exclude-standard -z)

if ((${#scan_paths[@]} == 0)); then
    exit 0
fi

exec ./bin/trufflehog filesystem \
    --results=verified,unknown,unverified \
    --force-skip-binaries \
    --exclude-paths local/trufflehog-exclude-paths.txt \
    "${scan_paths[@]}"
