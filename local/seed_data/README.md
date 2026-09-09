# Seed data

This directory holds non-enum static-data seed SQL. Files are executed by
`make seed` in lexicographic order, after migration and enum-backed
reference-data seeding.
It is intentionally empty until a production seed file is contributed.

Do not add seed SQL to `outpost/db/migration/`; migrations are DDL only.

Run `make seed-test` to verify the runner without a database.
