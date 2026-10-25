# Cloud Sphere API

Production-style backend for the imaginary **Cloud Sphere Solutions** ecommerce platform (SPEDO501 Year 3 SPE capstone). It implements SRS `CSS-SRS-2026-001`: catalogue, auth, cart, discounts, checkout with a payment stub, wishlist, orders, and admin fulfillment.

| | |
|---|---|
| Runtime | Java 21, Spring Boot 4.1 (WebMVC) |
| Database | PostgreSQL 16, schema **`cloud_sphere`** |
| Migrations | Flyway (`src/main/resources/db/migration`) |
| Cache / tokens | Redis 7 (guest-cart merge helpers, JWT denylist on logout) |
| API docs | OpenAPI 3 + Swagger UI (`springdoc` 3.1). Static contract: `docs/openapi.yaml` |
| Observability | Colourised console logs (JSON via profile `json`/`prod`), `traceId` on requests, Actuator `/actuator/prometheus` |

---

## 1. Architecture (how it is built)

```
                    ┌──────────────────┐
  Storefront / Swagger│  HTTP JSON API   │  /api/v1/*
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              │   Security   │  TraceId     │
              │   JWT filter │  MDC/logs    │
              └──────────────┼──────────────┘
                             │
        Controllers (identity, catalog, cart, order, wishlist, admin)
                             │
        Application services (transactions, pricing, state machine)
                             │
              ┌──────────────┼──────────────┐
              │  JPA / Flyway│  Redis       │  PaymentGateway
              │  schema      │  denylist    │  (stub adapter)
              │  cloud_sphere│              │
              └──────────────┴──────────────┘
                             │
                        PostgreSQL
```

**Package map** (feature-oriented, not a giant `controller/` dump):

| Package | Responsibility | SRS / UAT |
|---|---|---|
| `config` | Security, OpenAPI, properties, seed data | — |
| `common` | RFC 7807 errors, health, `X-Trace-Id` | UC-CS-13 |
| `security` | JWT issue/parse, logout denylist | FR-AUTH-* |
| `identity` | Register, login, lock-out | UC-CS-03/04 |
| `catalog` | Published search & detail | UC-CS-01/02 |
| `cart` | Guest + customer cart, discount codes | UC-CS-05/06 |
| `commerce` | Pricing rules (qty 10%, shipping, EP/BVA) | Appendix A |
| `order` | Checkout, pay stub, state machine | UC-CS-07/08/10 |
| `payment` | `PaymentGateway` stub (`SUCCESS`/`DECLINE`/`TIMEOUT`) | Out-of-scope gateway internals |
| `wishlist` | Persist across sessions | UC-CS-09 / REQ-WL-06 |
| `admin` | Catalogue + ship/deliver | UC-CS-11/12 |

**Hard rules baked in:**

- Money is **integer RWF** (no floats).
- Login failures are **generic** (no user enumeration). Lock after **3** failures for **30 minutes**.
- Passwords are **BCrypt**; logs record `inputLen`, never the secret.
- Unpublished products and other customers’ orders return **404**, not 403 with a leak.
- Discount codes are alphanumeric **5–10** characters. Qty ≥ 5 gets 10% off; a code does not stack on top to 20%.
- Shipping is **0** if Gold **or** discounted subtotal ≥ 50,000 RWF, else **2,000**.
- Payment stub `TIMEOUT` marks `PAYMENT_TIMED_OUT` and **never** `PAID` (Chapter 1 MoMo scenario).
- Illegal transitions (`DELIVERED → PLACED`, `PAID → PLACED`) return **409**.

---

## 2. How to navigate the codebase

