# Paytm Seat Reservation Service

A backend service for concurrent seat reservation with a focus on correctness under high contention, idempotency, transactional consistency, and observability.

## Assignment

This project implements the Paytm Backend Engineering take-home exercise: **Seat Reservation at Scale**.

The service is designed to ensure:

- A seat cannot be confirmed for two users.
- Concurrent requests for the same seat produce one successful reservation and clean domain declines for the rest.
- A user cannot exceed the configured per-show seat limit.
- Retried requests with the same idempotency key do not create duplicate reservations.
- Reusing an idempotency key with a different request body is rejected.
- Multi-seat reservations are handled atomically.
- Reservation state remains reconcilable: `available + held + confirmed = total seats`.
- Money is represented as integer paise, never floating-point values.
- Health, metrics, and structured logging are available for operating the service.

## Technology Stack

- Java 17
- Spring Boot 3.3.4
- Spring Data JPA / Hibernate
- MySQL 8
- Flyway
- Spring Security / JWT
- Actuator
- Micrometer / Prometheus
- Docker / Docker Compose
- Railway deployment

## Repository Structure

```text
.
├── src/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── mvnw
├── mvnw.cmd
├── README.md
├── WRITEUP.md
└── burst-test.sh
```

## Local Setup

### Prerequisites

- Java 17
- Maven (or use the included Maven Wrapper)
- MySQL 8
- Docker / Docker Compose (optional)

### Build

Linux/macOS/Git Bash:

```bash
./mvnw clean package
```

Windows:

```bat
mvnw.cmd clean package
```

### Run with Docker Compose

```bash
docker compose up --build
```

The application is configured to run on port `8080`.

## Production Deployment

The service is deployed on Railway.

### Live URL

```text
https://paytm-seat-reservation-production-a9e2.up.railway.app/
```

### Health

```text
https://paytm-seat-reservation-production-a9e2.up.railway.app/actuator/health
```

### Prometheus Metrics

```text
https://paytm-seat-reservation-production-a9e2.up.railway.app/actuator/prometheus
```

The application exposes Actuator endpoints for health and operational metrics.

## API

The service provides JSON HTTP APIs for creating shows, reserving seats, inspecting show state, and managing reservations.

Before running the examples below, use the exact authentication and request format implemented by the current controllers.

### Create Show

```http
POST /shows
Content-Type: application/json
```

Example:

```json
{
  "name": "friday-night",
  "seats": ["A1", "A2", "A3", "A12"],
  "price_paise": 25000
}
```

### Reserve Seats

```http
POST /shows/{id}/reserve
Content-Type: application/json
Authorization: Bearer <JWT>
Idempotency-Key: <unique-key>
```

Example body:

```json
{
  "seats": ["A12"]
}
```

A successful reservation returns HTTP `201`.

A concurrent request that loses a seat race should return a domain-level `409`, not a `500`.

### Show State

```http
GET /shows/{id}
Authorization: Bearer <JWT>
```

The response should expose seat state and counts so that:

```text
available + held + confirmed = total_seats
```

remains true.

### Cancel Reservation

```http
POST /reservations/{id}/cancel
Authorization: Bearer <JWT>
```

Only the owner of a reservation can cancel it.

## Idempotency

Reservation requests use an idempotency key.

The intended guarantees are:

1. First request with a new key performs the reservation.
2. A retry with the same key returns the original result.
3. A request that reuses the same key with a different request body is rejected with `409`.
4. Idempotency processing is persisted so it is not dependent on a single application instance.

See `WRITEUP.md` for the design explanation.

## Concurrency / Hot Seat Test

The included `burst-test.sh` is intended to exercise the most important correctness property: many concurrent users attempting to reserve the same hot seat.

Run:

```bash
chmod +x burst-test.sh
./burst-test.sh
```

For Railway:

```bash
BASE_URL="https://paytm-seat-reservation-production-a9e2.up.railway.app" ./burst-test.sh
```

You can override the parameters:

