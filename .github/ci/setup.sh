#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

require_command() {
    local command_name="$1"
    local installation_command="$2"

    if ! command -v "${command_name}" >/dev/null 2>&1; then
        printf '%s is required. Install it with: %s\n' \
            "${command_name}" "${installation_command}" >&2
        exit 1
    fi
}

require_command python3 'python3 -m pip install pre-commit'
require_command pre-commit 'python3 -m pip install pre-commit'

"${repository_root}/.github/ci/install-security-tools.sh"
