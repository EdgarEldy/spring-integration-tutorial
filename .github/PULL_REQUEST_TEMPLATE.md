## Branch

<!-- e.g. feature/core-architecture -->

## Task checklist

<!-- Copy the relevant section's checklist from README.md and check off each item. -->

- [ ]

## Commit summary

<!-- One line per group of commits, summarizing what changed and why. -->

## Test checklist

- [ ] Unit (Mockito), repository (`@DataJpaTest`), controller (MockMvc) and integration flow (`spring-integration-test`) tests pass
- [ ] Every flow change is covered by a test asserting its observable outcome (database row, written file, HTTP answer)
- [ ] `mvnw verify` is green on the whole project

## Code review checklist

- [ ] Both inbound sources converge on the intake channel and go through the shared transformer; no processing logic is duplicated per source
- [ ] Integration components are named after their EIP role (`...Router`, `...Transformer`, `...Activator`...)
- [ ] Every terminal outcome of a persisted order updates `Order.status` exactly once; an input that never became an order leaves an explicit record
- [ ] Every HTTP endpoint answers with `ApiResponse<T>`, errors go through `GlobalExceptionHandler`
- [ ] Authorization is role-only (`hasRole('ADMIN')` / `hasRole('USER')`), no fine-grained permission model
- [ ] No business logic in controllers; services are an interface plus an implementation in `service/impl`
- [ ] The database schema only changes through a Flyway migration; every directory path is a configuration property
- [ ] Commits are atomic, follow Conventional Commits, no em dash
