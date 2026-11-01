# identity-merchant-service

Authentication, users and merchant management for SwiftDrop. Port **8081**.

| | |
|---|---|
| Java package | `com.swiftdrop.identity` |
| Database | schema `identity_merchant` in `swiftdrop`, login `identity_merchant_user` |
| API contract | [contracts/openapi/identity-merchant-service.yaml](../../contracts/openapi/identity-merchant-service.yaml) |
| Auth design | [docs/architecture/authentication.md](../../docs/architecture/authentication.md) |

## Build and test

Tests use the `test` profile and an isolated database on port 15433, never the development database. Start a disposable database and wait until `pg_isready` reports accepting connections:

```powershell
docker run --name swiftdrop-identity-role-tests --rm -d -p 127.0.0.1:15433:5432 -e POSTGRES_PASSWORD=identity-test-only -e POSTGRES_DB=identity_role_test postgres:17
docker exec swiftdrop-identity-role-tests pg_isready -U postgres -d identity_role_test
```

Run the tests below, then stop the disposable container with `docker stop swiftdrop-identity-role-tests`. Alternative isolated databases use `IDENTITY_TEST_DB_URL`, `IDENTITY_TEST_DB_USER`, and `IDENTITY_TEST_DB_PASSWORD`.

```bash
./mvnw verify                                            # Windows: .\mvnw.cmd verify
```

## Run

```bash
./mvnw spring-boot:run
curl http://localhost:8081/actuator/health               # {"status":"UP"}
```

## Try it

```bash
# Register (creates a CUSTOMER and logs in)
curl -i -X POST localhost:8081/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"nimal@example.com","password":"Correct-horse-42","fullName":"Nimal Perera","role":"CUSTOMER"}'

# Use the accessToken from the response
curl localhost:8081/api/v1/users/me -H "Authorization: Bearer <accessToken>"
```

The response also sets the `sd_refresh` cookie (HttpOnly, sent only to `/api/v1/auth`). Browsers handle it automatically; with curl, pass it back with `-b 'sd_refresh=...'` to `POST /api/v1/auth/refresh`.

## Endpoints implemented

| Endpoint | Who |
|---|---|
| `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout` | public |
| `GET /.well-known/jwks.json` | public |
| `GET`, `PATCH /api/v1/users/me`; `POST /api/v1/users/me/password` | any signed-in user |
| `GET /api/v1/users` (filters: `role`, `status`, `q`; paging) | dispatcher |
| `POST /api/v1/dispatchers` | dispatcher |
| `POST /api/v1/merchants`, `POST /api/v1/couriers` (apply or resubmit) | matching assigned merchant/courier role |
| `GET /api/v1/merchants/me`, `GET /api/v1/couriers/me` | the applicant (merchants/me also with an API-key token) |
| `GET /api/v1/merchants`, `/merchants/{id}`; `GET /api/v1/couriers`, `/couriers/{userId}` | dispatcher |
| `POST /api/v1/merchants/{id}/approve`, `/reject`, `/suspend`, `/reactivate` (same for couriers) | dispatcher |
| `GET`, `POST /api/v1/merchants/me/api-keys`; `DELETE /api/v1/merchants/me/api-keys/{keyId}` | approved merchant (person token) |
| `POST /api/v1/auth/token` (header `X-API-Key`) | public |
| `GET /actuator/health` | public, status only |

Everything else returns `401` without a token. Errors use Problem Details with a `code` (see [contracts/README.md](../../contracts/README.md)).

## Configuration

