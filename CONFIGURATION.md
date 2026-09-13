# Configuration reference

Every setting the deployables read, and every variable `.env` supplies to the local platform.
A setting's validation rule is the one its `@ConfigurationProperties` record enforces at
startup; a value outside it stops the service before it serves a request.

## Gateway API (`outpost/gateway-api/src/main/resources/application.properties`)

| Key                                              | Required | Default                                            | Sensitive | Validation                                 |
| ------------------------------------------------ | -------- | -------------------------------------------------- | --------- | ------------------------------------------ |
| `outpost.gateway.ledger.base-url`                | yes      | `${OUTPOST_LEDGER_BASE_URL:http://localhost:8081}` | no        | not blank, a well-formed URL               |
| `outpost.gateway.ledger.hmac-secret`             | yes      | `${OUTPOST_LEDGER_GATEWAY_HMAC_SECRET:}`           | yes       | not blank                                  |
| `outpost.gateway.ledger.connect-timeout`         | yes      | `${OUTPOST_LEDGER_CONNECT_TIMEOUT:PT1S}`           | no        | 100 ms to 30 s, not above the read timeout |
| `outpost.gateway.ledger.read-timeout`            | yes      | `${OUTPOST_LEDGER_READ_TIMEOUT:PT5S}`              | no        | 100 ms to 5 min                            |
| `outpost.gateway.psp-client.connect-timeout`     | yes      | `PT10S`                                            | no        | 100 ms to 30 s, not above the read timeout |
| `outpost.gateway.psp-client.read-timeout`        | yes      | `PT30S`                                            | no        | 100 ms to 5 min                            |
| `outpost.gateway.accounting-queue.worker-count`  | yes      | `1`                                                | no        | 1 to 16                                    |
| `outpost.gateway.accounting-queue.poll-interval` | yes      | `PT1S`                                             | no        | 10 ms to 1 min                             |
| `outpost.gateway.accounting-queue.retry-delay`   | yes      | `PT5S`                                             | no        | 10 ms to 10 min                            |
| `outpost.gateway.accounting-queue.max-attempts`  | yes      | `20`                                               | no        | 1 to 1000                                  |

The Gateway also reads two variables that have no `outpost.*` key: `OUTPOST_HMAC_ENCRYPTION_KEY`
and `OUTPOST_OPERATOR_API_KEY` (below).

## Ledger API (`outpost/ledger-api/src/main/resources/application.properties`)

| Key                                                      | Required | Default                                  | Sensitive | Validation     |
| -------------------------------------------------------- | -------- | ---------------------------------------- | --------- | -------------- |
| `outpost.ledger.gateway-hmac-secret`                     | yes      | `${OUTPOST_LEDGER_GATEWAY_HMAC_SECRET:}` | yes       | not blank      |
| `outpost.ledger.accounting-queue.worker-count`           | yes      | `4`                                      | no        | 1 to 64        |
| `outpost.ledger.accounting-queue.poll-interval`          | yes      | `PT0.1S`                                 | no        | 10 ms to 1 min |
| `outpost.ledger.accounting-queue.transaction-lock-lease` | yes      | `PT5M`                                   | no        | 1 s to 1 h     |

Both deployables read `spring.datasource.url`, `spring.datasource.username`, and
`spring.datasource.password` from `OUTPOST_DB_URL`, `OUTPOST_DB_USER`, and
`OUTPOST_DB_PASSWORD`.

## Environment (`.env`, copied from `.env.example`)

`make up` passes these to the containers; `make seed` and `make smoke` read them on the host.

| Variable                             | Read by                                               | Required | Default in `.env.example`   | Sensitive |
| ------------------------------------ | ----------------------------------------------------- | -------- | --------------------------- | --------- |
| `OUTPOST_DB_PORT`                    | Compose, `make migrate`, `make seed`                  | no       | `5432`                      | no        |
| `OUTPOST_DB_USER`                    | Compose, both deployables, PSP simulator              | no       | `outpost`                   | no        |
| `OUTPOST_DB_PASSWORD`                | Compose, both deployables, PSP simulator              | yes      | `change-me`                 | yes       |
| `OUTPOST_PSP_SIMULATOR_BASE_URL`     | `make seed` (the PSP's stored base URL)               | yes      | `http://psp-simulator:8083` | no        |
| `OUTPOST_PSP_SIMULATOR_API_KEY`      | PSP simulator, `make seed`, `make smoke`              | yes      | `demo-outpost-api-key`      | yes       |
| `OUTPOST_PSP_SIMULATOR_HMAC_SECRET`  | PSP simulator, `make seed`                            | yes      | `demo-hmac-secret`          | yes       |
| `OUTPOST_OPERATOR_API_KEY`           | Gateway API, `make smoke`                             | yes      | `fake-operator-key`         | yes       |
| `OUTPOST_MERCHANT_API_KEY`           | `make seed` (the demo merchant), `make smoke`         | yes      | `demo-outpost-api-key`      | yes       |
| `OUTPOST_MERCHANT_HMAC_SECRET`       | `make seed` (the demo merchant), `make smoke`         | yes      | `demo-hmac-secret`          | yes       |
| `OUTPOST_HMAC_ENCRYPTION_KEY`        | Gateway API (AES-GCM key for stored merchant secrets) | yes      | 32 zero bytes, Base64       | yes       |
| `OUTPOST_LEDGER_GATEWAY_HMAC_SECRET` | Gateway API and Ledger API                            | yes      | `change-me-gateway-ledger`  | yes       |

Compose sets `OUTPOST_DB_URL` and `OUTPOST_LEDGER_BASE_URL` for the containers itself. A deployable
started outside Compose falls back to `localhost` for both, and may set
`OUTPOST_LEDGER_CONNECT_TIMEOUT` and `OUTPOST_LEDGER_READ_TIMEOUT` to override the two Ledger
timeouts above.