```
cloudsphere/
├── frontend/                   # React storefront (Skype / Fluent UI)
├── docker-compose.yml          # Postgres + Redis (default) and optional `api` profile
├── Dockerfile                  # Multi-stage JRE 21 image
├── docs/openapi.yaml           # Static OpenAPI 3 contract (same surface as Swagger)
├── pom.xml                     # Spring Boot 4.1 parent
├── src/main/java/rw/ac/rca/cloudsphere/
│   ├── CloudsphereApplication.java
│   ├── config/                 # start here for wiring
│   ├── identity/ catalog/ cart/ order/ ...
│   └── ...
├── src/main/resources/
│   ├── application.properties
│   ├── logback-spring.xml      # console logs; JSON on json/prod
│   └── db/migration/V1__init_cloud_sphere.sql
└── src/test/java/...           # Pricing + state machine + Testcontainers IT
```

**Read in this order**

1. `application.properties` — datasource, Flyway schema, JWT, payment stub.
2. `db/migration/V1__init_cloud_sphere.sql` — physical model.
3. `config/SecurityConfig.java` — what is public vs `ROLE_ADMIN`.
4. `commerce/Pricing.java` and `order/OrderStateMachine.java` — domain rules with no Spring.
5. Controllers under `/api/v1` — HTTP surface (also visible in Swagger).

---

## 3. How to run it (step by step)

### Prerequisites

- JDK **21**
- Docker Desktop (for Postgres, Redis, and Testcontainers)
- Maven wrapper is already in the repo (`./mvnw`)

### Step 1 — start infrastructure

From this directory:

```bash
docker compose up -d
docker compose ps
```

Wait until `cloudsphere-postgres` and `cloudsphere-redis` are healthy.

If port **5432** or **6379** is already taken on your machine:

```bash
POSTGRES_PORT=5433 REDIS_PUBLISH_PORT=6380 docker compose up -d
DATABASE_URL=jdbc:postgresql://localhost:5433/cloudsphere REDIS_PORT=6380 ./mvnw spring-boot:run
```

Optional — run Postgres, Redis, **and** the API in Docker (needs a JDK in the image build):

```bash
docker compose --profile app up --build
```

### Step 2 — run the API

```bash
./mvnw spring-boot:run
```

Or build a jar and run it:

```bash
./mvnw -q -DskipTests package
java -jar target/cloudsphere-1.0.0-SNAPSHOT.jar
```

The process listens on **http://localhost:8080**. Flyway creates schema `cloud_sphere` and `DataInitializer` seeds demo rows if the tables are empty.

### Step 2b — run the React storefront

The shop UI lives in `frontend/` — a React ecommerce portal using Skype brand colors (`#00AFF0`). Vite proxies `/api` to the backend.

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. Sign in with `demo@cloudsphere.rw` / `CloudSphere1` (or `ops@cloudsphere.rw` for admin). Theme control is the moon icon on the left rail (light / dark / system).

### Step 3 — smoke

```bash
curl -s http://localhost:8080/api/v1/health
# {"status":"UP"}
```

Open docs:

- Swagger UI: http://localhost:8080/swagger-ui
- OpenAPI JSON (live): http://localhost:8080/v3/api-docs
- OpenAPI YAML (repo): `docs/openapi.yaml`
- Prometheus: http://localhost:8080/actuator/prometheus

### Step 4 — a first shopper flow

Demo password for every seeded user is `CloudSphere1` (letter + digit, ≥ 8).

| Email | Role | Notes |
|---|---|---|
| `demo@cloudsphere.rw` | CUSTOMER | Standard shipping |
| `gold@cloudsphere.rw` | CUSTOMER | Free shipping under 50k |
| `locked@cloudsphere.rw` | CUSTOMER | Already locked |
| `ops@cloudsphere.rw` | ADMIN | Catalogue + fulfill |

```bash
# login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@cloudsphere.rw","password":"CloudSphere1"}' | jq -r .accessToken)

# list catalogue
curl -s http://localhost:8080/api/v1/products | jq .

# guest cart (keep X-Cart-Token)
curl -sD - http://localhost:8080/api/v1/cart
```

Authorize Swagger with `Bearer <TOKEN>` (Authorize button) to call checkout, wishlist, and admin.

Force a payment timeout (UAT CO-008):

```bash
curl -s -X POST http://localhost:8080/api/v1/checkout \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-1" \
  -H "X-Payment-Stub-Mode: TIMEOUT" \
  -d '{"paymentMethod":"MTN_MOMO"}'
```

