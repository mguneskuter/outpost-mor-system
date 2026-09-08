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

| Command             | Purpose                                                  |
| ------------------- | -------------------------------------------------------- |
| `make all`          | Run the build.                                           |
| `make clean`        | Remove Gradle build outputs.                             |
| `make setup`        | Provision repo-local scanners and install Git hooks.     |
| `make hooks`        | Install or refresh Git hooks and their dependencies.     |
| `make format`       | Apply Google Java Format.                                |
| `make format-check` | Verify Java formatting without changing files.           |
| `make lint`         | Run Checkstyle for main and test sources.                |
| `make build`        | Compile and run all build checks, including Error Prone. |
| `make test`         | Run the test suite.                                      |
| `make precommit`    | Run every content hook across repository files.          |

## Hooks

Security hooks block private keys and likely secrets. TruffleHog scans the working tree before commits and Git history before pushes; its working-tree scan excludes `.git/` and gitignored tool binaries. Gitleaks scans staged changes before commits. Java hooks run Google Java Format, Checkstyle, and the Gradle build. File, Markdown, Shell, Makefile, and SQL hooks validate or format their matching files.

Formatting hooks re-stage their changes before the commit continues. Validation and security violations block the commit until corrected.

`make precommit` skips only the branch-protection hook because this repository is checked out on `main`; direct commits still run that hook and are blocked.

In an emergency, skip hooks with `git commit --no-verify`. Use this only when the change is independently verified and follow up immediately, because it bypasses formatting, static analysis, and secret scanning.

## Git workflow

`main` is the integration branch. Do not commit directly to it.

1. Update `main` and make uncommitted changes there.
1. Move those changes to an MR branch with `git switch -c mr/<topic>`.
1. Commit and push the MR branch for review.
1. After approval, update the MR branch with `git fetch origin` and `git rebase origin/main`.
1. Force-push the rebased MR branch with `git push --force-with-lease` if its review branch already exists.
1. Fast-forward `main` with `git switch main` followed by `git merge --ff-only mr/<topic>`, then push `main`.

The repository permits rebase merges only and local Git rejects non-fast-forward merges.

## Layout

| Path                 | Purpose                                                   |
| -------------------- | --------------------------------------------------------- |
| `.github/workflows/` | Continuous integration workflows.                         |
| `bin/`               | Gitignored repository-local tool binaries.                |
| `deployment/`        | Dockerfiles built into images.                            |
| `local/`             | Local setup, orchestration, and local-only configuration. |
| `outpost/`           | Gradle root and Java modules for the Outpost system.      |
