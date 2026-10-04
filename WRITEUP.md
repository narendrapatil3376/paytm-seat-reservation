# WRITEUP — Seat Reservation at Scale

## 1. Atomic Decision

The most important correctness decision is that the database is the source of truth for seat ownership.

A simple:

```text
SELECT seat
if available:
    UPDATE seat
```

without a concurrency control mechanism is unsafe because two requests can read the same available state before either update is committed.

The implementation therefore relies on transactional database consistency and row-level locking/atomic state changes rather than JVM-local synchronization.

### Why this is race-free

For a hot seat, concurrent transactions must serialize the ownership decision at the database level.

Conceptually:

```text
Transaction 1 -> acquire/guard seat -> reserve -> commit
Transaction 2 -> waits/conflicts -> re-checks state -> clean 409 decline
```

Therefore two application threads cannot both successfully commit ownership of the same seat.

This is preferable to a Java `synchronized` block because the application may run with multiple instances. A JVM lock protects only one process, while the database protects the shared state across instances.

### Multi-seat requests

Multi-seat reservation is treated as one transactional business operation.

The desired behavior is all-or-nothing:

```text
[A12, A13] available
        |
        v
reserve both
        |
        v
commit once
```

If one requested seat cannot be obtained, the transaction must not leave a partial reservation behind.

For multiple rows, locks should be acquired in a deterministic order, for example by sorting seat identifiers before locking:

```text
A12 -> A13 -> A14
```

rather than allowing different transactions to lock:

```text
Transaction A: A12 -> A13
Transaction B: A13 -> A12
```

Deterministic ordering reduces the possibility of deadlocks.

## 2. Idempotency

Reservation requests are retriable operations, so an idempotency key is required.

The important guarantee is:

```text
same key + same request
        |
        v
same business result
```

and:

```text
same key + different request
        |
        v
409 IDENTITY/IDEMPOTENCY CONFLICT
```

The key must be persisted in the database rather than kept only in application memory.

### Exactly-once business effect

The idempotency record and reservation business operation should participate in the same transactional decision.

The flow is conceptually:

```text
begin transaction
      |
      v
check idempotency key
      |
      +---- existing + same request ---> return stored result
      |
      +---- existing + different body -> 409
      |
      v
perform reservation
      |
      v
persist reservation result
      |
      v
commit
```

A unique database constraint on the idempotency key prevents two concurrent requests from independently creating the same idempotency record.

### Same key with different body

For example:

First request:

```json
{
  "seats": ["A12"]
}
```

with:

```text
Idempotency-Key: abc-123
```

Later:

```json
{
  "seats": ["A13"]
}
```

with the same key must not be treated as a retry.

The stored request representation/fingerprint is compared with the incoming request. A mismatch produces a `409` conflict.

## 3. Holds and Expiry

Seat ownership is represented through explicit reservation/seat states such as:

```text
AVAILABLE
HELD
CONFIRMED
```

The reconciliation invariant is:

```text
available + held + confirmed = total seats
```

at all times.

If a time-boxed hold is used, its expiration timestamp must be persisted in the database.

The important property is that expiry is not dependent on the JVM staying alive.

Conceptually:

```text
now < expires_at
    -> hold is active

now >= expires_at
    -> hold can be released/reconciled
```

An expired seat can then become available for another reservation.

A release operation must also be transactional so it cannot accidentally overwrite a newer reservation.

## 4. Per-User Limit

The assignment requires a default maximum of four seats per user per show.

The important concurrency issue is that this cannot be implemented only as:

```text
count existing reservations
if count < 4:
    insert reservation
```

without protecting the count decision.

Two concurrent requests could both observe:

```text
current = 3
```

and both insert seats, resulting in five or more seats.

The limit therefore needs to participate in the same concurrency-controlled transaction as the reservation decision.

The database remains the authoritative source for the final state.

## 5. Consistency vs Availability During a Partition

For this service, consistency is more important than availability when the database is unavailable.

A seat reservation is a financial/ownership-like operation. Returning success when the authoritative database cannot confirm the state would be unsafe.

Therefore, during a database/network partition:

```text
DB unavailable
     |
     v
do not invent reservation state
     |
     v
fail closed
```

The service should return an appropriate infrastructure error rather than claiming that a seat was successfully reserved.

This intentionally sacrifices availability in favor of preventing incorrect seat ownership.

## 6. Identity and Authorization

