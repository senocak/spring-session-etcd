# Spring Session etcd
`spring-session-etcd` provides an etcd v3-backed Spring Session repository. It moves HTTP session state out of an application's local memory and stores it as JSON values in etcd, where every application instance can access it.

## What is etcd?
*[Etcd]([etcd](https://etcd.io/))* is an open-source, strongly consistent, distributed key-value store designed to safely save critical data, configuration details, and metadata for computer clusters. Its name combines the Unix system folder */etc* with the letter *d* for distributed. It serves as the core database for *Kubernetes* to track cluster state.
Key Features:
- Distributed Consensus: Uses the Raft algorithm so all connected machines agree on data changes safely, even if some servers crash.
- Watch API: Allows applications to watch for changes on specific keys and react instantly when data updates.
- Security: Supports optional SSL/TLS client certificate authentication to keep data safe.
- Written in Go: Built in the Go programming language for high performance and easy integration into cloud tools.
- A simple key-value API, suitable for small documents such as session records.
- Leases, which automatically remove keys when their time-to-live expires.
Those leases make etcd particularly useful for sessions: the expiry policy lives with the stored value, rather than relying on one application instance to run a cleanup task at the right time.

## The problem this library solves
Most servlet containers keep `HttpSession` data in the memory of one application process. That is convenient for local development, but it breaks down in common deployment scenarios:
- Restarting the application signs every user out because its in-memory sessions are lost.
- A load balancer can send a user's next request to a different instance, where that session does not exist.
- Horizontal scaling requires sticky sessions, session replication, or a shared session store.
- Session expiry must be cleaned up independently on every instance.

Spring Session solves this at the application boundary by routing `HttpSession` operations through a `SessionRepository`. This project supplies the missing etcd implementation of that repository. It lets teams that already operate an etcd cluster reuse it for web-session storage instead of introducing Redis or a relational session database solely for that purpose.

## Why `spring-session-etcd` matters
With this library, session state becomes independent of an individual application instance:

| Without a shared store | With `spring-session-etcd` |
| --- | --- |
| Sessions live in one JVM. | Sessions live in etcd and are visible to every JVM. |
| A restart invalidates users' sessions. | Sessions survive an application restart. |
| Scale-out needs sticky sessions or replication. | Instances share the same session record. |
| Expired data needs local cleanup. | etcd leases remove expired keys automatically. |

The result is a small Spring Session integration with no application-specific etcd code. Existing controllers and Spring Security configuration continue to use the normal `HttpSession` APIs.

## How it works
Each session is stored at:
```text
<namespace>/sessions/<session-id>
```
On every save, the repository attaches an etcd lease for the remaining inactive interval. etcd then removes expired sessions without a local cleanup job. A negative inactive interval creates a persistent session key, and Spring Security session-id rotation removes the old key before saving the replacement.

Session attributes are serialized as JSON with Jackson 3 and Spring Security's Jackson modules. This supports common security objects, such as an authenticated principal, as well as ordinary application attributes.

## Comparison with other Spring Session backends
Spring Session applications keep the same `HttpSession` programming model regardless of the storage backend. The practical differences are operational: which service you already run, what throughput you need, how expiry is handled, and which repository features are required.

| Backend | Spring Session module | Strengths | Considerations |
| --- | --- | --- | --- |
| **etcd** (`spring-session-etcd`) | This library | Strongly consistent shared state, lease-based expiry, and a natural choice when etcd is already operated. | Custom `SessionRepository`; keep sessions small and do not treat etcd as a high-volume cache. |
| **Redis** (`spring-session-data-redis`) | Official | The most common production choice; very fast, mature, and purpose-built for TTL-based data. | Requires Redis infrastructure and its own operational model. Usually the better default when Redis is already available. |
| **JDBC** (`spring-session-jdbc`) | Official | Reuses an existing relational database, offers durable storage, and is easy to inspect with SQL. | Each session update is database work; cleanup and database growth need attention at scale. |
| **Hazelcast** (`spring-session-hazelcast`) | Official | Fits applications that already use a Hazelcast data grid. | Adds or depends on a Hazelcast cluster and its memory/replication model. |
| **Container memory** | Built in | No external dependency; ideal for a single local instance. | Sessions disappear on restart and cannot be shared safely across instances. |

### etcd versus `spring-session-data-redis`
`spring-session-data-redis` is usually the first choice for a new, high-throughput session store. Redis is designed for low-latency caching and its Spring Session module has a long production history.

Choose this library instead when etcd is already a supported dependency in your platform and session volume is modest. It avoids operating Redis solely for sessions, uses etcd's native leases for expiry, and keeps session records strongly consistent. It is not intended to make etcd a replacement for Redis as a general cache.

### etcd versus `spring-session-jdbc`
JDBC is a strong fit when an application already has a managed relational database and does not need another datastore. It is generally easier for teams that prefer SQL backups, auditing, and database tooling.

etcd is a better fit when the platform already runs an etcd cluster and the session store should be independent of the application's business database. Its leases also mean expired session keys are removed by etcd itself rather than by a database cleanup schedule.

### Feature boundary
This project implements Spring Session's core `SessionRepository` contract. It does not currently provide an indexed session repository, so features such as finding every session for a principal are not available. If that capability is important, evaluate an official backend whose repository supports the required index operations.

### Switching backends
Your controllers and Spring Security code do not need to change when switching Spring Session backends. The stored session formats are backend-specific, however, so plan a cutover that expires or invalidates old sessions and lets users establish new ones.

## When to use it
Use this library when:
- Your application runs more than one instance and needs shared Spring sessions.
- You already run etcd or have it available as supported infrastructure.
- You want etcd-managed TTL cleanup for session records.
- You need a custom Spring Session backend but want to keep the standard Spring
  Session programming model.

Prefer another backend when:
- You do not operate etcd and Redis or JDBC is already the simpler operational choice.
- Sessions are very large or have exceptionally high write throughput; etcd is a
  coordination/key-value store, not a general-purpose cache.
- You need Spring Session features that require an indexed repository, such as
  lookups by principal name. This library currently implements `SessionRepository`.

## Quick start
Build and install the library:
```bash
mvn -f library/pom.xml clean install
```
Add the dependency:
```xml
<dependency>
  <groupId>com.github.senocak</groupId>
  <artifactId>spring-session-etcd</artifactId>
  <version>0.0.1</version>
</dependency>
```
Enable etcd-backed HTTP sessions:
```java
@Configuration
@EnableEtcdHttpSession(
    maxInactiveIntervalInSeconds = 1800,
    endpoints = "${app.session.etcd.endpoints:http://localhost:2379}",
    namespace = "${app.session.etcd.namespace:spring-session/my-app}"
)
class SessionConfiguration {
}
```
## Example
The example uses the `etcd` profile by default and connects to `http://localhost:2379`.
```bash
docker compose up -d
mvn -f library/pom.xml clean install
mvn -f example/pom.xml spring-boot:run
```

Run the tests with:
```bash
mvn -f library/pom.xml test
mvn -f example/pom.xml test
```

## Summary
`spring-session-etcd` makes standard Spring `HttpSession` state available to every application instance through a shared etcd v3 store. It preserves familiar Spring Security and servlet-session APIs while etcd leases handle session expiry. It is a good fit for modest session workloads on platforms that already operate etcd and do not need indexed session lookups.

## Final
Choose a session backend based on the infrastructure and features your application needs. Redis remains the usual choice for a dedicated, high-throughput session store, and JDBC is often simplest when a relational database is already the operational standard. When etcd is the platform's established distributed key-value store, this library provides a focused way to use it for Spring Session without adding a second datastore just for HTTP sessions.

Keep sessions small, configure etcd authentication and TLS in production, and plan backend migrations as a session reset because stored formats are not interchangeable.
