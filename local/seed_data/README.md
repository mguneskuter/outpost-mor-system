# Seed data

This directory is reserved for the future non-enum static-data seed lifecycle.
It is intentionally empty.

- `make ensure-static-data` is reserved for the enum/static-data materialisation
  task and is **not** implemented here.
- `make seed` is reserved for non-enum seed data and is **not** implemented
  here, because no concrete non-enum seed data exists yet.

Do not add seed SQL to `outpost/db/migration/`; migrations are DDL only.
