# Authentication and access tokens

> **Status: proposal for team review.** Owner: Identity & Merchant. Needs agreement from the API gateway owner and every service owner, because all of them validate these tokens.

## Summary

- **identity-merchant-service** is the only service that logs people in and issues tokens.
- It signs short-lived **access tokens** (JWTs, RS256) with a private key and publishes the public key at `/.well-known/jwks.json`.
- The **gateway and every service** verify tokens with that public key. Nobody calls identity per request, so a slow or restarting identity service doesn't break requests that already have a token.
- Services authorize with **roles in the token** (`hasRole('MERCHANT')`) plus their own ownership checks (for example "this parcel belongs to `merchant_id`").

## Access token

Example payload:

```json
{
  "iss": "swiftdrop-identity",
  "aud": "swiftdrop-api",
  "sub": "8f3c2a10-4b7d-4e9a-a1c2-5d6e7f809a1b",
  "roles": ["CUSTOMER", "MERCHANT"],
  "merchant_id": "c71a4e2b-9d3f-4b8a-b6e1-2f5c8d0a7e64",
  "auth": "password",
  "iat": 1761980000,
  "exp": 1761980900,
  "jti": "4f1d…"
}
```

| Claim | Meaning |
|---|---|
| `iss` | Always `swiftdrop-identity`. Services reject any other issuer. |
| `aud` | Always `swiftdrop-api`. |
| `sub` | User ID (UUID). For couriers this is also the courier ID. |
| `roles` | Roles that are **active right now**: `CUSTOMER`, `MERCHANT`, `COURIER`, `DISPATCHER`. A merchant or courier whose application is pending, rejected or suspended does **not** get that role. |
| `merchant_id` | Present only when `roles` contains `MERCHANT`. |
| `auth` | How the token was obtained: `password`, `google` or `api_key`. Services can refuse sensitive actions for `api_key` tokens. |
| `exp` | 15 minutes after `iat`. |
| `jti` | Unique token ID, for logs. |

Header: `alg: RS256` and `kid` (the key ID, used during key rotation).

**Rules**
- No personal data in tokens: no email, phone, name or address. Tokens end up in logs and browser tools.
- A role change (approval, suspension) takes effect at the user's next refresh, at most 15 minutes later. That delay is accepted; API keys of a suspended merchant stop working immediately because identity checks the merchant on every exchange.
- Every user starts with `CUSTOMER`. Approval adds `MERCHANT` or `COURIER`. `DISPATCHER` accounts can only be created by another dispatcher (or the startup bootstrap).
- A new dispatcher's tokens carry **no roles** until they replace the initial password, so a password someone else chose can't be used to act as a dispatcher.
- With a single audience, `aud` is sent as a string (`"aud": "swiftdrop-api"`), which the JWT standard allows. Spring accepts both the string and the array form.

## How to verify tokens in your service

