# BookHive (microservices)

An online book club and reading tracker. Members start or join clubs, pick a book, log their reading progress and
discuss it chapter by chapter, so people who are behind never see spoilers.

This is the microservice version of BookHive: an API gateway, service discovery, central configuration and three
services that each own their data.

## Architecture

```
                         ┌────────────────────────┐
 Browser ── nginx :5173 ─►  API Gateway  :8080    │   routes /api/** by path, via Eureka (lb://)
 (React)    /api proxied  └───┬───────────┬──────┬─┘
                              │           │      │
                  /api/auth   │  /api/books│      │ /api/notifications
                  /api/users  │  /api/clubs│      │
                              ▼           ▼      ▼
                       ┌──────────┐ ┌───────────┐ ┌──────────────────────┐
                       │  user-   │ │  club-    │ │  notification-       │
                       │  service │◄┤  service  │ │  service             │
                       │  :8081   │ │  :8082    │ │  :8083               │
                       └────┬─────┘ └─┬───┬──┬──┘ └───▲──────────┬───────┘
                       userdb         │   │  │  Kafka │          │
                                 clubdb   │  └────────┘       notificationdb
                                          Redis (catalog cache)

   Eureka :8761 (registry)      Config Server :8888 (shared settings)
```

| Service | Owns | Notes |
|---|---|---|
| `user-service` | users, login, JWTs | Seeds an admin account. Exposes `/internal/users/batch` for other services. |
| `club-service` | books, clubs, memberships, progress, comments, reviews | Redis catalog cache, Google Books search, publishes notification events. |
| `notification-service` | notifications | Consumes Kafka events, serves the bell icon's endpoints. |
| `api-gateway` | nothing | Single entry point. Has no route for `/internal/**`. |
| `eureka-server` | registry | Services find each other by name. |
| `config-server` | shared settings | Serves `config-server/src/main/resources/config/*.yml`. |

### How the pieces talk

- **OpenFeign (synchronous):** `club-service` asks `user-service` for display names (`UserClient`) in one batched call per request.
  The call is wrapped in a Resilience4j circuit breaker: if `user-service` is slow or down, pages still load and show
  "Unknown reader" instead of failing.
- **Kafka (asynchronous):** when someone comments, reviews, joins a club or a club picks a book, `club-service` puts a JSON event on
  the `notifications` topic *after the database commit*. `notification-service` turns it into a stored notification.
  A Kafka outage never breaks posting a comment.
- **JWT:** `user-service` signs tokens; every service verifies them with the same `JWT_SECRET`. Identity (id, name, role)
  travels inside the token, so services don't need a users table.

### Design decisions

- **Database per service.** No cross-service foreign keys: other services' users are plain ids. (One Postgres container holds
  three databases to keep local setup light.)
- **Names are looked up, not copied,** so a renamed user shows up everywhere immediately.
- **The admin role comes from the token.** Promoting someone takes effect when they log in again (token lifetime 24h).
- **Redis is fail-open.** If Redis is down, the catalog is read from Postgres.
- **Calls to `user-service` happen inside a database transaction** in a few read paths. They are capped by short timeouts and
  the circuit breaker; moving them outside the transaction would be a sensible next step.

## Run everything

Needs Docker with Compose, and roughly 6-8 GB of free RAM (six JVMs plus Kafka).

```bash
cp .env.example .env        # then edit the secrets
docker compose up --build   # or: docker-compose up --build
```

First start takes a few minutes (Maven and npm downloads). Then:

| URL | What |
|---|---|
| http://localhost:5173 | the app |
| http://localhost:8080 | API gateway |
| http://localhost:8761 | Eureka dashboard: all three services should be listed as UP |

An admin account is created on first start: `admin@bookhive.local` / `Admin@12345` (change it in `.env`). Admins can add
books on the Books page. Everyone else signs up normally.

Stop with Ctrl+C. `docker compose down -v` also wipes the databases. The three databases are created only when the Postgres
volume is first created, so after changing `docker/postgres/init.sql` run `down -v`.

## Test it

```bash
mvn test                              # all backend modules (Docker must be running: Testcontainers starts real Postgres)
cd frontend && npm install && npm test

node scripts/smoke-test.mjs           # with the stack running: end-to-end through the gateway
```

- **Unit tests (Mockito):** business rules in each service, the circuit-breaker fallback, the Redis cache's fail-open behaviour,
  Kafka publishing rules, JWT handling.
