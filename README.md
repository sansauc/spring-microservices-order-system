# Spring Microservices Order System

A learning project that simulates an ordering system with Spring Boot microservices. It covers service discovery, centralized configuration, an API gateway, resilient inter-service calls, and an **orchestrated saga with stock compensation** to keep data consistent across services.

> All data is stored **in memory** (no database). Restarting a service resets its data. This is intentional: the focus is on distributed-systems patterns, not persistence.

## Architecture

```
                         ┌────────────────┐
        client ────────▶ │  api-gateway   │ :8060
                         └───────┬────────┘
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
      ┌───────────────┐  ┌───────────────┐  ┌───────────────┐
      │ service-order │─▶│service-product│  │service-customer│
      │     :8082     │  │     :8081     │  │     :8083     │
      └───────┬───────┘  └───────────────┘  └───────────────┘
              └──────────────────────────────────▲
                       (Feign + load balancing)

   service-registry (Eureka) :8761      service-config (Config Server) :8088
```

| Module | Port | Role |
|---|---|---|
| `service-registry` | 8761 | Eureka server (service discovery) |
| `service-config` | 8088 | Spring Cloud Config Server (native profile, files in `services-configs/`) |
| `api-gateway` | 8060 | Single entry point, routes `/api/v1/**` to each service via `lb://` |
| `service-product` | 8081 | Products and stock (idempotent decrease/restore) |
| `service-customer` | 8083 | Customers |
| `service-order` | 8082 | Orders; orchestrates the stock saga |

## Tech stack

- Java 21, Spring Boot 4.1, Spring Cloud 2025.1
- Spring Cloud Netflix Eureka, Config Server, Gateway
- OpenFeign + Spring Cloud LoadBalancer
- Resilience4j (Retry, Circuit Breaker, Bulkhead, Rate Limiter)
- JUnit 5, Mockito, MockMvc, WireMock
- Maven (wrapper included), Lombok

## Key concepts demonstrated

### Resilience on inter-service calls
`service-order` calls `service-product` and `service-customer` through Feign with:
- **Timeouts** (connect/read) configured per service.
- **Retry** with different policies for reads and writes; 4xx business errors (e.g. `409` insufficient stock) are never retried.
- **Circuit breaker** that ignores 4xx, so business errors do not open the circuit.
- **Bulkhead** and **rate limiter** on order creation.
- **Fallbacks** that translate infrastructure failures into a `503 Service Unavailable`, while preserving the original exception type for business errors.

### Idempotent stock operations
`service-product` exposes:

| Method | Path | Description |
|---|---|---|
| `PUT` | `/api/v1/products/{id}/stock?quantity=N` | Decrease stock |
| `PUT` | `/api/v1/products/{id}/stock/restore?quantity=N` | Restore stock (saga compensation) |

Both require an `Idempotency-Key` header. The key is deterministic (`order-{orderId}-product-{productId}`), so retrying the same request never applies the change twice. A restore with a key whose decrease never arrived leaves a "tombstone", so a late decrease with that key is ignored.

### Orchestrated saga for order creation
`POST /api/v1/orders` in `service-order`:

1. Validates the customer and every product/stock.
2. Saves the order as `PENDING`.
3. Decreases stock for each item (with idempotency keys).
4. If any decrease fails, **compensates in reverse order** (restoring stock for every attempted item, including the one that failed, since a timeout leaves its outcome unknown) and marks the order `CANCELLED`.
5. If a compensation itself fails, the order is marked `COMPENSATION_FAILED` so it can be reconciled manually.
6. On success, the order is marked `CONFIRMED`.

```
PENDING ──all decreases OK──────────────▶ CONFIRMED
   │
   └─decrease fails─▶ compensate ─ok──▶ CANCELLED
                          └─fails───▶ COMPENSATION_FAILED
```

The original exception is rethrown, so the global exception handler still returns the right HTTP status (e.g. `409` for insufficient stock).

## Running the project