Default stub mode is `SUCCESS` (`PAYMENT_STUB_MODE` env / `cloudsphere.payment.stub-mode`). Repeat with the same `Idempotency-Key` to get the original order, not a second charge.

### Step 5 — tests

How to write them, including which level to use and what a test cannot prove: [`docs/testing/README.md`](docs/testing/README.md). GitLab runs the same commands from [`.gitlab-ci.yml`](.gitlab-ci.yml).

Unit tests only (no Docker):

```bash
./mvnw -q -Dgroups=unit test
```

Integration tests only (Docker required):

```bash
./mvnw -q -Dgroups=integration test
```

End-to-end tag. No browser suite is tagged `e2e` yet, so this exits without running a test:

```bash
./mvnw -q -Dgroups=e2e -Dsurefire.failIfNoSpecifiedTests=false test
```

Full suite and the 85% line-coverage gate:

```bash
./mvnw verify
```

---

## 4. API map

All business endpoints are under `/api/v1`. Errors are RFC 7807 `application/problem+json` with `traceId` (also echoed as `X-Trace-Id`).

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | public | Smoke; no version leak |
| POST | `/auth/register` | public | Create customer + JWT |
| POST | `/auth/login` | public | JWT |
| POST | `/auth/logout` | JWT | Revoke `jti` in Redis |
| GET | `/auth/me` | JWT | Current user |
| GET | `/products` | public | Search, page size ≤ 20 |
| GET | `/products/{id}` | public | 404 if draft/unknown |
| GET | `/categories` | public | Category list |
| GET/POST/PATCH/DELETE | `/cart...` | public or JWT | Guest uses `X-Cart-Token` |
| POST | `/checkout` | JWT | Place + pay (`Idempotency-Key`) |
| GET | `/orders`, `/orders/{id}` | JWT | Own orders only |
| POST | `/orders/{id}/pay` | JWT | Retry after timeout |
| GET/POST/DELETE | `/wishlist...` | JWT | Persist across sessions |
| POST/PATCH | `/admin/products` | ADMIN | Publish / stock |
| GET | `/admin/orders` | ADMIN | All orders |
| POST | `/admin/orders/{id}/ship` | ADMIN | Requires tracking no |
| POST | `/admin/orders/{id}/deliver` | ADMIN | |
| PATCH | `/admin/orders/{id}` | ADMIN | Explicit transition (409 if illegal) |

---

## 5. Configuration

| Property / env | Default | Meaning |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/cloudsphere` | JDBC URL |
| `DATABASE_USERNAME` / `PASSWORD` | `cloudsphere` | DB user |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `POSTGRES_PORT` / `REDIS_PUBLISH_PORT` | `5432` / `6379` | Host ports published by Compose (change if those ports are taken) |
| `JWT_SECRET` | dev-only string | **Change in any shared environment** (≥ 32 bytes) |
| `PAYMENT_STUB_MODE` | `SUCCESS` | `SUCCESS`, `DECLINE`, `TIMEOUT` |
| `CORS_ORIGINS` | localhost:3000,5173 | Allowed browser origins |
| `cloudsphere.seed.enabled` | `true` | Seed demo users/products |

Schema is forced to **`cloud_sphere`** via Flyway `spring.flyway.default-schema` and `hibernate.default_schema`. Do not point this app at a database you care about without a dedicated catalog.

---

## 6. Production notes (what we did on purpose)

- `ddl-auto=validate` — schema comes only from Flyway, never from Hibernate auto-create.
- HikariCP pool, `open-in-view=false`, UTC timestamps.
- Stateless JWT, CORS allow-list, CSRF off (pure API).
- Actuator exposes `health`, `prometheus`, `info` only; health **details are hidden**.
- Process runs as a non-root user in the Docker image.
- Stub payment adapter is an interface (`PaymentGateway`) so a real MoMo/card client can replace it without touching checkout.

Out of scope, same as the SRS: native mobile apps, real payment-gateway internals, live MoMo charges, production PII.
