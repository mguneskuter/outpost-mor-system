# Outpost MoR System

Outpost is a merchant-of-record payment platform: a merchant hands it an order, Outpost prices
the tax for the shopper's jurisdiction, collects the gross amount through a payment service
provider (PSP), keeps a double-entry ledger of what it owes each merchant and each tax
authority, and refunds on request. It is one PostgreSQL database and two deployables:

- **Gateway API** (`outpost/gateway-api`, port 8080) authenticates merchants, the operator, and
  PSPs; creates and refunds orders; receives PSP payment events; and serves balance reports.
- **Ledger API** (`outpost/ledger-api`, port 8081) books every payment event as journal entries
  and answers the balance reports. Only the Gateway calls it.

The local platform adds a PSP simulator (`psp-simulator`, port 8083) that hosts the payment page
and sends the webhooks a real PSP would. `merchant-cli` is a merchant's shell for driving the
platform by hand; it is not part of the Compose platform.

## Prerequisites

- Docker with Compose; every service and the test databases run in containers.
- A JDK that can launch Gradle. The build provisions its own pinned Java 21 toolchain through
  Foojay.
- Python 3 and `pre-commit` for the Git hooks; `make setup` installs the pinned TruffleHog and
  Gitleaks binaries into `bin/`.
- `psql` on the `PATH` for `make seed` and `make smoke`, or `OUTPOST_PSQL_BIN` pointing at one.

## Run it

```shell
make setup                 # once: scanners into bin/, Git hooks installed
cp .env.example .env       # then set the secrets; .env is gitignored
make smoke                 # build images, start the platform, migrate, seed, run one merchant flow
make tail ledger           # follow logs: gateway, ledger, psp-simulator, postgres; none for all
make down                  # stop the platform and remove its volume
```

`make merchant-cli` opens a merchant's shell against the running platform. It asks step by
step: which merchant, what to do (create an order, pay it, refund it, read what Outpost owes the
merchant or each tax authority), and for an order which PSP, which shopper country, and which
catalogue items; it prints the result and asks again. The same actions run scripted:
`java -jar merchant-cli/build/libs/merchant-cli.jar order --psp DEMO_PSP --items EBOOK,TSHIRT`,
`pay <order-reference>`, `refund <order-reference>`, `merchants`, `psps`, `catalogue`,
`balance-merchant`, `balance-tax`, and `help`.

`make smoke` builds the images with Buildpacks, starts PostgreSQL, applies the Flyway
migrations, materialises the enum tables, seeds a demo merchant and PSP, starts the services,
and runs `local/merchant_flow.py`: it creates an order for a Dutch shopper, pays it on the
simulator, waits for the capture to be booked, reads both balance reports, refunds the order,
waits for the refund to be booked, and reads the reports again. It exits 0 when every
assertion holds. `make up` alone starts the platform; `make smoke-flow` runs only the flow
against a running platform. `local/README.md` describes the platform's services, volumes,
seed lifecycle, and the read-only journal controls.

## Verify it

| Command          | Runs                                                                                             |
| ---------------- | ------------------------------------------------------------------------------------------------ |
| `make build`     | Compile and every build check: Error Prone, NullAway, Checkstyle, module graph                   |
| `make test`      | Every test; database tests start PostgreSQL through Testcontainers                               |
| `make precommit` | Every content hook across the repository: the build, formatting, scanners, SQL and Markdown lint |
| `make verify`    | Every Gradle root and the seed runner tests, as CI runs them                                     |
| `make format`    | Apply Google Java Format                                                                         |

`./outpost/gradlew -p outpost test -PskipIntegrationTests` runs the tests that need no
database.

## API

The full contracts are `openapi/gateway-api.yaml` and `openapi/ledger-api.yaml` (OpenAPI 3.1).
Every setting and environment variable is listed in `CONFIGURATION.md`.

### Gateway API

A merchant signs every request with `X-Outpost-Api-Key` and `X-Outpost-Signature`, the
Base64 HMAC-SHA-256 of the exact raw body under its HMAC secret. The operator sends its
`X-Outpost-Api-Key` alone. A PSP signs its webhook body with its own secret. Every refused or
failed request answers `{"code": "<CODE>"}`; a `500` adds `correlation_id`, the identifier of
the one log line that records the failure. A bad key or signature answers `401 UNAUTHENTICATED`
on every merchant and operator route, and a body over 1 MiB `413 BODY_TOO_LARGE`; the PSP webhook
answers its `413` with no body.

