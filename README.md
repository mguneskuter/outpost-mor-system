# Outpost MoR System

Repository foundation for the Outpost MoR system.

## Prerequisites

Python 3, `pre-commit`, and a JDK capable of launching Gradle are required. The build uses Foojay to provision its pinned Java 21 compilation toolchain. Do not manually install ShellCheck, Checkmake, SQLFluff, mdformat, Bashate, TruffleHog, Gitleaks, or the hooks from `pre-commit-hooks`.

## Setup

Run the following command from the repository root:

```shell
make setup
```

It downloads verified, pinned TruffleHog and Gitleaks binaries into `bin/`, then installs the Git hooks and their managed dependencies. Re-running it leaves already pinned binaries in place.

## Commands

| Command                   | Purpose                                                  |
| ------------------------- | -------------------------------------------------------- |
| `make all`                | Run the build.                                           |
| `make clean`              | Remove Gradle build outputs.                             |
| `make setup`              | Provision repo-local scanners and install Git hooks.     |
| `make hooks`              | Install or refresh Git hooks and their dependencies.     |
| `make format`             | Apply Google Java Format.                                |
| `make format-check`       | Verify Java formatting without changing files.           |
| `make lint`               | Run Checkstyle for main and test sources.                |
| `make build`              | Compile and run all build checks, including Error Prone. |
| `make test`               | Run the test suite.                                      |
| `make verify`             | Verify every Gradle root and the seed runner.            |
| `make precommit`          | Run every content hook across repository files.          |
| `make up`                 | Start the local platform Compose services.               |
| `make status`             | Show local platform container status.                    |
| `make down`               | Stop the local platform and remove its named volume.     |
| `make migrate`            | Run Flyway migrations against the local database.        |
| `make ensure-static-data` | Materialise enum reference data explicitly.              |
| `make seed`               | Run deterministic non-enum seed SQL explicitly.          |
| `make lifecycle`          | Run migration, enum materialisation, then seed SQL.      |
| `make journal-controls`   | Run the read-only journal integrity controls.            |

## Framework foundation

The `outpost/framework/persistence` module is the shared Spring/MyBatis/JDBC
and PostgreSQL/Testcontainers foundation. Spring Boot creates exactly one Hikari
datasource per deployable from that deployable's `spring.datasource.*`
properties; the foundation supplies shared mapper-marker discovery, transaction
and datasource validation conventions, Flyway tooling, and reusable PostgreSQL
18 Testcontainers fixtures. It introduces no business tables, mappers, or data.

`framework/logging` accepts only owner-defined `LogFields` descriptors and emits
safe JSON context fields. `framework/security` provides Java-standard-library
HMAC-SHA-256 signing/verification and narrow API-key and service-hop seams.
Neither module owns business keys, HTTP policy, persistence, request bodies, or
secret storage. Gateway API, Ledger API, and Worker include Spring Boot Actuator
for later same-port health and metrics exposure.

Service startup follows one fail-closed sequence:

```text
database reachable
-> VerifyStaticData (read-only)
-> applicable immutable tax/FX snapshot validation
-> ready
```

Health requests never run the verification or materialisation steps again.

## Hooks

Security hooks block private keys and likely secrets. TruffleHog scans the working tree before commits and Git history before pushes; its working-tree scan excludes `.git/` and gitignored tool binaries. Gitleaks scans staged changes before commits. Java hooks run Google Java Format, Checkstyle, and the Gradle build. File, Markdown, Shell, Makefile, and SQL hooks validate or format their matching files.

Formatting hooks re-stage their changes before the commit continues. Validation and security violations block the commit until corrected.

`make precommit` skips only the branch-protection hook. That hook protects local
commits and is not a content check, so repository-wide verification and CI must
not run it. Direct commits on `main` still run the hook and are blocked.

CI runs for pull requests targeting `main` and for pushes to `main`. Pull-request
CI is the merge gate. The post-merge `main` run is a regression signal and cannot
retroactively block a merge that has already completed.

In an emergency, skip hooks with `git commit --no-verify`. Use this only when the change is independently verified and follow up immediately, because it bypasses formatting, static analysis, and secret scanning.

## Git workflow

`main` is the integration branch. Do not commit directly to it.

1. Update the integration branch with `git switch main` followed by
   `git pull --ff-only`.
1. Create `mr/<topic>` before staging or committing. Uncommitted work already on
   `main` moves with `git switch -c mr/<topic>`.
1. Verify the change with `make precommit`, then create exactly one commit ahead
   of `origin/main`. If necessary, squash local implementation commits before
   the first push.
1. Confirm the invariant with `git rev-list --count origin/main..HEAD`; it must print `1`.
1. Push the MR branch for review, then immediately return locally to `main`.
1. Open the pull request. Do not integrate it until review is approved and the
   `CI / verify` status check passes.
1. Before integration, run `git fetch origin`, rebase the MR branch onto
   `origin/main`, verify it still has exactly one commit, and rerun
   `make precommit`.
1. If the already-pushed branch changed during rebase, update it only with
   `git push --force-with-lease`, then immediately return locally to `main`.
1. After the rebased commit is approved and green, integrate it locally with
   `git switch main` followed by `git merge --ff-only mr/<topic>`. Push `main`
   only with explicit authorization.

Never commit directly to `main`, create a merge commit, rebase `main`, or bypass the configured hooks. The current private repository plan does not support required status checks. Until the repository is public or its plan is upgraded, the maintainer must verify `CI / verify` succeeded before integration; the workflow file alone cannot enforce that gate.

### Parallel work

Run completely independent tasks concurrently in separate Git worktrees. Use
one `mr/<topic>` branch and one worktree per task; never let two tasks share a
branch or working directory. Keep worktrees under the gitignored `.worktrees/`
directory, start each from a base containing all declared dependencies, and run
the repository build before making changes so baseline failures are visible.
A linked worktree contains tracked files only: before baseline verification, run
`git -C <worktree> worktreeinclude apply` to copy the ignored scanner binaries
listed in `.worktreeinclude`, and read other ignored local context, such as
`.docs`, from the primary checkout.

Do not parallelize tasks that modify the same files, define an interface the
other consumes, require ordered migrations, or otherwise depend on each other's
output. Rebase and integrate each completed MR through the workflow above, then
remove only clean worktrees whose branches have been integrated.

## Layout

| Path                 | Purpose                                                   |
| -------------------- | --------------------------------------------------------- |
| `.github/workflows/` | Docker/CI workflows.                                      |
| `bin/`               | Gitignored repository-local tool binaries.                |
| `deployment/`        | Dockerfiles built into images.                            |
| `local/`             | Local setup, orchestration, and local-only configuration. |
| `outpost/`           | Gradle root and Java modules for the Outpost system.      |
