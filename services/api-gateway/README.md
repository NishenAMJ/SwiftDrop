# API Gateway

Spring Cloud Gateway on port **8080**. It forwards existing SwiftDrop API paths to
their owning services without rewriting paths or request headers. Services continue
to validate tokens and enforce their own role and ownership rules.

## Payment routing and security

`/api/v1/payments` and `/api/v1/payments/**` are forwarded unchanged to the payment
service on port 8085. The gateway preserves `Authorization` and `Idempotency-Key`;
there are no payment POST retry filters. The only payment route available without a
customer JWT is `POST /api/v1/payments/callbacks/payhere`. It remains protected by
PayHere's callback signature validation in the payment service. The gateway preserves
the callback's `application/x-www-form-urlencoded` content type and body.

JWTs are validated with Identity's JWKS, issuer and audience. The Authorization
header remains on the proxied request so the destination service validates it too.
Public routes are limited to Identity login/registration/refresh/token endpoints,
OAuth login redirects, the JWKS GET, public Tracking GETs, the PayHere callback POST,
and gateway health. Other requests require a valid access token. Role and resource
ownership decisions remain in each service.

## Routes

| Path | Destination |
|---|---|
| Identity auth, user, dispatcher, merchant, courier, OAuth and JWKS paths | Identity, `:8081` |
| `/api/v1/bookings/**`, `/api/v1/customer/profile/**`, `/api/pricing/**`, `/api/zones/**`, merchant order paths | Parcel & Pricing, `:8082` |
| `/api/v1/sorting-records/**`, `/api/v1/transfers/**`, `/api/v1/warehouses/**`, `/api/v1/warehouse-parcels/**` | Delivery Operations, `:8083` |
| `/api/v1/public/tracking/**`, `/api/v1/tracking/**`, `/api/v1/reports/**` | Tracking & Reporting, `:8084` |
| `/api/v1/payments` and `/api/v1/payments/**` | Payment & Settlement, `:8085` |

The API contracts are under `contracts/openapi`. Add routes when an owning service
publishes its API contract; do not invent or shadow service paths.

## Configuration and run

Defaults target services running on localhost. In Docker Compose, set service URIs to
container DNS names where services share the Compose network, and configure
`IDENTITY_JWKS_URI` to an Identity URL reachable from the gateway container.

```powershell
cd D:\Projects\EAD\SwiftDrop\services\api-gateway
mvn spring-boot:run
```

The public gateway URL is `http://localhost:8080`. Only `/actuator/health` is
exposed without authentication; other Actuator endpoints are not exposed.