- **Controller tests:** MockMvc with the real exception handler (status codes and messages).
- **Integration tests (Testcontainers + real Postgres):** repository queries, and full HTTP journeys for each service
  with real JWT security.
- **Gateway tests:** which paths reach which service, and that `/internal/**` reaches nothing.
- **Frontend tests (vitest):** pagination, route protection, auth state, notification bell, error messages.
- **`scripts/smoke-test.mjs`:** the only test that covers the real Kafka and Feign paths together; it signs up users, runs a
  club through its life, and checks that notifications arrive in other accounts.

## Run one service from your IDE

```bash
docker compose up postgres redis kafka config-server eureka-server   # infrastructure
cd user-service && mvn spring-boot:run                               # the defaults point at the published ports
```

Defaults: Postgres `localhost:5433`, Redis `localhost:6380`, Kafka `localhost:9094`, Eureka `localhost:8761`.
For the frontend: `cd frontend && npm install && npm run dev` (Vite proxies `/api` to the gateway on :8080).

## Configuration

Every setting can be overridden with an environment variable.

| Variable | Used by | Purpose |
|---|---|---|
| `JWT_SECRET` | all services | Shared signing secret, at least 32 characters. **Change it.** |
| `POSTGRES_PASSWORD` | compose | Database password |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` | user-service | First admin account (empty email disables it) |
| `GOOGLE_BOOKS_API_KEY` | club-service | Optional, raises the Google Books quota |
| `CONFIG_SERVER_URL`, `EUREKA_URL` | all services | Where to find the config server and registry |
| `USER_SERVICE_URL` | club-service | Optional fixed address that bypasses Eureka |

Tunables served by the config server (restart a service to pick up changes): token lifetime, catalog cache TTL, circuit-breaker
timeout (`config-server/src/main/resources/config/`). The config server is optional at runtime: services fall back to
the same defaults baked into their own `application.yml`.

## API (through the gateway)

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/signup`, `POST /api/auth/login`, `GET /api/users/me` |
| Books | `GET /api/books`, `GET /api/books/paginated`, `GET /api/books/search`, `GET /api/books/{id}`, `POST /api/books` (admin), `DELETE /api/books/{id}` (admin) |
| Reviews | `GET/POST /api/books/{bookId}/reviews` |
| Clubs | `POST /api/clubs`, `GET /api/clubs/grouped/paged?type=mine\|joined\|discover`, `GET /api/clubs/{id}`, `POST /api/clubs/{id}/join`, `PUT /api/clubs/{id}/current-book/{bookId}`, `GET /api/clubs/reading/{bookId}`, `DELETE /api/clubs/{id}` |
| Progress | `GET /api/clubs/{id}/progress`, `GET/PUT /api/clubs/{id}/progress/me` |
| Comments | `GET/POST /api/clubs/{id}/comments?chapter=N` |
| Notifications | `GET /api/notifications/me`, `GET /api/notifications/me/unread-count`, `PUT /api/notifications/{id}/read`, `PUT /api/notifications/read-all`, `DELETE /api/notifications/{id}` |

Errors are JSON: `{"message": "..."}`. Everything except `/api/auth/**` needs `Authorization: Bearer <token>`.

## Troubleshooting

- **A service fails at start with a Spring Cloud / Spring Boot compatibility error:** this project uses Spring Boot 4.1.1 with Spring Cloud
  2025.1.2. Spring Cloud's release notes name Boot 4.1.0, so if the check rejects 4.1.1, change the parent version in each `pom.xml` to
  4.1.0 (or set `spring.cloud.compatibility-verifier.enabled=false`).
- **Services missing in Eureka:** `docker compose logs <service>`. They wait for Postgres, Kafka, the config server and Eureka to be healthy first.
- **Names show as "Unknown reader":** `user-service` is unreachable or its circuit breaker is open (it closes again after ~15 s). Check Eureka.
- **No notifications:** `docker compose logs notification-service club-service`, and check Kafka is healthy (`docker compose ps`).
- **Port already in use:** stop anything on 5173, 8080 or 8761, or change the published ports in `docker-compose.yml`.

## Known limits

- One Kafka broker and one Postgres container; no replication or backups.
- Names are fetched per request (no cache), and notifications are polled by the frontend every 30 seconds (no websockets).
- A user deleted in `user-service` leaves orphaned ids elsewhere (there is no delete-user feature yet).
