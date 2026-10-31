# identity-merchant-service

Authentication, users and merchant management for SwiftDrop. Port **8081**.

| | |
|---|---|
| Java package | `com.swiftdrop.identity` |
| Database | schema `identity_merchant` in `swiftdrop`, login `identity_merchant_user` |
| API contract | [contracts/openapi/identity-merchant-service.yaml](../../contracts/openapi/identity-merchant-service.yaml) |
| Auth design | [docs/architecture/authentication.md](../../docs/architecture/authentication.md) |

## Build and test

Tests start the full application, so the database must be running (see the [root README](../../README.md), steps 3–4).

```bash
export IDENTITY_DB_PASSWORD="the-value-from-your-.env"   # PowerShell: $env:IDENTITY_DB_PASSWORD = "..."
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
  -d '{"email":"nimal@example.com","password":"Correct-horse-42","fullName":"Nimal Perera"}'

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
| `GET /actuator/health` | public, status only |

Everything else returns `401` without a token. Errors use Problem Details with a `code` (see [contracts/README.md](../../contracts/README.md)).

## Configuration

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `IDENTITY_DB_PASSWORD` | yes | (none) | Database password, same as in `.env` |
| `IDENTITY_DB_URL` | no | `jdbc:postgresql://localhost:5432/swiftdrop` | |
| `IDENTITY_DB_USER` | no | `identity_merchant_user` | |
| `IDENTITY_JWT_PRIVATE_KEY_PATH` | no locally, yes when shared | (temporary key) | RSA private key (PKCS#8 PEM) that signs access tokens. Without it, a new key is generated at every start and older tokens stop working. |
| `DISPATCHER_BOOTSTRAP_EMAIL`, `DISPATCHER_BOOTSTRAP_PASSWORD` | no | (none) | Creates the first dispatcher at startup if no dispatcher exists yet. It must change its password at first login. |
| `DISPATCHER_BOOTSTRAP_NAME` | no | `SwiftDrop Dispatcher` | |
| `IDENTITY_COOKIE_SECURE` | no | `true` | Keep `true`; browsers accept Secure cookies on `http://localhost`. |

Make a signing key (keep it outside the repository):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ~/swiftdrop-jwt-key.pem
export IDENTITY_JWT_PRIVATE_KEY_PATH=~/swiftdrop-jwt-key.pem
```

## How it works

- **Access tokens:** RS256 JWTs, 15 minutes, with `roles` (active roles only), `auth` and the user ID as `sub`. No personal data. Other services verify them with the JWKS endpoint ([auth design](../../docs/architecture/authentication.md)).
- **Refresh tokens:** random values, stored as SHA-256 hashes, 14 days, replaced on every refresh. Reusing an old one revokes that whole login, except within 10 seconds of a rotation (two browser tabs refreshing at once).
- **Passwords:** BCrypt; 8–64 characters with a letter and a digit. Unknown email and wrong password give the same error.
- **New dispatchers** get a token with no roles until they change the initial password.
- **Events** (`user.registered`, `user.updated`) are written to the `outbox_events` table in the same transaction as the change. They are not sent anywhere yet (see below).

## Not done yet

- Google login (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `FRONTEND_BASE_URL`).
- Publishing outbox events to RabbitMQ. Waits for a RabbitMQ container in `compose.yaml`; adding the dependency earlier would make `/actuator/health` report `DOWN`.
- Merchant and courier applications, approvals and API keys.
- Rate limiting for login and refresh.
