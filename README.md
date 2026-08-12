# dc-api-v2

Library-free reimplementation of `dc-api` (the Spring Boot pet-sitting marketplace API) on a
dependency-free foundation, mirroring the architecture proven in
[`condominios/api-v2`](../../condominios/api-v2):

- **HTTP server**: the JDK's built-in `com.sun.net.httpserver.HttpServer`, with a small hand-rolled
  path-pattern `Router` (`{name}` segments, CORS, a JSON error envelope).
- **Persistence**: plain JDBC over a hand-rolled connection pool — no JPA/Hibernate.
- **JSON**: a hand-rolled reader/writer (`org.doscolas.json.Json`) — no Jackson.
- **Auth**: a hand-rolled HS256 JWT signer/verifier — no JJWT, no Spring Security.
- **Payments**: Fintoc (Chile bank transfers) — Checkout Sessions for collecting from owners
  (a hosted Fintoc-redirect page, no client-side widget) and Transfers for paying sitters out.
  Transbank and a since-abandoned Stripe Connect plan both stay out (see "Known gaps").
- **Schema migrations**: a small hand-rolled versioned-SQL runner (`org.doscolas.db.MigrationRunner`)
  applies `src/main/resources/db/migrations/V*__*.sql` automatically at startup, tracked in a
  `schema_migrations` table — no Flyway, and nothing to run by hand anymore.