Local runs load `SwiftDrop/.env` automatically when the working directory is this service directory or the SwiftDrop root. Use plain, unquoted `KEY=value` entries (Java properties format); do not use `export` prefixes or inline comments. Environment variables override file values. For runs from other directories, supply environment variables instead.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `IDENTITY_DB_PASSWORD` | yes | (none) | Database password, same as in `.env` |
| `IDENTITY_DB_URL` | no | `jdbc:postgresql://localhost:5432/swiftdrop` | |
| `IDENTITY_DB_USER` | no | `identity_merchant_user` | |
| `IDENTITY_JWT_PRIVATE_KEY_PATH` | no locally, yes when shared | (temporary key) | RSA private key (PKCS#8 PEM) that signs access tokens. Without it, a new key is generated at every start and older tokens stop working. |
| `DISPATCHER_BOOTSTRAP_EMAIL`, `DISPATCHER_BOOTSTRAP_PASSWORD` | no | (none) | Creates the first dispatcher at startup if no dispatcher exists yet. It must change its password at first login. |
| `DISPATCHER_BOOTSTRAP_NAME` | no | `SwiftDrop Dispatcher` | |
| `IDENTITY_COOKIE_SECURE` | no | `true` | Keep `true`; browsers accept Secure cookies on `http://localhost`. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | no | (none) | Turns on Google login when both are set. See below. |
| `FRONTEND_BASE_URL` | no | `http://localhost:4200` | After Google login the browser goes to `{FRONTEND_BASE_URL}/auth/callback`. |
| `IDENTITY_RATE_LIMIT_ENABLED` | no | `true` | Turn off only for load tests. The limits themselves are in `application.yaml` under `swiftdrop.identity.rate-limit`. |

Make a signing key (keep it outside the repository):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ~/swiftdrop-jwt-key.pem
export IDENTITY_JWT_PRIVATE_KEY_PATH=~/swiftdrop-jwt-key.pem
```

## Google login

1. In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), create an **OAuth client ID** of type **Web application**.
2. Add the authorized redirect URI `http://localhost:8081/login/oauth2/code/google`.
3. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` (never commit them), then start the service.
4. Open http://localhost:8081/oauth2/authorization/google in a browser. After signing in at Google you land on `http://localhost:4200/auth/callback` with the `sd_refresh` cookie set; the Angular page then calls `POST /api/v1/auth/refresh`.

Errors arrive as `/auth/callback?error=email_not_verified | account_suspended | account_mismatch | google_login_failed`.

## How it works

- **Access tokens:** RS256 JWTs, 15 minutes, with `roles` (active roles only), `auth` and the user ID as `sub`. No personal data. Other services verify them with the JWKS endpoint ([auth design](../../docs/architecture/authentication.md)).
- **Refresh tokens:** random values, stored as SHA-256 hashes, 14 days, replaced on every refresh. Reusing an old one revokes that whole login, except within 10 seconds of a rotation (two browser tabs refreshing at once).
- **Passwords:** BCrypt; 8–64 characters with a letter and a digit. Unknown email and wrong password give the same error.
- **New dispatchers** get a token with no roles until they change the initial password.
- **Merchant and courier applications:** users choose MERCHANT or COURIER at registration and apply for that role; dispatchers approve, reject (with a reason), suspend (with a reason) or reactivate. Approval activates the assigned `MERCHANT`/`COURIER` role and suspension removes it, effective at the user's next token refresh. Invalid moves return `409 INVALID_STATUS_TRANSITION`. Every submission and decision is kept in `application_decisions` (who, when, reason, internal note).
- **Merchant tokens** carry `merchant_id`.
- **API keys:** `sd_live_` + 32 random characters, shown once, stored as a SHA-256 hash, at most 5 active per merchant. `POST /api/v1/auth/token` exchanges a key for a 15-minute `MERCHANT` token (`auth: "api_key"`). Keys stop working immediately when the merchant is suspended or the key is revoked. Inside identity such a token can only read `GET /api/v1/merchants/me`.
- **Rate limits:** per IP on register, login, refresh and API-key exchange, and per account on wrong passwords (login and password change), so guessing one person's password from many addresses is slowed down too. Over the limit: `429 RATE_LIMITED` with `Retry-After`. Defaults are in the [auth design](../../docs/architecture/authentication.md#security-rules). Counts are kept in memory per instance.
- **Google login:** finds the user by Google ID, else links the account with the same verified email, else creates an account awaiting one-time role selection. Ends with the refresh cookie and a redirect; no token in the URL.
- **Events** (`user.*`, `merchant.*`, `courier.*`) are written to the `outbox_events` table in the same transaction as the change. They are not sent anywhere yet (see below).

## Not done yet

- Trying Google login against real Google (needs the credentials above; the flow is covered by tests up to Google's own screen).
- Publishing outbox events to RabbitMQ. Waits for a RabbitMQ container in `compose.yaml`; adding the dependency earlier would make `/actuator/health` report `DOWN`.
- Rate limits shared across several instances (needs a shared store such as Redis; one instance is fine for now).

## Behind the API gateway

Per-IP rate limits use the address the request comes from. Through the gateway that is always the gateway, so all users would share one limit. Before routing traffic through the gateway, the gateway must set `X-Forwarded-For` and identity must trust it:

```yaml
server:
  forward-headers-strategy: framework
```

Only enable this when identity is reachable **only** through the gateway; otherwise clients could fake their address.

## Single-role onboarding

Password registration requires `role`: CUSTOMER, MERCHANT, or COURIER. There is one shared login. Each account keeps exactly one immutable `accountRole`; `roles` contains zero or one active permission role. Merchant/courier accounts have no operational role until approved. New Google accounts have no `accountRole` until `POST /api/v1/users/me/role` selects it; existing Google-linked accounts retain theirs.

User responses include `onboardingState`: ROLE_REQUIRED, APPLICATION_REQUIRED, PENDING, REJECTED, SUSPENDED, or READY. `mustChangePassword` takes routing priority. The Angular app routes to role selection, password change, application/status, or `/customer`, `/merchant`, `/courier`, `/dispatcher`. Operational dashboards are basic shells; approval APIs remain available, but dispatcher approval screens are a later feature.

### Coordinated rollout

Stop traffic to old identity instances before migrating. V7 preserves users, infers assigned roles from legacy permissions/applications, and aborts with user IDs if conflicting assignments exist. Resolve those conflicts explicitly; do not reset production data. The migration revokes previous refresh sessions. Updated identity rejects JWTs without `identity_version: 2`.

Deploy the matching frontend and update gateway/downstream validators to require `identity_version: 2` before reopening traffic. If those validators cannot yet be updated, keep old access tokens out of traffic until their maximum lifetime plus verifier clock skew has elapsed. All users must log in again. Never run old and new identity versions together after this migration.
