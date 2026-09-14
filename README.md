# Outpost

Outpost is a merchant-of-record payment platform. A merchant hands it an order; Outpost prices
the tax for the shopper's jurisdiction, collects the gross amount through a payment service
provider (PSP), keeps a double-entry ledger of what it owes each merchant and each tax
authority, and refunds on request.

**Stack:** Java 21, Spring Boot, MyBatis, PostgreSQL 18, Flyway, Gradle, Docker Compose.

## Contents

- [Architecture](#architecture)
- [Quick start](#quick-start)
- [Using the platform](#using-the-platform)
- [Development](#development)
- [API](#api)
- [How a payment flows](#how-a-payment-flows)
- [Design notes](#design-notes)
- [Repository layout](#repository-layout)
- [Contributing](#contributing)

## Architecture

Outpost is one PostgreSQL database and two services. The local platform adds two supporting
services and a command-line tool.

| Component         | Port | Role                                                                                                                                          |
| ----------------- | ---- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| **Gateway API**   | 8080 | Authenticates merchants, the operator, and PSPs; creates and refunds orders; receives PSP payment events; serves balance reports.             |
| **Ledger API**    | 8081 | Books every payment event as journal entries and answers balance queries. Only the Gateway calls it; Compose does not publish it to the host. |
| **PSP simulator** | 8083 | Stands in for a real PSP: hosts the payment page, accepts test cards, and sends the webhooks a PSP would.                                     |
| **Backoffice**    | 8090 | Browser view of payments, balance accounts, and reports. No login.                                                                            |
| **Merchant CLI**  | –    | A merchant's shell for driving the platform by hand. Runs on the host, not in Compose.                                                        |

```
merchant / merchant-cli ──▶ Gateway API ──▶ PSP simulator
                                │  ▲             │
                                │  └── webhooks ─┘
                                ▼
                            Ledger API ──▶ PostgreSQL ◀── Backoffice (read-only)
```

## Quick start

### Prerequisites

| Tool                       | Notes                                                                                    |
| -------------------------- | ---------------------------------------------------------------------------------------- |
| Docker with Compose        | Every service and the test databases run in containers.                                  |
| A JDK (any recent version) | Only to launch Gradle; the build provisions its own pinned Java 21 toolchain via Foojay. |
| Python 3 and `pre-commit`  | Git hooks and the smoke flow. `python3 -m pip install --user pre-commit`                 |
| `psql`                     | For `make seed` and `make smoke`. Set `OUTPOST_PSQL_BIN` if it is not on your `PATH`.    |

### Run the platform

```shell
make setup             # once: install Git hooks and the secret scanners into bin/
cp .env.example .env   # set OUTPOST_DB_PASSWORD and the other secrets; .env is gitignored
make smoke             # build images, start everything, migrate, seed, run one end-to-end payment
```

`make smoke` exits 0 when the whole flow holds: it creates an order for a Dutch shopper, pays it
on the simulator, waits for the capture to be booked, reads both balance reports, refunds the
order, waits for the refund to be booked, and reads the reports again.

Once it passes, open the Backoffice at <http://localhost:8090> or start a merchant shell with
`make merchant-cli`.

### Everyday commands

| Command             | Does                                                                                    |
| ------------------- | --------------------------------------------------------------------------------------- |
| `make up`           | Build images, start the platform, migrate, load enum tables, and seed the demo data.    |
| `make smoke-flow`   | Run the end-to-end payment flow against an already running platform.                    |
| `make status`       | Show container status and whether PostgreSQL is ready.                                  |
| `make tail ledger`  | Follow logs for `gateway`, `ledger`, `psp-simulator`, `backoffice`, `postgres`, or all. |
| `make down`         | Stop the platform and remove its data volume.                                           |
| `make merchant-cli` | Open a merchant shell against the running platform.                                     |
| `make backoffice`   | Run the Backoffice on the host instead of in Compose (stop the container first).        |

`make up` is not re-runnable on a volume that already holds data; run `make down` first.
Database-only targets (`migrate`, `ensure-static-data`, `seed`, `journal-controls`) are
described in [`local/README.md`](local/README.md). Every setting and environment variable is
listed in [`CONFIGURATION.md`](CONFIGURATION.md).

## Using the platform

### Merchant CLI

`make merchant-cli` opens a guided session: pick a merchant, then an action (create an order,
pay it, refund it, show its status, or read a balance report for the merchant or the platform).
For an order it asks which PSP, which shopper country, and which catalogue items. Paying posts
the card to the PSP's payment route, as the payment page would, then waits for the Ledger to
book the outcome the PSP reports by webhook: authorised and captured, or refused.

The same actions run scripted from the built jar:

```shell
java -jar merchant-cli/build/libs/merchant-cli.jar order --psp DEMO_PSP --items EBOOK,TSHIRT
java -jar merchant-cli/build/libs/merchant-cli.jar pay <order-reference> [--card <number>]
java -jar merchant-cli/build/libs/merchant-cli.jar refund <order-reference>
java -jar merchant-cli/build/libs/merchant-cli.jar status <order-reference>
java -jar merchant-cli/build/libs/merchant-cli.jar report --from 2026-09-01 --to 2026-09-30
java -jar merchant-cli/build/libs/merchant-cli.jar report-platform --from 2026-09-01 --to 2026-09-30
java -jar merchant-cli/build/libs/merchant-cli.jar merchants | psps | catalogue | help
```

The shell reads its credentials from `.env`.

### Backoffice

The Backoffice at <http://localhost:8090> reads Outpost's tables over a read-only connection
and calls the Gateway and the PSP the way the shell does. Its pages load DaisyUI and Tailwind
from a CDN, so the browser needs internet access; the server does not.

- **Payments** creates and pays an order in one step, and lists every payment with gross, net,
  tax, platform fee, shopper country, goods types, and the status the Ledger booked last. Captured
  payments have a refund button. Each order links to its transactions and events, its journal
  entries line by line, and the balance accounts it posted to.
- **Balance accounts** lists every balance account of every merchant, tax authority, PSP, and
  platform account in every operating currency, with filters and per-currency totals. Each row
  shows debits and credits separately and the balance on its side (`64.85 Cr`, `1.25 Dr`). With
  no filter, total debits equal total credits: the double-entry check across the ledger.
- **Reports** reads the Gateway's period balance report for a merchant or for the platform.

Inside the Compose network the simulator's payment links still name `localhost:8083`, so the
container pays through `OUTPOST_BACKOFFICE_PSP_BASE_URL` (`http://psp-simulator:8083`). On the
host that setting is empty and the link is used as published.

## Development

All Gradle commands run from the repository root with `-p <gradle-root>`. The main build is
`outpost/`; `psp-simulator/`, `merchant-cli/`, and `backoffice/` are separate Gradle roots.

| Command          | Runs                                                                                           |
| ---------------- | ---------------------------------------------------------------------------------------------- |
| `make build`     | Compile and every build check: Error Prone, NullAway, Checkstyle, module graph                 |
| `make test`      | Every test; database tests start PostgreSQL through Testcontainers                             |
| `make format`    | Apply Google Java Format                                                                       |
| `make precommit` | Every content hook across the repository: build, formatting, secret scanners, SQL and Markdown |
| `make verify`    | Build and test every Gradle root plus the seed runner tests; the CI gate                       |

Useful narrower invocations:

```shell
./outpost/gradlew -p outpost test -PskipIntegrationTests          # tests that need no database
./outpost/gradlew -p outpost :account:domain:test --tests <FQCN>   # one test class
```

Database tests use Testcontainers and need Docker, unless `OUTPOST_TEST_DB_URL`,
`OUTPOST_TEST_DB_USER`, and `OUTPOST_TEST_DB_PASSWORD` point at a PostgreSQL instance.

### Conventions the build enforces

- Every package has a `package-info.java` annotated `@NullMarked`; NullAway treats missing
  marking as an error. Genuine absence is `@Nullable`.
- Domain modules depend only on other domain modules, the JDK, JSpecify, and `slf4j-api`.
  Spring, MyBatis, SQL, and HTTP live in `repository` and `persistence` modules and in the
  deployables.
- A new module or dependency edge is declared in both `outpost/settings.gradle` and
  `outpost/buildSrc/.../ModuleGraphSpec.groovy`; the build fails on a mismatch.
- Migrations live in `outpost/db/migration/` as `V<yyyyMMdd><NN>__<snake_case>.sql`, PostgreSQL
  dialect, DDL only. They never run at application startup; `make migrate` applies them.
- Checkstyle (Google checks) allows zero warnings; Error Prone runs on every compile.

### Git hooks

`make setup` installs pre-commit hooks. On commit: TruffleHog and Gitleaks scan for secrets,
Google Java Format and Checkstyle run, the Gradle build runs, and file, Markdown, shell,
Makefile, and SQL hooks validate or format their files. Formatting hooks re-stage their changes;
validation and security failures block the commit. On push, TruffleHog scans Git history.

`make precommit` runs every content hook across the whole tree. It skips only the
branch-protection hook, which still blocks direct commits on `main`.

The CI workflow (`.github/workflows/ci.yml`) runs `make verify` when started by hand; its
automatic triggers are disabled, so `make precommit` on the rebased branch is the merge gate.

## API

The full contracts are [`openapi/gateway-api.yaml`](openapi/gateway-api.yaml) and
[`openapi/ledger-api.yaml`](openapi/ledger-api.yaml) (OpenAPI 3.1).

### Authentication and errors

A merchant signs every request with `X-Outpost-Api-Key` and `X-Outpost-Signature`, the Base64
HMAC-SHA-256 of the exact raw body under its HMAC secret. The operator sends
`X-Outpost-Api-Key` alone. A PSP signs its webhook body with its own secret.

Every refused or failed request answers `{"code": "<CODE>"}`; a `500` adds `correlation_id`,
the identifier of the one log line that records the failure. A bad key or signature answers
`401 UNAUTHENTICATED` on every merchant and operator route; a body over 1 MiB answers
`413 BODY_TOO_LARGE` (the PSP webhook answers its `413` with no body). Every route may answer
`500 INTERNAL_ERROR`.

### Gateway API

| Route                            | Caller   | Success | Errors                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| -------------------------------- | -------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST /v1/order`                 | merchant | `201`   | `400` a field's declared `INVALID_*` code, `INVALID_REQUEST`, `UNSUPPORTED_CURRENCY`, `DUPLICATE_MERCHANT_LINE_REFERENCE`, `MIXED_CURRENCIES`, `INVALID_PRODUCT_TYPE`, `TOTAL_AMOUNT_MISMATCH`, `AMOUNT_OVERFLOW`; `401 MERCHANT_NOT_FOUND`; `403 MERCHANT_REQUIRED`; `409 IDEMPOTENCY_CONFLICT`; `422 INVALID_COUNTRY`, `INVALID_STATE`, `TAX_RATE_UNAVAILABLE`, `PSP_UNAVAILABLE`, `MISSING_FEE_CONFIGURATION`, `MISSING_TAX_AUTHORITY`; `503 PSP_RETRYABLE` |
| `POST /v1/order/modification`    | merchant | `202`   | `400` a field's declared `INVALID_*` code, `INVALID_REQUEST`, `UNSUPPORTED_MODIFICATION_TYPE`; `403 MERCHANT_REQUIRED`; `404 ORDER_NOT_FOUND`; `409 ORDER_NOT_PAID`; `422 REFUND_REJECTED`; `503 PSP_RETRYABLE`                                                                                                                                                                                                                                                |
| `GET /v1/psps`                   | merchant | `200`   |                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `GET /v1/report?from=&to=`       | both     | `200`   | `400 INVALID_REPORT_PERIOD`, `INVALID_REQUEST`; `401 MERCHANT_NOT_FOUND`. Builds a balance report over at most 30 days and answers its `report_url`. A merchant's report covers its own account; the operator's covers every merchant, tax authority, and platform account.                                                                                                                                                                                    |
| `GET /v1/report/{reportId}`      | both     | `200`   | `400 INVALID_REQUEST`; `404 REPORT_NOT_FOUND`. The Gateway keeps the 100 most recent reports in memory; any authenticated key reads any of them.                                                                                                                                                                                                                                                                                                               |
| `POST /v1/psp/{pspCode}/webhook` | PSP      | `200`   | Acknowledgements carry `ACCEPTED`, or `UNKNOWN_ORDER` / `PSP_REFERENCE_MISMATCH` for an event that matches no order; `400 INVALID_PAYLOAD`; `401 INVALID_SIGNATURE`; `404 UNKNOWN_PSP`; `503 QUEUE_FULL`                                                                                                                                                                                                                                                       |

### Ledger API

Every request is signed with `X-Outpost-Signature` under the Gateway's key. An unsigned request
answers `401 UNAUTHENTICATED`, a signed request to a route the caller is not granted
`403 FORBIDDEN`, and a body over 1 MiB `413 BODY_TOO_LARGE`.

| Route                                            | Success | Errors                                                            |
| ------------------------------------------------ | ------- | ----------------------------------------------------------------- |
| `POST /v1/accounting-request`                    | `202`   | `400 INVALID_REQUEST`; `409 TRANSACTION_LOCKED`; `503 QUEUE_FULL` |
| `GET /v1/report/balance?from=&to=`               | `200`   | `400 INVALID_REQUEST`                                             |
| `GET /v1/report/balance/merchant/{merchantCode}` | `200`   | `400 INVALID_REQUEST`                                             |

`POST /v1/accounting-request` answers `202`, `400`, `409`, and `503` with the request's result:
`success`, `result_code`, `reason`, and the request's `type`, `original_reference`,
`merchant_reference`, `psp_reference`, `psp_code`, `merchant_code`, and `refund_reference`.

## How a payment flows

1. `POST /v1/order` prices each line for the shopper's country, subdivision, and product type,
   stores the shopper, order, and lines in one transaction, then creates the payment at the PSP
   and answers the payment link. The Gateway queues an `ORDER_CREATED` accounting request.
1. The PSP delivers `AUTHORISATION`, `CAPTURE`, and `REFUND` events to the webhook route. Each
   verified event that matches its order is queued as an accounting request.
1. A Gateway thread pool sends each request to the Ledger, which takes a per-payment transaction
   lock, answers `202`, and books it: the payment and its pending fee, the authorisation, the
   capture that moves the net to the merchant and the tax to the tax authority, and the refund
   that reverses them. A `409 TRANSACTION_LOCKED` or `503 QUEUE_FULL` answer makes the Gateway
   re-queue the request after a delay.
1. Each in-memory accounting queue holds at most `accounting-queue.capacity` requests (10,000).
   When the Gateway's queue is full, the PSP webhook answers `503 QUEUE_FULL` so the PSP
   redelivers; order creation still answers the order but logs its unqueued `ORDER_CREATED`
   request at error level.
1. `POST /v1/order/modification` refunds the whole order at the PSP synchronously and stores the
   accepted refund; the Ledger books it when the PSP's `REFUND` event arrives.

## Design notes

### Assumptions

- Each merchant pays one flat fee per currency, configured in basis points of the net amount;
  the fee is never refunded.
- Tax is resolved by the shopper's country, subdivision where one is given, and the line's
  product type, from rates loaded at startup. The rate is applied per line and rounded half even.
- Amounts are minor units of one currency per order. FX rates and fees are held per currency
  pair and day.
- A refund covers the whole order; there are no partial refunds or partial captures.
- One idempotency key means one request for the life of the merchant account.

### Key decisions

- **A double-entry ledger on PostgreSQL** rather than a dedicated ledger product: append-only
  journal tables guarded by triggers, a deferred per-currency balance constraint, and payment
  status derived from events rather than stored.
- **In-memory queues and a Ledger-owned transaction lock** instead of a database-backed queue
  and a separate worker process: fewer moving parts, at the cost of the limitations below.
- **Stored timestamps come from the database's `now()`** and are read back with `RETURNING`;
  application code never binds the current time into a row.
- **One shared HTTP contract** between the Gateway and the Ledger, declared once in
  `outpost/accounting/api` and served and consumed through Spring HTTP service interfaces.

### Known limitations

- Queued accounting work is lost when a Gateway or Ledger process stops, and only one Gateway
  instance is supported.
- A request that reaches the Ledger before its predecessor is booked fails in the Ledger's log.
  The Ledger never retries a booking and stores no outcome, so a merchant learns nothing after
  `202`, and there is no merchant-facing status endpoint.
- A repeated merchant refund reaches the PSP twice.
- A PSP answer the Gateway cannot classify is answered `503 PSP_RETRYABLE`; nothing is retried.
- `PSP_RECEIVABLE` is gross of PSP fees; what a PSP owes Outpost is not reported.
- Tax is approximate: one rate per jurisdiction and product type, with no historical rates.
- Settlement, payouts, disputes, chargebacks, partial captures, partial refunds, and accounting
  periods are out of scope.

## Repository layout

| Path                 | Purpose                                                                 |
| -------------------- | ----------------------------------------------------------------------- |
| `outpost/`           | Gradle root of the Outpost modules, deployables, and Flyway migrations. |
| `psp-simulator/`     | Gradle root of the PSP simulator.                                       |
| `merchant-cli/`      | Gradle root of the merchant shell.                                      |
| `backoffice/`        | Gradle root of the browser view of the platform.                        |
| `openapi/`           | The Gateway and Ledger API contracts.                                   |
| `local/`             | Docker Compose platform, migration and seed scripts, the smoke flow.    |
| `.github/workflows/` | The manually started CI workflow.                                       |
| `bin/`               | Gitignored scanner binaries installed by `make setup`.                  |
| `CONFIGURATION.md`   | Every setting and environment variable.                                 |

## Contributing

`main` is the integration branch; nothing is committed to it directly.

1. Update `main`: `git switch main && git pull --ff-only`.
1. Branch: `git switch -c mr/<topic>`.
1. Verify with `make precommit`, then commit. A branch carries exactly one commit ahead of
   `origin/main`; squash local commits before the first push. Check with
   `git rev-list --count origin/main..HEAD`, which must print `1`.
1. Push the branch and open a pull request. Commit messages read `type(scope): imperative summary`, for example `feat(tax): resolve subdivision rates`.
1. Before integration, `git fetch origin`, rebase onto `origin/main`, confirm the branch still
   has one commit, and rerun `make precommit`. Update the pushed branch only with
   `git push --force-with-lease`.
1. Integrate with `git switch main && git merge --ff-only mr/<topic>`.

Never create a merge commit, rebase `main`, or bypass the hooks with `--no-verify`.

### Working on several branches at once

Independent tasks can run concurrently in separate Git worktrees under the gitignored
`.worktrees/` directory, one `mr/<topic>` branch per worktree. A linked worktree contains
tracked files only; run `git -C <worktree> worktreeinclude apply` to copy the gitignored scanner
binaries listed in `.worktreeinclude` before running `make precommit` there. Do not parallelise
tasks that modify the same files, define an interface the other consumes, or need ordered
migrations.