Runtime dependencies: the PostgreSQL JDBC driver, `at.favre.lib:bcrypt` (hand-rolling password
hashing would be a real security risk), and Jakarta Mail + its Angus reference implementation for
SMTP (same reasoning — the SMTP protocol isn't worth reinventing). Everything else is hand-written
on top of the JDK standard library.

## Running

```bash
mvn package
java -jar target/dc-api-v2.jar
```

Schema migrations run automatically on startup against whatever `DB_URL` points at — no manual
`psql -f` step. First run creates everything from scratch; running against a database that already
has dc-api-v2's tables (e.g. from before migrations existed) is safe and just gets retroactively
recorded as up to date.

Server listens on `http://localhost:8080/api` by default (same port/context path as `dc-api`, so
`dc-ui` can point at either backend unchanged).

## Configuration

Every setting below is resolved by `org.doscolas.config.Env`, first match wins:

1. a real environment variable of that name — always the final word, e.g. for prod/CI;
2. the matching key in `config.yml` (`org.doscolas.config.ConfigFile`) — same lookup order as
   `logging.yml`/`LoggingConfig`: `CONFIG_FILE` env var path, then `./config.yml` in the working
   directory, then the copy bundled in `src/main/resources/config.yml`;
3. the hardcoded default the call site passes, if neither of the above is set.

The shipped `config.yml` carries the same dev-friendly defaults previously hardcoded in
`AppConfig`. To override without rebuilding the jar — required for anything secret
(`DB_PASSWORD`, `JWT_SECRET`, `FINTOC_*`, `SMTP_PASSWORD`) — drop your own `config.yml` next to
the jar or point `CONFIG_FILE` at a path; that file is gitignored, unlike the shipped one.

| Variable | Default |
|---|---|
| `PORT` | `8080` |
| `CONTEXT_PATH` | `/api` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/doscolas` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `37dominga` |
| `DB_POOL_SIZE` | `10` |
| `JWT_SECRET` | (dev default — override in production) |
| `JWT_EXPIRATION_MS` | `86400000` (24h) |
| `FRONTEND_URL` | `http://localhost:3000` |
| `LOG_LEVEL` | `INFO` |
| `SMTP_HOST` | *(blank — logs emails instead of sending; see below)* |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | *(blank)* |
| `SMTP_PASSWORD` | *(blank)* |
| `SMTP_STARTTLS` | `true` |
| `SMTP_FROM_ADDRESS` | `no-reply@doscolas.cl` |
| `SMTP_FROM_NAME` | `Dos Colas` |
| `RATE_LIMIT_MAX_REQUESTS` | `10` (per window, per IP, per auth endpoint) |
| `RATE_LIMIT_WINDOW_MS` | `60000` |
| `FINTOC_API_URL` | `https://api.fintoc.com` |
| `FINTOC_SECRET_KEY` | `sk_test_placeholder` — get a real one from dashboard.fintoc.com |
| `FINTOC_ACCOUNT_ID` | `acc_placeholder` — the Fintoc account Transfers are sent *from* |
| `FINTOC_WEBHOOK_SECRET` | `whsec_placeholder` — from the webhook endpoint's dashboard page |
| `FINTOC_JWS_PRIVATE_KEY` | *(blank)* — see "Payments (Fintoc)" below |
| `PLATFORM_FEE_PERCENTAGE` | `0.10` |
| `PAYOUT_MAX_ATTEMPTS` | `3` |
| `PAYOUT_PROCESS_INTERVAL_MS` | `60000` |
| `PAYOUT_POLL_INTERVAL_MS` | `300000` |

### Payments (Fintoc)

- **Collecting from owners** (`POST /payments`): creates a Fintoc Checkout Session and returns its
  `redirectUrl` — the frontend does a full-page redirect there, no JS widget/public key needed.
  Fintoc calls back on `checkout_session.finished`/`.expired` via `POST /webhooks/fintoc`
  (HMAC-verified, see `FintocWebhookVerifier`); `POST /payments/{id}/fintoc-sync` is an eager
  fallback the result page calls in case the webhook hasn't landed yet.
- **Paying sitters out** (`PayoutService` + `FintocClient.initiateTransfer`): Fintoc's Transfers
  API additionally requires every request to carry an RS256-signed `Fintoc-JWS-Signature` header —
  hand-rolled in `JwsSigner`, no JOSE library, same spirit as the hand-rolled JWT signer. Generate
  and register a key pair once per environment:
  ```
  openssl genrsa -out jws_private.pem 2048
  openssl pkcs8 -topk8 -inform PEM -in jws_private.pem -out jws_private_pkcs8.pem -nocrypt
  ```
  Upload `jws_private.pem`'s matching public key at dashboard.fintoc.com → API Keys → JWS Public
  Keys, then set `FINTOC_JWS_PRIVATE_KEY` to the contents of `jws_private_pkcs8.pem`. Without it,
  Transfers calls fail loudly (`PayoutProviderException`) rather than silently doing nothing —
  Checkout Sessions (owner-facing payment collection) work fine either way.
- **Note on API versioning:** Checkout Sessions live under `/v1/checkout_sessions` for this
  account, not `/v2/` — confirmed against the live API, `/v2/checkout_sessions` 404s with
  `unrecognized_request` even though `/v2/accounts` and `/v2/transfers` both work. Fintoc versions
  per-resource, not with one blanket API version, so don't assume a resource is on `/v2/` just
  because another one is.
- **Testing a full click-through locally:** Fintoc rejects non-HTTPS `success_url`/`cancel_url` —
  a plain string-scheme check, not a reachability check, so `https://localhost:3000/...` is
  accepted even with nothing publicly listening there (only your own browser needs to load it,
  after the redirect back). Run `dc-ui` with `npm run dev:https` (self-signed cert via Next's
  built-in `--experimental-https`, one-time "proceed anyway" click in the browser) and set
  `FRONTEND_URL=https://localhost:3000` when starting `dc-api-v2` — the "Main (Fintoc HTTPS
  callback)" launch config in `.vscode/launch.json` does this. No tunnel or third-party account
  needed. Without this, `POST /payments` still works and fails gracefully into a normal error
  response (dc-ui's `PaymentModal` shows it in-modal) — this is only needed to see the actual
  Fintoc-hosted redirect complete.
- A sitter without a bank account on file (`PUT /sitters/me/bank-account`) has their payout held as
  `PENDING_BANK_ACCOUNT` until they add one; `AppScheduler` sweeps stuck `PENDING` payouts and polls
  `PROCESSING` ones against Fintoc directly (transfer webhook delivery isn't confirmed against live
  traffic yet, so this poll is the reliable path for now).

### Email (verification + password reset)

Registration and "forgot password" both send an email, via whichever `EmailSender` wins this
priority order (checked fresh on every send — no restart needed to pick up a change):

1. **Admin-configured SMTP** (`smtp_settings` table) — set from the admin dashboard's **Email
   Settings** tab (`/admin`, ADMIN role required). Pre-filled for
   [Maileroo](https://maileroo.com) (`smtp.maileroo.com`, port `587`, STARTTLS) but works with any
   SMTP provider. Only used when its "Enabled" checkbox is on; the saved password is never echoed
   back by the API, only whether one is set. Includes a "send test email" action that sends
   through whatever's currently saved, so you can verify credentials without digging through logs.
2. **`SMTP_*` env vars** (below) — the fallback when nothing's configured in the admin dashboard,
   or it's disabled.
3. **`LoggingEmailSender`** — logs the email body instead of sending it. The final fallback when
   neither of the above is set up; local dev and a fresh checkout work out of the box.

Set `SMTP_HOST`/`SMTP_USERNAME`/`SMTP_PASSWORD` to any provider's SMTP relay (Gmail, SES, Mailgun,
...) to send real email; there's no vendor SDK to swap, just Jakarta Mail talking plain SMTP.

Registering returns a usable session token immediately (unchanged from before), but a *subsequent*
login is rejected until the account's email is verified — see `AuthService.authenticate`. This is
enforced at token-issuance time only: an already-issued JWT stays valid for its normal lifetime
regardless of later verification-status changes, the same way `enabled`/disabled accounts already
worked (there's no per-request DB check, by design — that's the tradeoff of staying stateless).

### Health check

`GET /health` (unauthenticated) checks DB connectivity and returns `{"status":"UP"}` / 200 or
`{"status":"DOWN"}` / 503 — point a load balancer or orchestrator at it.

## Testing

```bash
mvn test
```

`*Test.java` are pure unit tests. `*IT.java` are integration tests that run against a real local
Postgres (same `DB_URL`/credentials as the running app, see `org.doscolas.testsupport.TestDb`) —
consistent with how this project is tested everywhere else (no DB mocking). Both run under the
same `mvn test` command.

## Scope

Ported: auth (register/login/email verification/password reset), users, pets, pet-sitter
assignments, take-care bookings, and the admin dashboard endpoints. The nightly take-care sweep
runs on a hand-rolled `java.util.concurrent.ScheduledExecutorService`
(`org.doscolas.scheduler.AppScheduler`) instead of Spring's `@Scheduled`.

Both Transbank and Fintoc were removed the week of 2026-08-05, then a Stripe Connect marketplace
integration was investigated as their replacement — but Stripe's country support for Chile (CLP,
RUT bank accounts) turned out unconfirmed/discouraged for Connect specifically, so that plan was
dropped in favor of restoring Fintoc (see "Payments (Fintoc)" above). Transbank stays removed: it
can technically split a marketplace payment across commerce codes, but each sitter would need to
complete Transbank's own non-self-serve merchant-affiliation process, which doesn't fit individual
sitters signing up on their own.