Add to your service's `application.yaml`. No Java code is needed for the basics (Spring Boot maps the `roles` claim to `ROLE_*` authorities):

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${IDENTITY_JWKS_URI:http://localhost:8081/.well-known/jwks.json}
          issuer-uri: swiftdrop-identity   # validated against "iss"; with jwk-set-uri set, nothing is fetched from it
          audiences: swiftdrop-api
          authorities-claim-name: roles
          authority-prefix: ROLE_
```

- Running in Docker Compose, set `IDENTITY_JWKS_URI=http://identity-merchant-service:8081/.well-known/jwks.json`.
- Then use `@PreAuthorize("hasRole('MERCHANT')")` or `requestMatchers(...).hasRole("DISPATCHER")`.
- Read IDs from the token: `jwt.getSubject()` for the user ID, `jwt.getClaimAsString("merchant_id")` for the merchant.
- Always check ownership in your own data too. The role says *what kind* of user they are; only your service knows whether *this* record is theirs.
- `/actuator/health` stays public for Compose health checks; other Actuator endpoints stay restricted.

The **gateway** uses the same properties (they work for WebFlux too). It rejects requests without a valid token on non-public routes and forwards the `Authorization` header unchanged. Services validate the token again; the gateway is not the only line of defence.

## Login flows

### Email and password
1. `POST /api/v1/auth/login` with email and password.
2. Identity checks the BCrypt hash, returns `{ accessToken, expiresIn, user }` and sets the refresh cookie.

### Google
1. The Angular app navigates the browser to `/oauth2/authorization/google`.
2. Google sends the user back to `/login/oauth2/code/google`. Identity finds the user by Google ID; failing that, links to an existing account with the same **verified** email; failing that, creates a new `CUSTOMER`.
3. Identity sets the refresh cookie and redirects to `{FRONTEND_BASE_URL}/auth/callback`.
4. The Angular app calls `POST /api/v1/auth/refresh` to get its first access token. No token ever appears in a URL.

On failure, step 3 redirects to `{FRONTEND_BASE_URL}/auth/callback?error=<reason>` instead (`email_not_verified`, `account_suspended`, `account_mismatch`, `google_login_failed`), and the Angular page shows a message. Rules:

- Only emails Google marks as **verified** are used; otherwise anyone could claim an address and take over the account registered with it.
- An account is linked to at most one Google account. A different Google account with the same email is refused (`account_mismatch`).
- Linking keeps the existing name and password; the user can then log in either way. A Google-only user can set a password later (`POST /api/v1/users/me/password` without `currentPassword`).
- The redirect-and-callback step uses a short-lived session on identity, ended when login finishes. With several identity instances this needs sticky sessions (or a cookie-based store later).

### Refresh and logout (Angular)
- Keep the access token **in memory only** (a service/signal), never in `localStorage`.
- The refresh token is an opaque random value in an `HttpOnly; Secure; SameSite=Strict` cookie named `sd_refresh`, sent only to `/api/v1/auth/*`. JavaScript cannot read it.
- On a `401`, or shortly before `expiresIn`, call `POST /api/v1/auth/refresh`. Each refresh returns a new cookie and invalidates the old one. If an old refresh token is ever reused, identity revokes the whole chain for that login (likely theft).
- Refresh tokens last 14 days. Identity stores only their SHA-256 hash.
- `POST /api/v1/auth/logout` revokes the refresh token and clears the cookie. Changing the password revokes all of a user's refresh tokens.

### Merchant API keys (server to server)
1. An approved merchant creates a key in the dashboard. The full key (`sd_live_…`) is shown once; identity stores its prefix and SHA-256 hash.
2. The merchant's system calls `POST /api/v1/auth/token` with header `X-API-Key: sd_live_…`.
3. Identity returns a normal access token (`roles: ["MERCHANT"]`, `merchant_id`, `auth: "api_key"`, 15 minutes). No refresh token; the system exchanges the key again when needed.
4. The system calls other services with `Authorization: Bearer <token>`, exactly like the web app.

So the gateway and other services only ever see JWTs; API keys are handled entirely inside identity.

Inside identity, an API-key token is accepted only by `GET /api/v1/merchants/me`. It cannot manage API keys, change the password or edit the owner's account, so a leaked key can't be turned into more access. Keys stop working as soon as the merchant is suspended, because identity checks the merchant's status on every exchange.

## Signing keys

| Environment | Where the private key comes from |
|---|---|
| Local dev | `IDENTITY_JWT_PRIVATE_KEY_PATH` (PEM file, outside the repo). If unset, identity generates a temporary key at startup and logs a warning; tokens then stop working after each restart. |
| CI | Temporary key (as above). |
| Shared/demo deployment | PEM file mounted as a secret. Never committed. |

**Rotation:** add the new key; JWKS serves both; identity signs with the new one; remove the old key after 15 minutes (when the last token signed with it has expired). Services cache JWKS and refetch when they see an unknown `kid`, so nothing restarts.

## Configuration identity needs

| Variable | Purpose |
|---|---|
| `IDENTITY_JWT_PRIVATE_KEY_PATH` | RSA private key (PEM) for signing |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth client. Redirect URI to register: `http://localhost:8081/login/oauth2/code/google` (local) |
| `FRONTEND_BASE_URL` | Where to redirect after Google login, for example `http://localhost:4200` |
| `DISPATCHER_BOOTSTRAP_EMAIL`, `DISPATCHER_BOOTSTRAP_PASSWORD` | Creates the first dispatcher at startup if none exists. That account must change its password at first login. |

## Security rules

- Passwords: BCrypt, 8–64 characters, at least one letter and one digit.
- Login returns the same error for unknown email and wrong password (`INVALID_CREDENTIALS`).
- Rate limits (per identity instance, configurable under `swiftdrop.identity.rate-limit`). Over the limit: `429 RATE_LIMITED` with `Retry-After`.

  | What | Default |
  |---|---|
  | Register | 10 per hour per IP |
  | Login attempts | 30 per minute per IP |
  | Wrong passwords for one account, from any IP | 10 per 15 minutes; a successful login clears the count |
  | Refresh | 120 per minute per IP |
  | API-key exchange | 60 per minute per IP |
  | Wrong current password on password change | 5 per 15 minutes per user |

  Behind the gateway, identity sees the gateway's address unless forwarded headers are configured (see the service README), so the per-IP limits need that before going through the gateway.
- Never log passwords, tokens, refresh tokens or API keys. Log the user ID and `jti` instead.
- Error responses never include stack traces or internal details.

## Decisions this document asks the team to agree on

1. Identity issues RS256 JWTs; everyone verifies with JWKS (no per-request call to identity).
2. Claim names and meanings above, especially `roles` (active roles only) and `merchant_id`.
3. 15-minute access tokens; role changes apply within 15 minutes.
4. API keys are exchanged for JWTs at identity; nothing else accepts `X-API-Key`.
5. The Spring configuration block above is what every service uses.
