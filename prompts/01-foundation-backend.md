# Phase 1 — Backend Foundation

Implement the backend foundation only.

Requirements:
- Create Spring Boot 3.x Maven project using Java 21.
- Add Web, Validation, Spring Data JPA, Spring Security, Flyway, PostgreSQL driver, Actuator and springdoc-openapi dependencies.
- Establish package structure from `docs/architecture.md`.
- Configure profiles: `local`, `test`, `prod`.
- Add environment-based configuration and `.env.example`.
- Add Docker Compose for PostgreSQL.
- Add Flyway baseline migration and health endpoint.
- Implement consistent API error handling and request validation.
- Add a basic integration test using Testcontainers if environment supports it.
- Do not implement business modules yet.

Run Maven tests and package. Update README with exact commands. Report files, test results and outstanding issues.