Requirements: **JDK 21** (the Maven wrapper is included, no Maven install needed).

Start each module in its own terminal, **in this order**, waiting for each one to finish booting:

```bash
cd service-registry && ./mvnw spring-boot:run   # 1. Eureka      -> http://localhost:8761
cd service-config   && ./mvnw spring-boot:run   # 2. Config Server
cd service-product  && ./mvnw spring-boot:run   # 3. Products
cd service-customer && ./mvnw spring-boot:run   #    Customers
cd service-order    && ./mvnw spring-boot:run   #    Orders
cd api-gateway      && ./mvnw spring-boot:run   # 4. Gateway
```

On Windows use `mvnw.cmd` (or `./mvnw` in Git Bash). If you change a file in `service-config`, restart the Config Server before restarting the affected service. Check that the services appear in Eureka before sending requests; the gateway may return `503` for a few seconds until it discovers them.

## API

All requests go through the gateway at `http://localhost:8060` (send `Content-Type: application/json`).

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/products` | Create a product → `201` |
| `GET` | `/api/v1/products`, `/api/v1/products/{id}` | List / get products |
| `POST` | `/api/v1/customers` | Create a customer → `201` |
| `GET` | `/api/v1/customers`, `/api/v1/customers/{id}` | List / get customers |
| `POST` | `/api/v1/orders` | Create an order (runs the saga) → `201` |
| `GET` | `/api/v1/orders`, `/api/v1/orders/{id}` | List / get orders |
| `GET` | `/api/v1/orders/customer/{customerId}` | Orders of a customer |

### Quick walkthrough

Create a product and a customer:

```json
POST /api/v1/products
{ "id": 1, "name": "Laptop", "price": 1500.00, "stock": 10 }
```
```json
POST /api/v1/customers
{ "id": 1, "name": "John Doe", "email": "john.doe@mail.com" }
```

Create an order:

```json
POST /api/v1/orders
{ "id": 1, "customerId": 1, "items": [ { "productId": 1, "quantity": 2 } ] }
```

Then `GET /api/v1/products/1` shows the stock dropped to `8`. An order requesting more stock than available returns `409` and leaves the stock untouched (the saga compensates any partial decrease).

### Trying the saga under concurrency

With a product at stock `10` and another at stock `2`, send several simultaneous orders that each ask for 1 unit of the first and 2 units of the second. Exactly one order ends `CONFIRMED`, the others `CANCELLED` with `409`, and the first product's stock ends at `9`: every partial decrease was compensated.

## Error responses

Errors follow a common `ApiError` shape:

```json
{
  "timestamp": "2026-01-01T12:00:00",
  "status": 409,
  "error": "Insufficient stock to complete the requested operation",
  "message": "Insufficient quantity for the product with ID: 1",
  "path": "/api/v1/orders",
  "errors": null
}
```

| Status | When |
|---|---|
| `400` | Validation failed (`errors` contains the messages per field) |
| `404` | Customer, product or order not found |
| `409` | Insufficient stock |
| `429` | Too many order-creation requests (rate limiter) |
| `503` | A downstream service is unavailable (retries exhausted or circuit open) |

## Tests

```bash
cd service-order    && ./mvnw test
cd service-product  && ./mvnw test
cd service-customer && ./mvnw test
```

- **Service layer:** Mockito unit tests, including every saga path (success, failure on first/second item, failed compensation).
- **Controllers:** `MockMvc` slice tests covering status codes, validation errors and the `ApiError` shape.
- **Feign clients:** WireMock tests for success, 404, 409, 500, timeouts and connection resets.

## Possible next steps

- Replace the in-memory repositories with a database (JPA) and use unique constraints for idempotency keys.
- A scheduled job to recover stale `PENDING` and `COMPENSATION_FAILED` orders.
- Authentication at the gateway and restricting the internal stock endpoints to internal traffic.
- Docker Compose and a CI pipeline.

## License

Released under the [MIT License](LICENSE).
