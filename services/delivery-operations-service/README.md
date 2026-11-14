# Delivery Operations Service

Spring Boot service for SwiftDrop delivery operations. The current module implements Member 5's warehouse, sorting, and parcel-transfer responsibilities. Member 6 should extend this same application for courier and delivery-execution features.

## Technology

- Java 21 and Spring Boot 4.1
- Spring MVC, Validation, and Data JPA
- PostgreSQL with Flyway migrations
- RabbitMQ client dependency for future agreed event contracts
- Actuator health checks

## Development

Create a root `.env` from `.env.example`, set `DELIVERY_DB_PASSWORD`, and start PostgreSQL from the repository root:

```bash
docker compose up -d postgres
```

Run and test the application:

```bash
./mvnw spring-boot:run
./mvnw test
```

Environment variables:

| Name | Default |
| --- | --- |
| `SERVER_PORT` | `8083` |
| `DELIVERY_DB_URL` | `jdbc:postgresql://localhost:5432/swiftdrop` |
| `DELIVERY_DB_USER` | `delivery_user` |
| `DELIVERY_DB_PASSWORD` | Required; loaded from the root `.env` for local runs |

## API groups

- `/api/v1/warehouses` — create, list, view, update, and change warehouse status
- `/api/v1/warehouse-parcels/arrivals` — record parcel arrivals
- `/api/v1/sorting-records` — record parcel sorting decisions
- `/api/v1/transfers` — create, dispatch, receive, and list parcel transfers
- `/api/v1/warehouses/{warehouseId}/dashboard` — warehouse operational summary

The shared OpenAPI contract is at `../../contracts/openapi/delivery-operations-service.yaml`.

Authentication and RabbitMQ publication are intentionally not hard-coded yet. The team must first agree on JWT claims, endpoint roles, exchange names, routing keys, and event payloads.
