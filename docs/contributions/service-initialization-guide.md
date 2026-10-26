# SwiftDrop — Service Initialization Guide

## 1. Shared project rules

- Use one Git repository: **SwiftDrop**.
- One designated member initializes each application. Members sharing a service use the same scaffold.
- **Create a new branch for each service.** Every service is scaffolded on its own branch (`setup/<artifact>`), created from `dev`, and merged into `dev` through a pull request. Never commit a scaffold directly to `dev` or `main`, and never put two services on one branch. See [Section 8](#8-git-commands-for-service-owners).
- Generate each application with Spring Initializr: https://start.spring.io/
- Use the exact Spring Boot version already selected in `services/identity-merchant-service/pom.xml`: **4.1.2**. Do not independently choose different releases, snapshots or release candidates.

Settings for every project:

| Setting | Value |
|---|---|
| Group | `com.swiftdrop` |
| Project | Maven |
| Language | Java |
| Java version | **21** |
| Packaging | Jar |
| Configuration file | `src/main/resources/application.yaml` |
| Spring Boot | **4.1.2** (same exact version as the Identity service's `pom.xml`) |

Other rules:

- Use Spring Cloud dependencies only where required, with a release compatible with that Spring Boot version; Initializr should select it for the gateway.
- Each service has its own `pom.xml`, Maven Wrapper, source files and `Dockerfile` when containerized.
- Commit `.mvn/`, `mvnw`, `mvnw.cmd` and `pom.xml`. Do not commit `target/`, credentials or `.env`.
- No nested `.git` directories. Service-level `.gitignore` files are normal.

## 2. Initialization ownership and exact names

Use these names consistently for **all six services and the API Gateway**. The **Folder**, **Artifact** and **Name** fields must all use the artifact value below. Keep the Spring Initializr Group as `com.swiftdrop`, and set **Package name** explicitly.

| Members | Folder / Artifact / Name | Java package | Main class | Port | Branch |
|---|---|---|---|---:|---|
| **1–2** | `identity-merchant-service` | `com.swiftdrop.identity` | `IdentityMerchantServiceApplication` | 8081 | `fix/identity-service-config` |
| **3–4** | `parcel-pricing-service` | `com.swiftdrop.parcel` | `ParcelPricingServiceApplication` | 8082 | `setup/parcel-pricing-service` |
| **5–6** | `delivery-operations-service` | `com.swiftdrop.delivery` | `DeliveryOperationsServiceApplication` | 8083 | `setup/delivery-operations-service` |
| **7** | `tracking-reporting-service` | `com.swiftdrop.tracking` | `TrackingReportingServiceApplication` | 8084 | `setup/tracking-reporting-service` |
| **8–9** | `payment-settlement-service` | `com.swiftdrop.payment` | `PaymentSettlementServiceApplication` | 8085 | `setup/payment-settlement-service` |
| **10** | `notification-webhook-service` | `com.swiftdrop.communication` | `NotificationWebhookServiceApplication` | 8086 | `setup/notification-webhook-service` |
| **Integration owner** | `api-gateway` | `com.swiftdrop.gateway` | `ApiGatewayApplication` | 8080 | `setup/api-gateway` |

Identity & Merchant has already been generated. Members 1–2 must use the existing service rather than generating it again, and make any corrections on a new branch: `fix/identity-service-config`. For paired services, agree which member creates the initial scaffold.

Use these **Description** values:

| Application | Description |
|---|---|
| Identity & Merchant | Authentication, users and merchant management for SwiftDrop |
| Parcel & Pricing | Parcel bookings, merchant orders and pricing for SwiftDrop |
| Delivery Operations | Courier assignments, delivery execution and issue management for SwiftDrop |
| Tracking & Reporting | Parcel tracking, delivery history and reporting for SwiftDrop |
| Payment & Settlement | Online payments, COD reconciliation and merchant ledger for SwiftDrop |
| Notification & Webhook | Customer notifications and merchant webhook delivery for SwiftDrop |
| API Gateway | API routing and edge security for SwiftDrop |

## 3. Common dependencies for six business services

In Initializr, search for and select these dependency names. The exact displayed labels can vary slightly by Spring Boot version; let Initializr generate the Maven coordinates.

| Dependency | Purpose |
|---|---|
| Spring Web | REST controllers using Spring MVC |
| Validation | Request validation |
| Spring Data JPA | Entities and repositories through Hibernate |
| PostgreSQL Driver | PostgreSQL database connection |
| Flyway Migration | Service-owned schema migrations |
| Spring Security | Authentication and authorization rules |
| OAuth2 Resource Server | JWT access-token verification |
| Spring Boot Actuator | Health and management endpoints |

Do not manually choose separate Hibernate, Security or Flyway versions. Use Spring Boot dependency management. Modern Flyway configurations need PostgreSQL database support in addition to core; inspect the generated `pom.xml` for `flyway-database-postgresql` and retain it when provided. If missing, add the appropriate PostgreSQL support dependency using the version managed by the selected Boot release.

## 4. Complete dependency lists by application

### Members 1–2 — Identity & Merchant

Folder: `services/identity-merchant-service`

- Spring Web
- Validation
- Spring Data JPA
- PostgreSQL Driver
- Flyway Migration
- Spring Security
- OAuth2 Resource Server
- OAuth2 Client
- Spring Boot Actuator
- Spring for RabbitMQ

OAuth2 Client supports Google login. Resource Server verifies access tokens; it does not generate login endpoints or issue tokens automatically. RabbitMQ supports merchant and account events and may be deferred until event integration. Login must work independently of notification delivery.

### Members 3–4 — Parcel & Pricing

Folder: `services/parcel-pricing-service`

- Spring Web
- Validation
- Spring Data JPA
- PostgreSQL Driver
- Flyway Migration
- Spring Security
- OAuth2 Resource Server
- Spring Boot Actuator
- Spring for RabbitMQ

Owns personal bookings, merchant orders, addresses, estimated weight, zones, fee calculation, receipts and parcel events. Pricing and booking are modules of this single service, not separate applications.

### Members 5–6 — Delivery Operations

Folder: `services/delivery-operations-service`

- Spring Web
- Validation
- Spring Data JPA
- PostgreSQL Driver
- Flyway Migration
- Spring Security
- OAuth2 Resource Server
- Spring Boot Actuator
- Spring for RabbitMQ

Owns courier profiles, assignments, attempts, status changes, proofs and issues. Add an object-storage SDK only after the team chooses a proof-storage provider.

### Member 7 — Tracking & Reporting

Folder: `services/tracking-reporting-service`

- Spring Web
- Validation
- Spring Data JPA
- PostgreSQL Driver
- Flyway Migration
- Spring Security
- OAuth2 Resource Server
- Spring Boot Actuator
- Spring for RabbitMQ

Owns tracking/history/report projections. Spring MVC can serve SSE; WebFlux is not required merely for live tracking. Public tracking endpoints need explicit public access and must exclude private data.

### Members 8–9 — Payment & Settlement

Folder: `services/payment-settlement-service`

- Spring Web
- Validation
- Spring Data JPA
- PostgreSQL Driver
- Flyway Migration
- Spring Security
- OAuth2 Resource Server
- Spring Boot Actuator
- Spring for RabbitMQ

Owns online payments, COD, handovers, reconciliation and merchant ledger. Add a payment-provider SDK only if required after choosing the gateway. Provider callbacks use their own authenticity checks rather than customer JWT authentication.

### Member 10 — Notification & Webhook

Folder: `services/notification-webhook-service`

- Spring Web
- Validation
- Spring Data JPA
- PostgreSQL Driver
- Flyway Migration
- Spring Security
- OAuth2 Resource Server
- Spring Boot Actuator
- Spring for RabbitMQ
- Java Mail Sender

Java Mail Sender is needed for SMTP email; an HTTP email provider may instead use a provider-specific integration. Webhooks can use Spring's HTTP client. Tracking, customer notifications and merchant webhooks each consume events from separate queues. Do not add a payment SDK or Google-login dependency here.

### Integration owner — API Gateway

Folder: `services/api-gateway`

- Gateway — Server WebFlux
- Spring Security
- OAuth2 Resource Server
- Spring Boot Actuator

Select the **Server WebFlux** gateway variant, not Proxy Exchange. Its Maven artifact is `org.springframework.cloud:spring-cloud-starter-gateway-server-webflux`. Use Initializr's compatible Spring Cloud BOM.

Do not add Spring Web MVC, JPA, PostgreSQL Driver, Flyway or RabbitMQ to this gateway scaffold. It routes traffic and validates tokens; business rules and ownership checks remain in services. Do not configure a datasource for the gateway.

## 5. Generate and place a service

1. Clone the shared repository and switch to `dev`.
2. **Create a new setup branch for your assigned service** (`setup/<artifact>`, see Section 2). One service per branch.
3. Open Spring Initializr and enter the shared settings and your exact names from Section 2.
4. Add your dependencies from Section 4.
5. Click **Generate** and extract the ZIP **outside** the repository first.
6. Move the generated application folder into `services/`.
7. Check that `pom.xml` is directly inside `services/<artifact>/`; avoid an extra nested directory.
8. The configuration file must be `src/main/resources/application.yaml` in YAML syntax. If Initializr generated `application.properties`, convert its contents and rename it to `application.yaml`. Do not just rename nonempty properties syntax.
9. Keep the generated main class (see Section 2) and the test class in the matching package.
10. Review and commit the scaffold through a pull request into `dev`.

Expected layout:

```
SwiftDrop/
└── services/
    └── parcel-pricing-service/
        ├── .mvn/
        ├── src/
        │   ├── main/
        │   │   ├── java/com/swiftdrop/parcel/ParcelPricingServiceApplication.java
        │   │   └── resources/application.yaml
        │   └── test/
        ├── .gitignore
        ├── mvnw
        ├── mvnw.cmd
        └── pom.xml
```

## 6. Initial YAML configuration

Put these initial application-name and port settings in `src/main/resources/application.yaml`. Add database, security and messaging configuration afterward. If the file already contains other settings, merge these under the existing `spring` and `server` sections rather than duplicating those keys.

**Identity & Merchant**

```yaml
spring:
  application:
    name: identity-merchant-service

server:
  port: 8081
```

**Parcel & Pricing**

```yaml
spring:
  application:
    name: parcel-pricing-service

server:
  port: 8082
```

**Delivery Operations**

```yaml
spring:
  application:
    name: delivery-operations-service

server:
  port: 8083
```

**Tracking & Reporting**

```yaml
spring:
  application:
    name: tracking-reporting-service

server:
  port: 8084
```

**Payment & Settlement**

```yaml
spring:
  application:
    name: payment-settlement-service

server:
  port: 8085
```

**Notification & Webhook**

```yaml
spring:
  application:
    name: notification-webhook-service

server:
  port: 8086
```

**API Gateway**

```yaml
spring:
  application:
    name: api-gateway

server:
  port: 8080
```

Use spaces, not tabs. This is scaffold configuration only. Business services with JPA/Flyway require database settings before normal startup. Rabbit listeners, JWT decoders and external clients may also require configuration before startup. Do not globally disable security or database integration just to make a scaffold appear complete.

## 7. Database ownership

Use one PostgreSQL instance with one `swiftdrop` database and separate schemas/users. The infrastructure owner provisions schemas and credentials; each service owner maintains their own Flyway migrations.

| Service | Schema | Database user | Password environment variable |
|---|---|---|---|
| Identity & Merchant | `identity_merchant` | `identity_user` | `IDENTITY_DB_PASSWORD` |
| Parcel & Pricing | `parcel_pricing` | `parcel_user` | `PARCEL_DB_PASSWORD` |
| Delivery Operations | `delivery_operations` | `delivery_user` | `DELIVERY_DB_PASSWORD` |
| Tracking & Reporting | `tracking_reporting` | `tracking_user` | `TRACKING_DB_PASSWORD` |
| Payment & Settlement | `payment_settlement` | `payment_user` | `PAYMENT_DB_PASSWORD` |
| Notification & Webhook | `notification_webhook` | `communication_user` | `COMMUNICATION_DB_PASSWORD` |

- Service users must be restricted to their assigned schemas; no administrator credentials in application connections.
- Set the datasource schema/search path, Hibernate default schema and Flyway schema consistently.
- Use Hibernate `ddl-auto: validate` and Flyway migrations under `src/main/resources/db/migration/`.
- Each schema keeps its own Flyway history table.
- Do not create cross-schema queries, foreign keys or entity relationships.
- Use IDs, APIs and events for cross-service references.
- From a service running on the laptop, PostgreSQL is `localhost` with the published port. From Compose, use the PostgreSQL service name and container port.
- Root `.env` is read by Compose for substitution; Spring Boot launched directly in PowerShell does not automatically load it.

## 8. Git commands for service owners

**Each service gets its own branch.** Always start from an up-to-date `dev`, and create a separate branch for every service you initialize:

| Service | Branch |
|---|---|
| Identity & Merchant | `fix/identity-service-config` (already generated; corrections only) |
| Parcel & Pricing | `setup/parcel-pricing-service` |
| Delivery Operations | `setup/delivery-operations-service` |
| Tracking & Reporting | `setup/tracking-reporting-service` |
| Payment & Settlement | `setup/payment-settlement-service` |
| Notification & Webhook | `setup/notification-webhook-service` |
| API Gateway | `setup/api-gateway` |

Example for Parcel & Pricing, from the repository root:

```powershell
git switch dev
git pull origin dev
git switch -c setup/parcel-pricing-service
```

After placing and configuring the generated service:

```powershell
cd services/parcel-pricing-service
.\mvnw.cmd --version
.\mvnw.cmd -DskipTests compile
cd ../..
git add services/parcel-pricing-service
git diff --cached --stat
git status
git commit -m "Initialize parcel and pricing service"
git push -u origin setup/parcel-pricing-service
```

Confirm Java 21 in wrapper output. Compilation checks source/dependency setup, not runtime readiness. Run the generated tests and application after infrastructure and runtime configuration are supplied; skipping tests for the initial compile does not complete testing obligations.

Create a GitHub pull request from `setup/parcel-pricing-service` into `dev`. Review the names, dependencies and absence of secrets before merge. The second member sharing this service starts a feature branch from updated `dev` after the scaffold merges.

## 9. Verification after runtime configuration

From your service directory:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Confirm: configured port is used; datasource reaches only the assigned schema; migrations succeed; security is configured; required broker connections work. Each member owns tests for their functionality, plus API documentation, migrations and demonstration evidence.

Do not treat an unconfigured service's failed datasource or JWT startup as proof that project generation failed. Record the missing configuration and complete it before claiming successful startup.

## 10. Shared-file ownership

- One frontend owner generates the Angular application once in `frontend/`; other members contribute feature folders.
- One integration owner coordinates `compose.yaml`, gateway routes and service startup documentation.
- Each service owner supplies their container settings and required variable names to that owner.
- Agree on API/event contracts in `contracts/` before dependent feature development.
- Use health endpoints to support Compose checks; keep sensitive Actuator endpoints restricted.
- Do not introduce Eureka, a Config Server or distributed tracing infrastructure at initialization unless the team explicitly adds them to scope.

## 11. Scaffold pull-request checklist

- [ ] Scaffold is on its own `setup/<artifact>` branch, created from `dev`.
- [ ] Correct folder, artifact, package, main class, application name and port.
- [ ] Configuration is in `src/main/resources/application.yaml` (no `application.properties`).
- [ ] Java 21 and the shared Spring Boot version (4.1.2).
- [ ] Correct dependencies for this application.
- [ ] YAML syntax is valid; no duplicate properties configuration.
- [ ] Maven Wrapper committed and compilation result recorded.
- [ ] No nested `.git` directory, `target/` files or real credentials committed.
- [ ] README states how to build and which runtime configuration is still required.
- [ ] Runtime configuration that is still missing is listed in the pull request description.
