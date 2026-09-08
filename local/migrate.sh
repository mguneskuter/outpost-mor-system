#!/usr/bin/env bash
set -euo pipefail

# Runs Flyway against the configured database via the narrow Flyway CLI.
# The CLI lives in the `cli` source set and never ships in a production runtime.

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"${repository_root}/outpost/gradlew" -p outpost \
    :common-persistence:writeCliClasspath :common-persistence:compileCliJava >/dev/null

java -Doutpost.migration.location="${repository_root}/outpost/db/migration" \
    -cp "$(cat "${repository_root}/outpost/common-persistence/build/cli/classpath.txt")" \
    com.outpost.persistence.flyway.FlywayCli \
    "${OUTPOST_DB_URL:-jdbc:postgresql://localhost:${OUTPOST_DB_PORT:-5432}/outpost}" \
    "${OUTPOST_DB_USER:-outpost}" \
    "${OUTPOST_DB_PASSWORD}"
