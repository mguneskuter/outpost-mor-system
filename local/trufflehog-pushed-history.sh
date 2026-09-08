#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"

if git rev-parse --verify origin/main >/dev/null 2>&1; then
    since_commit=$(git merge-base origin/main HEAD)
else
    since_commit=$(git rev-list --max-parents=0 HEAD | tail -n 1)
fi

exec ./bin/trufflehog git "file://$repo_root" \
    --since-commit "$since_commit" \
    --branch HEAD \
    --results=verified,unknown \
    --fail \
    --trust-local-git-config