| Route                             | Caller   | Success | Errors                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| --------------------------------- | -------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST /v1/order`                  | merchant | `201`   | `400` a field's declared `INVALID_*` code, `INVALID_REQUEST`, `UNSUPPORTED_CURRENCY`, `DUPLICATE_MERCHANT_LINE_REFERENCE`, `MIXED_CURRENCIES`, `INVALID_PRODUCT_TYPE`, `TOTAL_AMOUNT_MISMATCH`, `AMOUNT_OVERFLOW`; `401 MERCHANT_NOT_FOUND`; `403 MERCHANT_REQUIRED`; `409 IDEMPOTENCY_CONFLICT`; `422 INVALID_COUNTRY`, `INVALID_STATE`, `TAX_RATE_UNAVAILABLE`, `PSP_UNAVAILABLE`, `MISSING_FEE_CONFIGURATION`, `MISSING_TAX_AUTHORITY`; `503 PSP_RETRYABLE` |
| `POST /v1/order/modification`     | merchant | `202`   | `400` a field's declared `INVALID_*` code, `INVALID_REQUEST`, `UNSUPPORTED_MODIFICATION_TYPE`; `403 MERCHANT_REQUIRED`; `404 ORDER_NOT_FOUND`; `409 ORDER_NOT_PAID`; `422 REFUND_REJECTED`; `503 PSP_RETRYABLE`                                                                                                                                                                                                                                                |
| `GET /v1/psps`                    | merchant | `200`   |                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `GET /v1/report/balance/tax`      | operator | `200`   | `403 OPERATOR_REQUIRED`                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `GET /v1/report/balance/merchant` | both     | `200`   | `401 MERCHANT_NOT_FOUND`; a merchant receives only its own balances                                                                                                                                                                                                                                                                                                                                                                                            |
| `POST /v1/psp/{pspCode}/webhook`  | PSP      | `200`   | acknowledgements carry `ACCEPTED`, or `UNKNOWN_PAYMENT`, `FOREIGN_PAYMENT`, `PSP_REFERENCE_MISMATCH` for an event that matches no payment; `400 INVALID_PAYLOAD`; `401 INVALID_SIGNATURE`; `404 UNKNOWN_PSP`                                                                                                                                                                                                                                                   |

Every route may answer `500 INTERNAL_ERROR`.

### Ledger API

Every request is signed with `X-Outpost-Signature` under the Gateway's key; an unsigned
request answers `401 UNAUTHENTICATED`, a signed request to a route the caller is not granted
`403 FORBIDDEN`, and a body over 1 MiB `413 BODY_TOO_LARGE`.

| Route                                            | Success | Errors                                                            |
| ------------------------------------------------ | ------- | ----------------------------------------------------------------- |
| `POST /v1/accounting-request`                    | `202`   | `400 INVALID_REQUEST`; `409 TRANSACTION_LOCKED`; `503 QUEUE_FULL` |
| `GET /v1/report/balance/tax`                     | `200`   |                                                                   |
| `GET /v1/report/balance/merchant`                | `200`   |                                                                   |
| `GET /v1/report/balance/merchant/{merchantCode}` | `200`   |                                                                   |

`POST /v1/accounting-request` answers `202`, `400`, `409`, and `503` with the request's result:
`success`, `result_code`, `reason`, and the request's `type`, `original_reference`,
`merchant_reference`, `psp_reference`, `psp_code`, `merchant_code`, and `refund_reference`.

## How a payment flows

1. `POST /v1/order` prices each line for the shopper's country, subdivision, and product type,
   stores the shopper, order, and lines in one transaction, then creates the payment at the
   PSP and answers the payment link. The Gateway queues an `ORDER_CREATED` accounting request.
1. The PSP delivers `AUTHORISATION`, `CAPTURE`, and `REFUND` events to the webhook route. Each
   verified event that matches its order is queued as an accounting request.
1. A Gateway thread pool sends each request to the Ledger, which takes a per-payment
   transaction lock, answers `202`, and books it: the payment and its pending fee, the
   authorisation, the capture that moves the net to the merchant and the tax to the tax
   authority, and the refund that reverses them. A `409 TRANSACTION_LOCKED` or
   `503 QUEUE_FULL` answer makes the Gateway re-queue the request after a delay.
1. Each in-memory accounting queue holds at most `accounting-queue.capacity` requests (10,000).
   When the Gateway's queue is full, the PSP webhook answers `503 QUEUE_FULL` so the PSP
   redelivers, and order creation still answers the order but logs its unqueued
   `ORDER_CREATED` request at error.
1. `POST /v1/order/modification` refunds the whole order at the PSP synchronously and stores
   the accepted refund; the Ledger books it when the PSP's `REFUND` event arrives.

## Assumptions

- Each merchant pays one flat fee per currency, configured in basis points of the net amount;
  the fee is never refunded.
- Tax is resolved by the shopper's country, subdivision where one is given, and the line's
  product type, from rates loaded at startup; the rate is applied per line and rounded half
  even.
- Amounts are minor units of one currency per order; FX rates and fees are held per currency
  pair and day.
- A refund covers the whole order; there are no partial refunds or partial captures.
- One idempotency key means one request for the life of the merchant account.

## Limitations

Each is a recorded decision, cited by its section in the decision record, which is kept with
the design documents outside this repository.

- Queued accounting work is lost when a Gateway or Ledger process stops, and only one Gateway
  instance is supported (§12.22).
- A request that reaches the Ledger before its predecessor is booked fails in the Ledger's
  log; the Ledger never retries a booking and stores no outcome, so a merchant learns nothing
  after `202` and there is no merchant-facing status endpoint (§12.22, §10.42, §12.16 MG-03).
- A repeated merchant refund reaches the PSP twice (§12.22).
- A PSP answer the Gateway cannot classify is answered `503 PSP_RETRYABLE` and nothing is
  retried (§12.16 MG-01, open item §13).
- `PSP_RECEIVABLE` is gross of PSP fees; what a PSP owes Outpost is not reported (open item
  §10, §19.1).
- Country and subdivision tax is approximate: one rate per jurisdiction and product type, no
  historical rates (§7.5, §12.16 MG-07).
- Settlement, payouts, disputes, chargebacks, partial captures, partial refunds, and accounting
  periods are out of scope (open items §16, §19, §21).

## Trade-offs

- **A double-entry ledger on PostgreSQL** rather than Formance or TigerBeetle (§1): append-only
  journal tables guarded by triggers, a deferred per-currency balance constraint, and status
  derived from events rather than stored (§2.7, §4). The interview task asks for this choice to
  be defended; the decision record does.
- **In-memory queues and a Ledger-owned transaction lock** instead of a database queue and a
  Worker (§12.22): fewer moving parts, at the cost of the loss and ordering limitations above.
- **Stored timestamps come from the database's `now()`** and are read back with `RETURNING`;
  application code never binds the current time into a row (§12.21).
- **One shared HTTP contract** between the Gateway and the Ledger, declared once in
  `outpost/accounting/api` and served and consumed through Spring HTTP service interfaces
  (§12.20).

## Hooks

Security hooks block private keys and likely secrets. TruffleHog scans the working tree before
commits and Git history before pushes; its working-tree scan excludes `.git/` and gitignored
tool binaries. Gitleaks scans staged changes before commits. Java hooks run Google Java Format,
Checkstyle, and the Gradle build. File, Markdown, Shell, Makefile, and SQL hooks validate or
format their matching files.

Formatting hooks re-stage their changes before the commit continues. Validation and security
violations block the commit until corrected.

`make precommit` skips only the branch-protection hook. That hook protects local commits and
is not a content check, so repository-wide verification must not run it. Direct commits on
`main` still run the hook and are blocked.

The CI workflow in `.github/workflows/ci.yml` runs `make verify` when started by hand; its
automatic triggers are disabled, so `make precommit` is the merge gate.

## Git workflow

`main` is the integration branch. Do not commit directly to it.

1. Update the integration branch with `git switch main` followed by `git pull --ff-only`.
1. Create `mr/<topic>` before staging or committing. Uncommitted work already on `main` moves
   with `git switch -c mr/<topic>`.
1. Verify the change with `make precommit`, then create exactly one commit ahead of
   `origin/main`. If necessary, squash local implementation commits before the first push.
1. Confirm the invariant with `git rev-list --count origin/main..HEAD`; it must print `1`.
1. Push the MR branch for review, then immediately return locally to `main`.
1. Open the pull request. Do not integrate it until review is approved and `make precommit`
   has passed on the rebased branch.
1. Before integration, run `git fetch origin`, rebase the MR branch onto `origin/main`, verify
   it still has exactly one commit, and rerun `make precommit`.
1. If the already-pushed branch changed during rebase, update it only with
   `git push --force-with-lease`, then immediately return locally to `main`.
1. Integrate it locally with `git switch main` followed by `git merge --ff-only mr/<topic>`.
   Push `main` only with explicit authorization.

Never create a merge commit, rebase `main`, or bypass the configured hooks.

### Parallel work

Run completely independent tasks concurrently in separate Git worktrees under the gitignored
`.worktrees/` directory: one `mr/<topic>` branch and one worktree per task, started from a base
containing all declared dependencies, with the repository build run before any change so
baseline failures are visible. A linked worktree contains tracked files only: before baseline
verification, run `git -C <worktree> worktreeinclude apply` to copy the ignored scanner
binaries listed in `.worktreeinclude`. Do not parallelize tasks that modify the same files,
define an interface the other consumes, require ordered migrations, or otherwise depend on each
other's output.

## Layout

| Path                 | Purpose                                                                 |
| -------------------- | ----------------------------------------------------------------------- |
| `.github/workflows/` | The manually started CI workflow.                                       |
| `bin/`               | Gitignored repository-local scanner binaries from `make setup`.         |
| `local/`             | Docker Compose platform, migrations and seed scripts, the smoke flow.   |
| `merchant-cli/`      | Gradle root of the merchant shell.                                      |
| `openapi/`           | The Gateway and Ledger API contracts.                                   |
| `outpost/`           | Gradle root of the Outpost modules, deployables, and Flyway migrations. |
| `psp-simulator/`     | Gradle root of the PSP simulator.                                       |
| `CONFIGURATION.md`   | Every setting and environment variable.                                 |
