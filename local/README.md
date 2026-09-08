# Local development

This directory contains the scripts, orchestration, and local-only configuration required to run the project on a developer machine. Docker Compose configuration belongs here when a runnable service exists.

## Database

A local PostgreSQL 18 instance runs through Docker Compose. The image is digest-pinned, data lives in the named volume `outpost-postgres-data`, and the application role `outpost` is a non-superuser. Configure the local variables in the repository-root `.env` (copy it from `.env.example`) and keep secrets out of version control.

| Variable              | Purpose                                                                      |
| --------------------- | ---------------------------------------------------------------------------- |
| `OUTPOST_DB_PORT`     | Host port mapped to the container's PostgreSQL port (default `5432`).        |
| `OUTPOST_DB_USER`     | Application role that owns the `outpost` database (default `outpost`).       |
| `OUTPOST_DB_PASSWORD` | Password for the application role. Required; no usable default is committed. |

| Command          | Does                                                                                                 |
| ---------------- | ---------------------------------------------------------------------------------------------------- |
| `make db-up`     | Start the PostgreSQL container and wait for a healthy `pg_isready` check.                            |
| `make db-status` | Show container status and report `pg_isready` for the `outpost` database.                            |
| `make db-down`   | Stop the container. Preserves the named volume; it never deletes data.                               |
| `make migrate`   | Run Flyway migrations (DDL only) against the configured database. Reports applied and pending state. |

There is no automated database reset or volume-deletion target.

### Migrations

Flyway uses a single repository-level migration location at `outpost/db/migration/`, the `public` schema, and its default `flyway_schema_history` history table. Migrations never run from an application startup path; run `make migrate` explicitly.

### Seed lifecycle

`local/seed_data/` is reserved for the future non-enum static-data seed. The targets `make ensure-static-data` (enum/static-data materialisation) and `make seed` (non-enum seed data) are also reserved by the static-data tasks and are intentionally not implemented here. Migrations remain DDL-only.