The reservation identity must come from the authenticated token.

The API must not trust:

```json
{
  "userId": "another-user"
}
```

from an untrusted request body.

The authenticated principal determines who owns the reservation.

The same principle applies to cancellation:

```text
authenticated user == reservation owner
```

is required before the reservation can be cancelled.

This prevents a user from modifying another user's reservation by changing a request field.

## 7. Observability

The assignment requires the service to be observable during a high-contention burst.

### Health

The service exposes Actuator health information.

A readiness check should include the database dependency because the application is not actually ready to process reservations if it cannot reach its database.

### Metrics

Important metrics include:

- confirmed reservations
- declined reservations
- decline reason
- available seats
- HTTP request count
- HTTP error count
- JVM metrics
- database connection-pool metrics

The most important reservation metrics should be compared against the API state after a burst.

For example:

```text
confirmed reservations
+
available seats
+
held seats
=
total seats
```

### Structured logs

Logs include structured fields such as:

```text
timestamp
level
thread
logger
requestId
message
```

The request/correlation ID is particularly useful during a concurrency test because many reservation attempts occur simultaneously.

## 8. What I Would Page on at 2 AM

I would configure alerts for:

### Critical

- readiness/health check failure
- database connection failure
- application unavailable
- sustained HTTP 5xx errors
- database connection pool exhaustion
- reservation consistency/reconciliation failure
- unexpected duplicate reservation detection

### Warning

- high reservation latency
- high p95/p99 response time
- increasing lock wait/deadlock frequency
- abnormal decline rate
- unusual database connection utilization
- JVM memory pressure

A domain-level `409 seat taken` is not itself an incident. During an on-sale event, a high number of `409` responses can be completely normal.

The distinction between expected business declines and infrastructure failures is important.

## 9. Correctness Under the Hot-Seat Burst

The key test is:

```text
many users
    |
    +---- A12
    +---- A12
    +---- A12
    +---- A12
    +---- ...
```

The required outcome is:

```text
A12 -> exactly one successful ownership
others -> 409 seat-taken/domain decline
5xx -> 0
```

The database transaction/locking strategy is responsible for making the ownership decision atomic.

## 10. AI Usage

AI tools were explicitly allowed and expected by the assignment.

I used AI as an engineering assistant for:

- implementation scaffolding
- Spring Boot configuration
- reviewing concurrency scenarios
- identifying race conditions
- reviewing idempotency edge cases
- generating test cases
- preparing curl/test commands
- Docker/Railway troubleshooting
- documentation
- reviewing deployment configuration

AI was not treated as the authority for the final design.

The developer made and reviewed the important decisions around:

- where the atomic reservation decision belongs
- database transactions
- concurrency control
- idempotency semantics
- authentication/authorization
- per-user limits
- failure behavior
- consistency versus availability
- production deployment

The code was built, run, and tested against the actual service rather than assuming generated code was correct.

## 11. What I Would Do Next

For a production-scale version, I would add:

1. **Concurrency integration tests**
   - hundreds/thousands of concurrent attempts against the same seat
   - concurrent per-user limit tests
   - concurrent idempotency retries

2. **Testcontainers**
   - run integration tests against a real MySQL container instead of relying only on mocks.

3. **Distributed observability**
   - OpenTelemetry traces
   - Prometheus
   - Grafana dashboards
   - alert rules

4. **Load testing**
   - k6/Gatling/JMeter
   - hot-seat and mixed-seat workloads
   - latency and throughput measurements

5. **Database tuning**
   - inspect lock waits
   - query plans
   - proper indexes
   - connection-pool sizing

6. **Security hardening**
   - secret management
   - key rotation
   - stricter JWT configuration
   - rate limiting
   - abuse protection

7. **Scalability testing**
   - multiple application instances
   - concurrent deployments
   - database failover behavior

8. **Operational safeguards**
   - automated reconciliation jobs
   - audit records
   - alerting on invariant violations

## Conclusion

The central design principle is simple:

> The database must make the atomic ownership decision.

The application should not rely on a read-then-write sequence or a JVM-local lock for correctness.

For a hot seat, exactly one transaction must win. Every other request should receive a predictable business decline rather than a server error.

Idempotency, authorization, per-user limits, transactional multi-seat reservation, and observability are built around that same principle: preserve a correct authoritative state even when requests are duplicated, retried, concurrent, or arrive during infrastructure failures.