Not ported (out of scope for this pass): API docs/OpenAPI/Swagger UI — logic-first port, docs can
follow later the same way `condominios/api-v2` added its own hand-written OpenAPI spec.

## Known gaps (from a 2026-08 production-readiness pass)

Closed in this pass: the take-care double-assignment race condition, missing rate limiting on auth
endpoints, no schema migration tooling, no health check, no automated tests, no password
reset/email verification.

**Closed 2026-08-05**: a real, verified authorization gap — `PetController` (update/delete/status),
`TakeCareController` (create + reads), and `PaymentController` (all reads + status update) checked
only "is logged in", not "do you own this." Any authenticated user could read/mutate any other
user's pets, take-care listings, and payment records (including marking arbitrary payments as
COMPLETED with zero role check). Verified exploitable live before fixing, verified closed after —
see `AuthorizationIT`. Present identically in the original Spring `dc-api`, not a v2-only
regression. `GET /pets` (unfiltered, platform-wide) is now ADMIN-only; it was previously reachable
by any authenticated user and was the data source for the also-newly-noticed orphaned `/pets`
legacy page in `dc-ui` (superseded by `/home`, never linked from the app, still worth deleting).

**Closed 2026-08-05**: Fintoc restored (`V6__restore_fintoc.sql`) after Transbank removal and an
abandoned Stripe Connect plan left the app with zero payment providers for part of the day. Rebuilt
against Fintoc's *current* API rather than a verbatim restore — the integration shape changed since
this was first built: payment collection is now a Checkout Session (hosted Fintoc-redirect page,
`fintoc_checkout_session_id`) instead of a Payment Intent + embedded JS widget, and Transfers
(payouts) now require RS256 JWS-signed requests (`JwsSigner`), not just a bearer token. Also fixed
in the same pass: a stale `payments_status_check` CHECK constraint left over from `dc-api`'s
original (Hibernate-managed) schema — it only allowed
`PENDING/APPROVED/REJECTED/CANCELLED/REFUNDED/IN_PROCESS`, none of which match dc-api-v2's actual
`PaymentStatus` enum (`PENDING/COMPLETED/FAILED`), so every payment would have crashed the moment
it tried to reach `COMPLETED` or `FAILED`. Undetected until now because no payment provider had
successfully completed a payment against this database before.

**Closed 2026-08-05**: Transbank removed — `TransbankService`, `SitterCommerceAccountService`/
`Controller`/`Repository`/model, the `webpay-return` callback, and the Transbank-specific `payments`
columns (`provider`, `transbank_token`, `buy_order`) are gone for good; not part of the Fintoc
restoration above. The admin dashboard's Commerce Accounts tab is gone too.

Still open: the seeded default admin account (`admin@doscolas.cl` / `admin123` in
`V1__init.sql`) must be rotated before any real deployment; `JWT_SECRET`, `DB_PASSWORD`, and every
`FINTOC_*` var have source-committed placeholder defaults in `AppConfig` that must be overridden via
real env vars before payments/payouts can actually move money; `FINTOC_JWS_PRIVATE_KEY` specifically
needs a generated key pair with the public half registered on Fintoc's dashboard (see "Payments
(Fintoc)" above) — Checkout Sessions work without it, Transfers don't; the exact Fintoc webhook
event envelope (`{type, data: {object}}` vs. a flattened object) isn't confirmed against live
traffic, `FintocWebhookController` tries both shapes defensively; there's no Dockerfile or CI
pipeline for this module yet; no error tracking/APM; the rate limiter is in-memory per-instance
(fine for one JVM, not for horizontal scaling without a shared store); `dc-api-v2` has no git
commits yet — everything in this file's history is uncommitted working-directory state.