```bash
BASE_URL="https://paytm-seat-reservation-production-a9e2.up.railway.app" \
SHOW_ID="1" \
SEAT_ID="A12" \
REQUESTS="20" \
./burst-test.sh
```

**Important:** Update the request body and endpoint in `burst-test.sh` if the final controller contract in the source code differs from the assignment example.

The expected hot-seat result is:

```text
1 confirmed
remaining requests -> 409 seat-taken/domain decline
5xx -> 0
```

## Multi-seat Reservation

Multi-seat requests are intended to be processed atomically.

For a request such as:

```json
{
  "seats": ["A12", "A13"]
}
```

the service should not partially commit the request if all-or-nothing semantics are configured.

The concurrency strategy and deadlock considerations are documented in `WRITEUP.md`.

## Per-User Limit

The service enforces a maximum number of seats a user can hold for a show.

The default assignment requirement is:

```text
4 seats per user per show
```

The limit must hold under concurrent requests as well as sequential requests.

## Observability

### Health

```text
/actuator/health
```

The health endpoint is used to verify application health and database connectivity.

### Metrics

```text
/actuator/metrics
/actuator/prometheus
```

Prometheus-compatible metrics are exposed for operational monitoring.

Important reservation metrics include:

- Confirmed reservations
- Declined reservations
- Decline reason
- Available seats
- HTTP request/error metrics
- JVM and database-pool metrics

### Logs

Application logs use structured JSON fields including:

- timestamp
- level
- thread
- logger
- request/correlation ID
- message

This makes concurrent reservation activity easier to trace.

## Failure Handling

Business conflicts such as a seat already being taken are domain outcomes and should be returned as `4xx` responses.

Infrastructure failures such as database connectivity failures are treated separately and should not be converted into successful reservations.

The database remains the source of truth for reservation state.

## Security

Authentication identity is derived from the JWT/token rather than trusting a `userId` supplied in the reservation request body.

This prevents a caller from booking or cancelling a reservation on behalf of another user by simply changing a request field.

## Database

The application uses MySQL and Flyway migrations.

Production database configuration is supplied through environment variables rather than committing credentials to Git.

Do not commit:

- database passwords
- JWT secrets
- private keys
- Railway credentials
- local `.env` files

## AI Usage

AI tools were used as engineering assistants during development.

Examples include:

- generating initial implementation scaffolding
- reviewing concurrency and transaction edge cases
- identifying idempotency requirements
- preparing test cases and curl commands
- reviewing deployment configuration
- debugging Railway/MySQL configuration
- preparing README and design documentation

The final architecture and implementation decisions were reviewed and tested by the developer. In particular, correctness requirements around concurrency, transactions, idempotency, authorization, and database consistency were treated as application design decisions rather than blindly accepting generated code.

More detail is available in `WRITEUP.md`.

## Git History

The assignment requests incremental commits. Keep the actual development history in the repository.

Example documentation commits:

```bash
git add README.md
git commit -m "Add project README"

git add WRITEUP.md
git commit -m "Add engineering writeup"

git add burst-test.sh
git commit -m "Add concurrent reservation burst test"

git push origin main
```

Do not squash the repository history after completing the assignment.

## Submission Checklist

Before submitting to Paytm:

- [ ] Public Git repository
- [ ] Full incremental commit history
- [ ] Clean checkout builds successfully
- [ ] Dockerfile included
- [ ] Docker Compose included
- [ ] README included
- [ ] WRITEUP.md included
- [ ] Burst test included
- [ ] Live Railway URL working
- [ ] Health endpoint working
- [ ] Prometheus endpoint working
- [ ] Database connected
- [ ] Flyway migrations successful
- [ ] Same-seat concurrency tested
- [ ] No duplicate seat confirmation
- [ ] No 5xx for expected reservation conflicts
- [ ] Per-user limit tested under concurrency
- [ ] Idempotency retry tested
- [ ] Same-key/different-body conflict tested
- [ ] Cancellation/release tested
- [ ] No secrets committed to Git

## License

This project was created as part of a technical interview assignment.
