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

## Configuration

| Variable | Required | Default |
|---|---|---|
| `IDENTITY_DB_PASSWORD` | yes | (none) |
| `IDENTITY_DB_URL` | no | `jdbc:postgresql://localhost:5432/swiftdrop` |
| `IDENTITY_DB_USER` | no | `identity_merchant_user` |

## Security today

- `GET /actuator/health` is public and shows only `{"status": ...}`. No other Actuator endpoint is exposed.
- Every other request returns `401`. No sessions, form login or HTTP Basic.

## Not configured yet

These come with the login work and will add required variables (listed in the auth design doc):

- Issuing and verifying JWTs (signing key, JWKS endpoint)
- Google login (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `FRONTEND_BASE_URL`)
- First dispatcher bootstrap (`DISPATCHER_BOOTSTRAP_EMAIL`, `DISPATCHER_BOOTSTRAP_PASSWORD`)
- RabbitMQ (`spring-boot-starter-amqp`): deferred until event publishing, and until `compose.yaml` has a RabbitMQ container. Adding it earlier would make `/actuator/health` report `DOWN` with no broker running.
