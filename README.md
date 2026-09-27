# spring-integration-tutorial

A complete tutorial for building message-driven integration flows with **Spring Integration**, on top of **Spring Boot 4.1.x** (Spring Framework 7, Java 17), applying core Enterprise Integration Patterns (EIP) to an order-intake pipeline that accepts orders from two different channels at once.

This document is the **complete specification** of the project: it is meant to be followed step by step to implement each branch.

## Table of contents

- [Spring Integration vs. scheduled batch processing](#spring-integration-vs-scheduled-batch-processing)
- [Core EIP concepts used in this tutorial](#core-eip-concepts-used-in-this-tutorial)
- [Tech stack](#tech-stack)
- [Data model](#data-model)
- [Branching strategy](#branching-strategy)
- [Project structure](#project-structure)
- [Standard response format](#standard-response-format)
- [feature/core-architecture](#featurecore-architecture)
- [feature/auth](#featureauth)
- [feature/order-intake](#featureorder-intake)
- [feature/message-routing](#featuremessage-routing)
- [feature/bulk-file-processing](#featurebulk-file-processing)
- [feature/error-handling](#featureerror-handling)
- [Order of work](#order-of-work)
- [Code conventions](#code-conventions)
- [Concepts covered](#concepts-covered)
- [How to follow this tutorial](#how-to-follow-this-tutorial)

## Spring Integration vs. scheduled batch processing

Both process files and both move data through a pipeline of steps, which makes it easy to blur the two - they solve different problems.

**Scheduled batch processing** works on a *bounded, known dataset*, on a *schedule* (nightly, hourly): a job starts, reads a fixed set of records, processes them in chunks, finishes, and reports a result. There is a clear beginning and end to each run, tracked as a job execution.

**Spring Integration** works on a *continuous, unbounded stream* of messages, *as they arrive*, with no concept of a "run" - a file landing in a watched directory, an HTTP request hitting a gateway, and a message on a queue are all just messages entering a flow at arbitrary times. There is no scheduled start, no fixed dataset, and no execution history the way a batch job has one.

This tutorial's order-intake pipeline processes orders **as they arrive**, from two different sources (an HTTP gateway and a file-drop folder), through the same flow - the point isn't "run this every night", it's "react to this the moment it happens", regardless of where it came from.

## Core EIP concepts used in this tutorial

Spring Integration is an implementation of the Enterprise Integration Patterns catalog (Hohpe & Woolf). The vocabulary matters because the code is organized directly around these names:

- **Message**: an immutable envelope (payload + headers) - everything in a Spring Integration flow is a `Message<T>`
- **Channel**: how messages move between components (`DirectChannel`, synchronous, single subscriber; `QueueChannel`, buffered, decouples producer/consumer speed; `PublishSubscribeChannel`, broadcasts to every subscriber)
- **Gateway**: the entry point from regular application code (or HTTP) into a messaging flow - a plain Java interface Spring Integration implements for you
- **Channel Adapter**: connects a channel to something outside the messaging system - inbound (a file poller, an HTTP endpoint) or outbound (writing a file, sending an email)
- **Transformer**: converts a message's payload from one type to another, without changing its meaning
- **Router**: inspects a message and decides which channel it continues on next - this tutorial uses a **content-based router**, deciding by order value
- **Splitter** / **Aggregator**: a splitter breaks one message (a multi-line file) into many; an aggregator collects related messages back into one, once a completion condition is met - the two are almost always used as a pair
- **Service Activator**: invokes a plain Spring bean method as the "business logic" step of a flow, wiring the return value back onto an output channel

## Tech stack

| Component | Choice |
|---|---|
| Framework | Spring Boot 4.1.x (Spring Framework 7) |
| Language | Java 17 (LTS) |
| Build | Maven |
| Integration | Spring Integration (`spring-boot-starter-integration`), version managed by the Spring Boot 4.1.x BOM |
| Database | PostgreSQL 16 (via Docker Compose) |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Security | Spring Security 7 + JWT (conventional REST layer, not an integration flow - see [feature/auth](#featureauth)) |
| API documentation | springdoc-openapi (Swagger UI) |
| Monitoring | Spring Boot Actuator, plus Spring Integration's integration graph: the Actuator `integrationgraph` endpoint, backed by Spring Integration's `IntegrationGraphServer` (visualizes the live message flow graph) |
| Tests | JUnit 5, Mockito, `spring-integration-test` (`MockIntegrationContext`, channel interceptors), Testcontainers |
| CI/CD | GitHub Actions |
| Containerization | Docker, docker-compose |

## Data model

Two independent domains, one shared database, no cross-domain foreign key.

```
users (id, first_name, last_name, email, password, enabled, account_locked)
    │ N──N (via role_user)
roles (id, role_name)

categories (id, category_name)
    │ 1
    │
    │ N
products (id, category_id, product_name, unit_price)
    │ 1
    │
    │ N
orders (id, customer_id, product_id, quantity, total, source, status)
    │ N        └─ source: API, FILE
    │           └─ status: RECEIVED, AUTO_CONFIRMED, PENDING_REVIEW, APPROVED, REJECTED, FAILED
    │ 1
customers (id, first_name, last_name, telephone, email, address)
```

`orders.source` and `orders.status` are additions to the base e-commerce model, needed to record which channel an order arrived through and what the routing flow decided. `total` is a snapshot of `quantity * product.unitPrice`, taken and stored at the moment the order is persisted (`feature/order-intake`), never recalculated afterward - a later price change on the product must not alter the total of an order already received, whichever channel it came through.

The identity domain is intentionally reduced to `users`, `roles` and `role_user`: `feature/auth` only registers, logs in and returns the current profile, so there are no activation, blacklist or password-reset token tables. A registered account is enabled immediately (`enabled = true`) and receives the `USER` role; the `ADMIN` and `USER` roles are seeded by `V1__init_schema.sql`.

Nothing in the API creates categories, products or customers: orders reference existing ones. In the `dev` profile, a demo-data migration kept in a separate Flyway location (`db/dev-data/`, enabled only by `application-dev.yml`) inserts a few categories, products and customers; the tests insert the data they need themselves. The production schema never contains demo data.

Authorization in this tutorial is deliberately simple and role-only (`hasRole('ADMIN')`/`hasRole('USER')` via Spring Security). There is no `Permission` entity or fine-grained resource/action model - the point of this project is Enterprise Integration Patterns, not an authorization system, and a `Role`-only check keeps that focus without pretending the access-control layer on top is more developed than it is.

## Branching strategy

| Branch | Role |
|---|---|
| `master` | Stable, production-ready code. No direct commits, only merges from `develop`. |
| `develop` | Integration branch. |
| `feature/core-architecture` | Project structure, base Spring Integration configuration, Docker, CI. |
| `feature/auth` | Conventional REST + JWT authentication, protecting the HTTP gateway and admin endpoints. |
| `feature/order-intake` | HTTP gateway and file inbound channel adapter, both converging on one flow that persists a single order. |
| `feature/message-routing` | Content-based router splitting the flow by order value into auto-confirm and manual-review paths. |
| `feature/bulk-file-processing` | Splitter/aggregator pair for multi-line order files, producing a per-file completion report. |
| `feature/error-handling` | Global error channel, retry advice, and a dead-letter file for messages that fail permanently. |

## Project structure

```
spring-integration-tutorial/
├── src/
│   ├── main/
│   │   ├── java/com/edgareldy/springintegrationtutorial/
│   │   │   ├── SpringIntegrationTutorialApplication.java
│   │   │   ├── config/
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── IntegrationConfig.java          (channel bean definitions, poller defaults)
│   │   │   │   └── AdminBootstrap.java             (creates the ADMIN account from environment variables, if set)
│   │   │   ├── dto/
│   │   │   │   ├── ApiResponse.java, PageResponse.java
│   │   │   │   ├── auth/ (RegisterRequest, LoginRequest, AuthResponse, UserResponse)
│   │   │   │   └── order/ (OrderIntakeRequest - the REST payload, RejectRequest)
│   │   │   ├── entity/
│   │   │   │   ├── User.java, Role.java
│   │   │   │   ├── Category.java, Product.java, Customer.java, Order.java, OrderSource.java, OrderStatus.java
│   │   │   ├── repository/
│   │   │   │   ├── UserRepository.java, RoleRepository.java
│   │   │   │   ├── CategoryRepository.java, ProductRepository.java,
│   │   │   │   │   CustomerRepository.java, OrderRepository.java
│   │   │   ├── service/
│   │   │   │   ├── UserService.java, OrderService.java   (contracts)
│   │   │   │   └── impl/ (one *ServiceImpl per interface)
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── OrderIntakeController.java          (POST /intake - calls OrderIntakeGateway)
│   │   │   │   └── OrderReviewController.java          (POST /approve, /reject - calls OrderReviewGateway)
│   │   │   ├── integration/
│   │   │   │   ├── message/                           (payload types travelling through the flows)
│   │   │   │   │   ├── OrderCommand.java                (normalized order, whatever its source)
│   │   │   │   │   ├── ReviewDecision.java
│   │   │   │   │   └── LineOutcome.java                 (per-line result collected by the aggregator)
│   │   │   │   ├── gateway/
│   │   │   │   │   ├── OrderIntakeGateway.java        (@MessagingGateway interface, entry point for both channels)
│   │   │   │   │   └── OrderReviewGateway.java         (@MessagingGateway interface, entry point for the review decision)
│   │   │   │   ├── adapter/
│   │   │   │   │   ├── FileInboundAdapterConfig.java   (polls an "incoming-orders" directory)
│   │   │   │   │   ├── ConfirmationOutboundAdapterConfig.java
│   │   │   │   │   ├── ReviewOutboundAdapterConfig.java
│   │   │   │   │   ├── RejectionOutboundAdapterConfig.java
│   │   │   │   │   ├── ReportOutboundAdapterConfig.java     (bulk completion report)
│   │   │   │   │   └── DeadLetterOutboundAdapterConfig.java (failed-orders/)
│   │   │   │   ├── transformer/
│   │   │   │   │   └── RawOrderTransformer.java         (CSV line or REST payload → OrderCommand)
│   │   │   │   ├── router/
│   │   │   │   │   └── OrderValueRouter.java             (content-based router by order total)
│   │   │   │   ├── splitter/
│   │   │   │   │   └── OrderFileSplitter.java             (multi-line file → one message per line)
│   │   │   │   ├── aggregator/
│   │   │   │   │   └── OrderFileAggregator.java            (per-line results → one completion report)
│   │   │   │   ├── activator/
│   │   │   │   │   ├── OrderPersistenceActivator.java       (@ServiceActivator, persists via OrderService)
│   │   │   │   │   └── ReviewDecisionActivator.java          (@ServiceActivator, resolves a PENDING_REVIEW order)
│   │   │   │   └── error/
│   │   │   │       └── OrderErrorHandler.java                (global error channel handler, dead-letter routing)
│   │   │   ├── security/ (JwtService, JwtAuthFilter)
│   │   │   └── exception/
│   │   │       ├── ResourceNotFoundException.java
│   │   │       ├── BusinessRuleException.java
│   │   │       └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-test.yml
│   │       └── db/
│   │           ├── migration/
│   │           │   └── V1__init_schema.sql
│   │           └── dev-data/                          (Flyway location enabled in the dev profile only)
│   │               └── R__demo_data.sql               (demo categories, products, customers)
│   └── test/
│       └── java/com/edgareldy/springintegrationtutorial/
│           ├── integration/   (spring-integration-test: MockIntegrationContext, per-endpoint tests)
│           ├── controller/    (MockMvc)
│           ├── service/       (Mockito)
│           └── repository/    (@DataJpaTest)
├── incoming-orders/            (watched directory, mounted into the container)
│   └── processed/               (files already read, moved here so a restart never reprocesses them)
├── outgoing-orders/             (outbound files, mounted into the container)
│   ├── confirmations/           (auto-confirmed and approved orders)
│   ├── reviews/                 (review-queue entries)
│   ├── rejections/              (rejected orders)
│   └── reports/                 (one completion report per bulk file)
├── failed-orders/               (dead-letter directory, mounted into the container)
├── docker-compose.yml
├── Dockerfile
├── .github/workflows/ci.yml
├── pom.xml
└── README.md
```

## Standard response format

The HTTP-facing parts of this project (the auth REST layer, and the intake gateway's synchronous HTTP endpoint) return the same generic `ApiResponse<T>` used for REST elsewhere.

```java
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
```

The file-based flow has no HTTP response to wrap - its outcome instead becomes an outbound message (a confirmation file, a review-queue entry, or a dead-letter file), which serves the same purpose in a message-driven context: a clear, consistently-shaped record of what happened to each order.

## feature/core-architecture

### Tasks

- [x] Initialize the project (Maven, Java 17, Spring Boot 4.1.x)
- [x] Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-integration`, `spring-integration-file`, `spring-boot-starter-security`, `spring-boot-starter-actuator`, `flyway-core`, `postgresql`, `lombok`, `springdoc-openapi-starter-webmvc-ui`
- [x] Test dependencies: `spring-boot-starter-test`, `spring-integration-test`, `testcontainers`
- [x] `IntegrationConfig`: base channel beans, default poller (`@Bean PollerMetadata`)
- [x] `GlobalExceptionHandler`, `ApiResponse<T>`, `PageResponse<T>`
- [x] Flyway script `V1__init_schema.sql` (all tables from both domains, including `orders.source`/`orders.status`, and the seeded `ADMIN`/`USER` roles)
- [x] Actuator health check exposed at `/actuator/health`, including the database connection, plus the Actuator `integrationgraph` endpoint exposed in the `dev` profile only, so the live flow graph is inspectable at `/actuator/integrationgraph`. That endpoint is public (`permitAll`, like health): it only exists in the `dev` profile, so outside `dev` it answers `404` whatever the caller, and in `dev` it stays viewable straight from a browser
- [x] `docker-compose.yml` (app + the official `postgres:16` image, with the `incoming-orders/`, `outgoing-orders/` and `failed-orders/` directories mounted as volumes; every directory path is a configuration property), `.github/workflows/ci.yml`. The app service runs with the `dev` profile by default (`SPRING_PROFILES_ACTIVE`, overridable), so the demo data and the integration graph are there when following [How to follow this tutorial](#how-to-follow-this-tutorial); the compose `SPRING_DATASOURCE_*` variables take precedence over the `localhost` datasource of `application-dev.yml`

### Notes on what was built

- **Versions**: Spring Boot 4.1.1, which manages Spring Integration 7.1.1, Flyway 12.4 and Testcontainers 2.0.5; springdoc-openapi 3.1.1 is the only version held in a `pom.xml` property, since the Boot BOM does not manage it.
- **Spring Boot 4 modular starters**: Boot 4 moved each auto-configuration into its own module. Flyway comes through `spring-boot-starter-flyway` plus `flyway-database-postgresql` instead of `flyway-core` alone (which would sit on the classpath without ever migrating). `spring-integration-test` is brought by `spring-boot-starter-integration-test`, Testcontainers 2.x is declared as `spring-boot-testcontainers`, `testcontainers-postgresql` and `testcontainers-junit-jupiter`, and the MockMvc support comes from `spring-boot-starter-webmvc-test`. `spring-boot-starter-data-jpa-test` (`@DataJpaTest`) and `spring-boot-starter-security-test` (`@WithMockUser`) are declared from this branch on, so `feature/auth` and `feature/order-intake` can be built in parallel without both editing the `pom.xml`.
- **Bean Validation**: `spring-boot-starter-validation` is added on top of the listed dependencies, for `@Valid` request bodies and the 400 contract of `GlobalExceptionHandler`.
- **Error responses** also use `ApiResponse<T>` (`ApiResponse.error(...)`): 404 for `ResourceNotFoundException`, 422 for `BusinessRuleException`, 400 for a validation failure (field and class-level errors as `data`) or an unreadable body, 403 for a failed role check, the real status and headers for Spring MVC's own errors (such as `Allow` on a 405), and a generic 500 otherwise.
- **`IntegrationConfig`** declares the shared `intake-channel` (`DirectChannel`) and the default poller (`PollerMetadata.DEFAULT_POLLER`, fixed delay from `orders.poller.fixed-delay`, batch size from `orders.poller.max-messages-per-poll`), which replaces the one Spring Boot derives from `spring.integration.poller.*`. The channels of the later branches are added to it as they appear.
- **Schema**: `V1__init_schema.sql` creates the tables of both domains with foreign keys only inside a domain, CHECK constraints on `orders.source`, `orders.status` and `orders.quantity`, and seeds the `ADMIN` and `USER` roles (no account).
- **Security baseline**: `SecurityConfig` is stateless, disables CSRF, opens health, the OpenAPI document, Swagger UI, `/error` and `/actuator/integrationgraph`, requires authentication everywhere else and answers 401 to an anonymous caller. `feature/auth` plugs the JWT filter into it. `OpenApiConfig` declares the API title and a bearer JWT scheme, so Swagger UI offers an "Authorize" button.
- **Configuration**: every directory is a property under `orders.directories` (`incoming`, `processed`, `outgoing`, `confirmations`, `reviews`, `rejections`, `reports`, `failed`), the sub-directories being derived from their root. `application-test.yml` moves the three roots under `target/test-orders/` and shortens the poll interval; the Spring context tests run with `@ActiveProfiles("test")` (`{"dev", "test"}` for the integration graph test), and `src/test/resources/config/application.yml` activates the `test` profile for any test declaring none, such as the generated `contextLoads()` test, so no test ever touches the repository's order folders.
- **Order directories**: the repository holds the folders (each leaf keeps a `.gitkeep`, none directly in `incoming-orders/` since that folder is polled), and `.gitignore` excludes the files the flows exchange.
- **Docker**: `docker-compose.yml` publishes PostgreSQL on host port `${DB_PORT:-5433}` (5432 is often taken by a local installation), runs the application with the `dev` profile by default, bind-mounts the three order roots under `/data` and points `ORDERS_DIRECTORIES_*` at them, and runs the container as `${APP_UID:-1000}:${APP_GID:-1000}` so it can write to the mounted host folders. `.env.example` uses the same defaults as `application-dev.yml`.
- **CI**: `ci.yml` validates `docker-compose.yml` (`docker compose config -q`) then runs `mvnw verify` (Testcontainers starts `postgres:16` on the runner); `pr-checks.yml` validates Conventional Commits on pull requests. A pull request template lists the branch checklist and the review rules.
- **Test packages**: besides the four folders of the project structure, tests of classes outside those layers mirror their main package (`config`, `dto`, `exception`) or the concern they check (`flyway`, `actuator`).

## feature/auth

Conventional REST + JWT - deliberately **not** modeled as an integration flow, since authentication isn't naturally an Enterprise Integration Pattern; forcing it into one would add indirection with no benefit.

### Endpoints

| Method | URL | Description |
|---|---|---|
| POST | `/api/v1/auth/register` | Register |
| POST | `/api/v1/auth/login` | Returns a JWT |
| GET | `/api/v1/auth/me` | Current user profile |

### Tasks

- [x] `User`, `Role` entities (with the `role_user` join), `UserRepository`, `RoleRepository`
- [x] `UserService` (interface) + implementation: registration creates an enabled account with the `USER` role (duplicate email rejected), login issues a JWT, `me` returns the current profile
- [x] `AdminBootstrap`: at startup, creates an account with the `ADMIN` role from `APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD` when both are set and the account does not exist yet - no default password anywhere in the code or the migrations, and nothing happens when the variables are absent
- [x] `JwtService`, `JwtAuthFilter`, `SecurityConfig`
- [x] `SecurityConfig` requires authentication on every `/api/v1/orders/**` route (both the intake gateway and the review endpoints added in `feature/message-routing`); the review endpoints additionally require `hasRole('ADMIN')`. The file-drop channel has no HTTP surface and is not subject to this filter - it is a trusted, internal-only input by design, documented as such rather than left ambiguous
- [x] `AuthController`
- [x] Tests: standard controller/service tests, no Spring Integration involved

### Notes on what was built

- **JWT library**: jjwt 0.13.0 (`jjwt-api`, with `jjwt-impl` and `jjwt-jackson` at runtime), version held in a `pom.xml` property since the Boot BOM does not manage it. Tokens are HMAC-SHA signed, carry the email as subject, the roles (informative only) and an expiry.
- **Configuration**: `app.jwt.secret` (`APP_JWT_SECRET`, at least 32 bytes, no default in `application.yml`: outside `dev` and `test` the application refuses to start without it), `app.jwt.expiration` (`1h`), `app.admin.email`/`app.admin.password` (`APP_ADMIN_EMAIL`/`APP_ADMIN_PASSWORD`, empty by default). `application-dev.yml`, `docker-compose.yml` and `.env.example` share one development secret; `application-test.yml` holds a fixed test secret. The admin variables are empty in docker-compose and commented out in `.env.example`: no default password anywhere.
- **Responses**: register answers `201` with a `UserResponse` (id, names, email, roles), login `200` with an `AuthResponse` (`accessToken`, `tokenType: Bearer`, `expiresIn` in seconds), `me` `200` with the `UserResponse`. A taken email is a `BusinessRuleException` (`422`, consistent with the other business rules), invalid fields a `400`, wrong credentials a `401` with the same message whether the email exists or not, a disabled or locked account a `401`. A password longer than the 72 bytes BCrypt hashes (possible within 72 characters once multi-byte characters are used) is refused with a `422` instead of failing in the encoder; the same check gives a clear startup error for an oversized `APP_ADMIN_PASSWORD`.
- **Emails** are trimmed and lower-cased on registration, login and admin creation, so the login is case-insensitive and `Jane@Example.com` cannot register twice.
- **Security errors as `ApiResponse`**: the entry point (`401`) and the access denied handler (`403`) of `SecurityConfig` delegate to Spring MVC's `handlerExceptionResolver`, so `GlobalExceptionHandler` renders them like any other error; it gained an `AuthenticationException` handler for that.
- **Login** goes through Spring Security's `AuthenticationManager` (a `DaoAuthenticationProvider` over a `UserDetailsService` bean declared in `SecurityConfig`, which maps the `User` entity to Spring's `UserDetails`, so the entity implements no security interface). Passwords use the delegating encoder (BCrypt, stored with its `{bcrypt}` prefix).
- **`JwtAuthFilter`** is not a Spring bean (Spring Boot would also register it as a servlet filter): `SecurityConfig` creates it inside the chain. It reloads the account on every request, so a locked or deleted account or a changed role takes effect immediately; an invalid token leaves the request anonymous.
- **Order routes**: `/api/v1/orders/*/approve` and `/api/v1/orders/*/reject` require `hasRole('ADMIN')` (any HTTP method), every other `/api/v1/orders/**` route an authenticated caller. `@EnableMethodSecurity` is on, for `@PreAuthorize` in later branches. The review endpoints themselves arrive with `feature/message-routing`.
- **`AdminBootstrap`** is an `ApplicationRunner` delegating to `UserService.createAdminIfAbsent`; the admin account gets the `ADMIN` role only, with the names "Admin Account".
- **Tests**: the MockMvc and security tests share the cached Spring context and run `@Transactional`, so the accounts they create are rolled back and the shared database stays empty. The repository tests are `@DataJpaTest` against the Testcontainers PostgreSQL (`replace = NONE`). The `403` rules are tested without any order controller: the URL rules reject a `USER` before dispatch.

## feature/order-intake

The first end-to-end flow: a single order, arriving from either of two sources, ending up persisted the same way regardless of which one it came from.

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| POST | `/api/v1/orders/intake` | HTTP entry point into `OrderIntakeGateway`, answers `202 Accepted` with an `ApiResponse<Void>` | any authenticated user |

### Tasks

- [x] `OrderIntakeGateway` (`@MessagingGateway`): a plain Java interface (`void submit(OrderIntakeRequest request)`), Spring Integration generates the implementation that puts a message onto the shared intake channel. The gateway carries the **raw** REST payload, not an `OrderCommand`: turning it into an `OrderCommand` is the transformer's job, for both sources
- [x] `OrderIntakeController` calling the gateway directly for `POST /api/v1/orders/intake`, with Bean Validation on `OrderIntakeRequest` (`customerId`, `productId`, `quantity > 0`). The intake channel is a `DirectChannel`, so the HTTP flow runs in the caller's thread: an unknown customer or product comes back to the HTTP client as a `404` through `GlobalExceptionHandler`
- [x] `FileInboundAdapterConfig`: polls `incoming-orders/`, one file per order (`@InboundChannelAdapter` + `FileReadingMessageSource`), each file's content becomes a message on the **same** intake channel the gateway uses. Each file is a single CSV line, without header: `customerId,productId,quantity` - the same three fields the REST payload carries, just comma-separated instead of JSON. Once read, a file is moved to `incoming-orders/processed/`, so a restart never processes it again (the in-memory duplicate filter does not survive a restart)
- [x] `RawOrderTransformer` (`@Transformer`): normalizes both sources' payloads (an `OrderIntakeRequest`, a raw CSV line) into one common `OrderCommand` type carrying its `source` (`API` or `FILE`) - this is the point where "where the message came from" stops mattering to the rest of the flow
- [x] `OrderPersistenceActivator` (`@ServiceActivator`): persists the `OrderCommand` via `OrderService`, which checks that the customer and product exist (`ResourceNotFoundException` otherwise), takes the `total` snapshot (`quantity * unitPrice`) and saves the order with `status = RECEIVED`
- [x] Demo data for the `dev` profile: `db/dev-data/R__demo_data.sql` (a few categories, products and customers, idempotent inserts), added to `spring.flyway.locations` in `application-dev.yml` only
- [x] Tests: `spring-integration-test`'s `MockIntegrationContext` to test the transformer and activator in isolation, plus one end-to-end test per source (HTTP call, and a file dropped into a test directory) both landing in the database

### Notes on what was built

- **Flow wiring**: gateway or file adapter → `intake-channel` → `RawOrderTransformer` → `order-command-channel` → `OrderPersistenceActivator`. Both channels are `DirectChannel`s declared in `IntegrationConfig`, so the HTTP call runs the whole flow in the request thread.
- **Activator output**: `OrderPersistenceActivator` returns the persisted `Order` (with its `total` snapshot). Until the router of `feature/message-routing` exists, its output channel is `nullChannel`, which discards it: without an output channel the endpoint would look for a reply channel that neither source provides (the gateway method returns `void`). `feature/message-routing` replaces `nullChannel` with the router's input channel.
- **Gateway exceptions**: checked by a test, not assumed. The generated gateway rethrows the first non-messaging runtime exception found in the cause chain, so a `ResourceNotFoundException` thrown by `OrderService` reaches `GlobalExceptionHandler` as itself (404), not as a `MessageHandlingException` (500). Sent directly to a channel, the same failure is wrapped; no unwrapping code was needed.
- **Transformer**: the payload type decides the source (`OrderIntakeRequest` → `API`, `String` → `FILE`). A CSV line must hold exactly three integers with a strictly positive quantity (the same rule as the REST Bean Validation); surrounding spaces, a trailing line break and a UTF-8 byte order mark are tolerated. Anything else raises an `IllegalArgumentException` whose message starts with `Malformed order line`.
- **File adapter**: `FileReadingMessageSource` on `orders.directories.incoming`, not recursive, filtered on `*.csv` plus an in-memory `AcceptOnceFileListFilter`, polled by the default poller of `IntegrationConfig`. The `@InboundChannelAdapter` bean reads the file content (UTF-8), moves the file to `orders.directories.processed` (created at startup, an existing file of the same name is replaced) **before** sending it, then removes it from the accept-once filter, so a later file with the same name is read as a new order. The message carries the content as its payload plus the `file_name` and `file_originalFile` (the processed location) headers.
- **File path failures**: this branch has no error handling of its own. A malformed line or an unknown customer or product in a dropped file fails in the poller thread and goes to Spring Integration's default `errorChannel`, which logs it; the file is already in `processed/` and no row is created. `feature/error-handling` adds the handler and the dead-letter file.
- **Service**: `OrderService.receive(OrderCommand)` is transactional, looks the customer and the product up (`ResourceNotFoundException` otherwise), takes `total = unitPrice * quantity` rounded to two decimals and saves the order with `status = RECEIVED` and the command's `source`.
- **HTTP**: `POST /api/v1/orders/intake` answers `202` with `ApiResponse<Void>` (`"Order accepted"`), `400` on a missing field, a non-positive quantity or invalid JSON, `404` on an unknown customer or product, `401` for an anonymous caller (the baseline `SecurityConfig` is unchanged).
- **Demo data**: `R__demo_data.sql` inserts 3 categories, 5 products (49.90 to 1299.00) and 3 customers, idempotently (`ON CONFLICT` on the unique columns, `NOT EXISTS` on the product name). On a fresh database the ids follow the listed order, so `1,2,2` (458.00) is a low-value order and `1,3,1` (1299.00) a high-value one.
- **Tests**: `integration/OrderIntakeFlowTest` uses `@SpringIntegrationTest` and `MockIntegrationContext` to replace the persistence activator by a capturing mock (transformer in isolation) and feeds `order-command-channel` directly (activator in isolation). `integration/OrderIntakeEndToEndTest` covers both sources end to end with nothing mocked; since every cached context keeps its poller running, it watches its own incoming directory (`target/test-orders/end-to-end/`, a property, hence its own context), writes files under a temporary name before renaming them, and waits with Awaitility. Test data comes from `support/CommerceTestData` (plain SQL, unique names and emails, so tests sharing one database never collide). `flyway/DemoDataMigrationTest` checks the demo data and its idempotence in the dev context.
- **Entities** use Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor`); enums are stored with `EnumType.STRING` to match the CHECK constraints.

## feature/message-routing

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| POST | `/api/v1/orders/{id}/approve` | Resolves a `PENDING_REVIEW` order as approved, answers `200` with an `ApiResponse<Void>` | `hasRole('ADMIN')` |
| POST | `/api/v1/orders/{id}/reject` | Resolves a `PENDING_REVIEW` order as rejected (body: `RejectRequest` with a mandatory `reason`), answers `200` with an `ApiResponse<Void>` | `hasRole('ADMIN')` |

### Tasks

- [x] `OrderValueRouter` (`@Router`): inspects the persisted order's `total`, returns one of two channel names - `auto-confirm-channel` for orders under a configurable threshold (`orders.review-threshold`, default `1000.00`), `manual-review-channel` for orders at or above it
- [x] `ConfirmationOutboundAdapterConfig`: writes a confirmation file into `outgoing-orders/confirmations/` for auto-confirmed orders (`@ServiceActivator` + `FileWritingMessageHandler`), updates `status = AUTO_CONFIRMED`
- [x] `ReviewOutboundAdapterConfig`: writes a review-queue entry into `outgoing-orders/reviews/` (a file, for this tutorial, standing in for an email/ticket system) for orders needing review, updates `status = PENDING_REVIEW`
- [x] `OrderValueRouter` inserted into the flow **after** `OrderPersistenceActivator` (transformer → persistence → router → outbound adapter): the activator's output is the persisted order, so the router decides on the stored `total` snapshot, persistence happens exactly once and in one place whichever branch a message takes, and every branch only updates the status of an order that already exists
- [x] `OrderReviewGateway` (`@MessagingGateway`): a plain Java interface (`void approve(Long orderId)`, `void reject(Long orderId, String reason)`), puts a `ReviewDecision` message onto a review-decision channel - this is what actually closes out the `PENDING_REVIEW` path, since without it a manually-reviewed order would sit in that status forever
- [x] `OrderReviewController`: calls `OrderReviewGateway` for the two endpoints above
- [x] `ReviewDecisionActivator` (`@ServiceActivator`): loads the order, rejects the decision with a `BusinessRuleException` if it isn't currently `PENDING_REVIEW` (an order can only be resolved once), then updates `status = APPROVED` or `status = REJECTED` and forwards to the matching outbound adapter - `ConfirmationOutboundAdapterConfig` (reused as-is) for an approval, `RejectionOutboundAdapterConfig` (new, same shape as the review-queue writer, writing into `outgoing-orders/rejections/`) for a rejection. The review-decision channel is a `DirectChannel`: an unknown order comes back to the HTTP client as a `404`, an order that is not `PENDING_REVIEW` as a `422`
- [x] Tests: a low-value and a high-value order each routed to the correct outbound adapter, verified by asserting on the resulting file/database state, not by inspecting the router's internals directly; plus approve/reject tests covering the double-resolution guard

### Notes on what was built

- **Flow wiring**: `OrderPersistenceActivator` → `persisted-order-channel` → `OrderValueRouter` → `auto-confirm-channel` or `manual-review-channel`. Each outbound adapter config is three endpoints behind their own channels: a status step, a `@Transformer` rendering the order as a file message, and the `FileWritingMessageHandler` (`@ServiceActivator` on a `@Bean`). Auto-confirm: `auto-confirm-channel` → `autoConfirm` (status `AUTO_CONFIRMED`) → `confirmation-channel` → `toConfirmationFile` → `confirmation-file-channel` → `confirmationFileWriter`. Review: `manual-review-channel` → `queueForReview` (status `PENDING_REVIEW`) → `review-queue-channel` → `toReviewEntry` → `review-file-channel` → `reviewFileWriter`. Rejection: `rejection-channel` → `toRejectionFile` → `rejection-file-channel` → `rejectionFileWriter`. Every channel is a `DirectChannel` declared in `IntegrationConfig`, so an HTTP intake or review decision runs down to its outbound file in the caller's thread.
- **Status before file**: each outcome's status is recorded first (exactly one update through `OrderService`), then the file is written by a separate endpoint. The approval reuses the confirmation adapter as is by entering at `confirmation-channel`, after its `APPROVED` status is recorded; the rejection enters at `rejection-channel` after `REJECTED`, with the reason in the `rejectionReason` header. A failure while writing a file therefore happens after the status update, in an endpoint of its own (`*FileWriter.serviceActivator`), which `feature/error-handling` can target to set `FAILED`. Every file message carries the order id in an `orderId` header for that purpose.
- **Files**: one `order-<id>.txt` per order in `orders.directories.confirmations`, `reviews` or `rejections`, UTF-8 `key=value` lines: `orderId`, `status`, `source`, `quantity`, `total`, plus `approve=POST /api/v1/orders/<id>/approve` and `reject=POST /api/v1/orders/<id>/reject` in a review-queue entry and `reason=...` in a rejection (line breaks in the reason are replaced by spaces). Only the order's own columns are written: customer and product are lazy associations of an entity whose transaction is over. The writers create their directory, write under a temporary `.writing` name before renaming, and replace an existing file of the same name. The rendering and the writer settings are shared by the three configs through a package-private `integration/adapter/OrderFiles` helper, a class the project structure does not list.
- **Router**: `OrderValueRouter.route(Order)` returns `auto-confirm-channel` when `total < orders.review-threshold`, `manual-review-channel` otherwise (`compareTo`, so `1000` and `1000.00` are the same amount). The threshold is set in `application.yml` (`1000.00`), like the other `orders.*` properties.
- **Review decision**: `OrderReviewGateway` builds the `ReviewDecision` (`orderId`, `outcome` `APPROVED`/`REJECTED`, `reason`) with `@Gateway(payloadExpression = ...)` from the method arguments. `ReviewDecisionActivator` calls `OrderService.resolveReview`, which checks and records the decision in one conditional `UPDATE ... WHERE status = 'PENDING_REVIEW'` (`OrderRepository.updateStatusIfCurrent`): two concurrent decisions on the same order cannot both succeed. When no row is updated, the service answers `404` (`ResourceNotFoundException`) for an unknown order and `422` (`BusinessRuleException`, "Order with id X is not pending review (status Y)") otherwise. The activator then sends the resolved order to `confirmation-channel` or `rejection-channel` itself, since the target depends on the decision. As for the intake, the exceptions reach `GlobalExceptionHandler` unwrapped through the gateway.
- **HTTP**: `POST /api/v1/orders/{id}/approve` and `/reject` answer `200` with `ApiResponse<Void>` (`"Order approved"`, `"Order rejected"`). `RejectRequest.reason` is mandatory and non-blank (`400` otherwise), at most 500 characters. A non-numeric id is a `400` ("Invalid value for parameter 'id'"): `GlobalExceptionHandler` gained a `MethodArgumentTypeMismatchException` handler, the case would otherwise have been a `500`. `401` for an anonymous caller and `403` for a `USER` come from the existing `SecurityConfig` rules, unchanged.
- **OrderService**: `updateStatus(orderId, status)` (used by the auto-confirm and review steps, `404` for an unknown order; `feature/error-handling` can use it for `FAILED`) and `resolveReview(orderId, outcome)`.
- **Existing tests adjusted**: an intake now continues past persistence, so `OrderIntakeEndToEndTest` expects `AUTO_CONFIRMED` (waiting for it on the file path) and `OrderIntakeFlowTest` cuts the router off with `MockIntegrationContext` (`orderValueRouter.route.router`) to keep testing the persistence step alone. `CommerceTestData.singleOrderOf` also returns the `id`, and `statusOf(orderId)` was added.
- **Tests**: `integration/OrderRoutingEndToEndTest` sends low-value, high-value and boundary orders (`999.99`, exactly `1000.00`) through the gateway and the file source and checks the file written and the status, never the router. `controller/OrderReviewControllerTest` covers approve and reject through the real flow (confirmation or rejection file, status), the double-resolution guard (approve twice, reject after approve, approve an auto-confirmed order: `422`), `404`, `400` (missing, blank reason, non-numeric id), `401` and `403`. Both classes share one context through `support/RoutingTestDirectories` (own incoming and outgoing directories under `target/test-orders/routing/`, emptied before each test): every context has its own database, so order ids, and therefore file names, repeat from one context to another. Unit tests cover the router (Mockito-free), `ReviewDecisionActivator` (Mockito), `ReviewDecision`, `OrderServiceImpl` and the conditional update (`@DataJpaTest`); `IntegrationGraphEndpointTest` checks the new channels and endpoints.

## feature/bulk-file-processing

Extends the file channel adapter to handle a file containing many order lines at once, rather than one order per file.

### Tasks

- [ ] `OrderFileSplitter` (`@Splitter`): given a multi-line CSV file message, emits one message per line (blank lines ignored, no header), each carrying a correlation ID tying it back to the source file. From this branch on, **every** dropped file goes through the splitter: a single-line file is simply a batch of one, so the one-order-per-file format of `feature/order-intake` keeps working and also gets a report
- [ ] Each split line re-enters the **same** transformer → persistence → router flow already built in `feature/order-intake`/`feature/message-routing` - no duplicate processing logic for the bulk case
- [ ] Per-line failures (malformed line, unknown customer or product) are handled **locally**: they are turned into a failed `LineOutcome` sent to the aggregator instead of escaping to the global error channel, so one bad line never blocks the release of its file's group. A line that fails after its order was persisted (for example while writing its outbound file), or because persistence kept failing after its retries (`feature/error-handling`), is also reported as a failed line, so its file's group is still released; `feature/error-handling` additionally writes its dead-letter file and, when the order exists, sets `status = FAILED`. "A failed line creates no order row" applies to failures before persistence (malformed line, unknown customer or product), where no order row is ever created
- [ ] `OrderFileAggregator` (`@Aggregator`): collects the per-line outcomes (`LineOutcome`: auto-confirmed, pending review or failed) back together by correlation ID, releasing once every line from the source file has been accounted for
- [ ] `ReportOutboundAdapterConfig`: writes one summary file per source file into `outgoing-orders/reports/` (counts of confirmed/review/failed, plus the reason of each failed line), once the aggregator releases
- [ ] Tests: a multi-line file with a deliberate mix of low-value, high-value, and invalid rows, asserting the final summary report's counts match

## feature/error-handling

### Tasks

- [ ] A global `errorChannel` subscriber (`OrderErrorHandler`, `@ServiceActivator(inputChannel = "errorChannel")`): catches any exception raised anywhere in the flow that wasn't already handled locally. This concerns the asynchronous paths (the file poller); a synchronous HTTP call through a gateway keeps getting its error back as an `ApiResponse`, and bulk line failures stay handled by the aggregator path. Since every dropped file goes through the splitter, the dead-letter and `FAILED` rules below also apply to such failed lines, on top of their failed entry in the bulk report (see [feature/bulk-file-processing](#featurebulk-file-processing))
- [ ] Retry advice (`RequestHandlerRetryAdvice`) attached to `OrderPersistenceActivator`, so a transient database error is retried a configurable number of times (`orders.persistence.max-attempts`) before being treated as a failure
- [ ] `DeadLetterOutboundAdapterConfig`: a failed message is written to the `failed-orders/` directory (original payload + failure reason) rather than silently dropped. `FAILED` is a status, so it only applies to an order that exists: when the failure happens **after** persistence (for example while writing an outbound file), the order is also updated to `status = FAILED`; when persistence itself fails after its retries are exhausted, no order row exists and the dead-letter file is the only record
- [ ] Tests: a deliberately failing persistence call (mocked), verifying the retry count, that the message ends up in the dead-letter file with the right failure reason attached and that no order row was created; plus a failure after persistence, verifying the dead-letter file and `status = FAILED`

## Order of work

1. `feature/core-architecture` → Pull Request to `develop`
2. `feature/auth` (depends on `core-architecture`) → Pull Request to `develop`
3. `feature/order-intake` (depends on `core-architecture`) → Pull Request to `develop`
4. `feature/message-routing` (depends on `order-intake`) → Pull Request to `develop`
5. `feature/bulk-file-processing` (depends on `message-routing`) → Pull Request to `develop`
6. `feature/error-handling` (depends on `message-routing`, since `status = FAILED` applies to a failure occurring after persistence, in the routed part of the flow; can be built in parallel with `bulk-file-processing`) → Pull Request to `develop`

`feature/auth` and `feature/order-intake` both depend only on `core-architecture` and can be built in parallel: the order-intake HTTP tests authenticate with a mock user (`@WithMockUser`) rather than a real JWT.
7. `develop` → `master`

## Code conventions

- Root package: `com.edgareldy.springintegrationtutorial`
- **Contract/implementation services**: interface at the root of `service/`, implementation in `service/impl/`
- Every HTTP-facing endpoint returns an `ApiResponse<T>`
- A message never skips the shared transformer once it enters the flow - both the HTTP gateway and the file adapter converge onto the same channel immediately, so there is exactly one place that turns a raw input into an `OrderCommand`, not two
- Every terminal outcome (auto-confirmed, pending review, approved, rejected, failed) of a persisted order results in exactly one `Order.status` update - never left implicit in a log line only. An input that never became an order (rejected before or during persistence) leaves an explicit record instead: an HTTP error response, a failed line in a bulk report, or a dead-letter file
- Integration components are named after their EIP role, not their business purpose alone (`OrderValueRouter`, not `OrderHandler`), so the flow's shape is visible from the class names

## Concepts covered

- Core Enterprise Integration Patterns: Message, Channel, Gateway, Channel Adapter, Transformer, Router, Splitter, Aggregator, Service Activator
- Converging multiple inbound sources (HTTP, file) onto one shared processing flow
- Content-based routing, including closing out a manual-review branch with an explicit human decision (approve/reject) rather than leaving it as a dead end
- The splitter/aggregator pair for bulk, multi-item messages
- Global error handling, retry advice, and the Dead Letter Channel pattern
- The distinction between scheduled batch processing and continuous message-driven processing
- Testing integration flows in isolation (`spring-integration-test`, `MockIntegrationContext`)
- Live flow inspection via the Actuator `integrationgraph` endpoint (`IntegrationGraphServer`)
- Containerization (Docker, docker-compose)
- Continuous integration (GitHub Actions)

## How to follow this tutorial

1. Clone the repository (`https://github.com/EdgarEldy/spring-integration-tutorial.git`) and check out `develop`
2. Follow the branches in order: `feature/core-architecture` → `feature/auth` → `feature/order-intake` → `feature/message-routing` → `feature/bulk-file-processing` → `feature/error-handling`
3. Run `docker-compose up` (the application starts with the `dev` profile, so the demo categories, products and customers exist), then either `POST /api/v1/orders/intake` or drop a CSV file into `incoming-orders/` and watch it processed
4. Inspect the live flow graph at `http://localhost:8080/actuator/integrationgraph` (exposed when the application runs with the `dev` profile)
