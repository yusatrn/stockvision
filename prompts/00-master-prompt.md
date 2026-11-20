# Master Prompt — StockVision AI

Act as a senior full-stack engineer and software architect. Build **StockVision AI**, a Borsa İstanbul-first stock market analysis and decision-support web application.

Read `AGENTS.md`, `docs/product-requirements.md`, `docs/architecture.md` and `docs/api-outline.md` before changing code.

## Stack
- Backend: Java 21, Spring Boot 3.x, Maven, Spring Security, Spring Data JPA, Flyway, PostgreSQL, springdoc-openapi.
- Frontend: Next.js, React, TypeScript, Tailwind CSS, shadcn/ui, TradingView Lightweight Charts.
- Local infrastructure: Docker Compose; Redis may be introduced when justified.
- Tests: JUnit 5, Mockito, Testcontainers, frontend unit/component tests.

## Operating rules
1. Inspect repository and report current state before implementation.
2. Work phase by phase using the numbered prompts.
3. Do not skip tests or hide failures.
4. Do not invent market data or claim a provider is live.
5. Start with deterministic mock market data and a provider interface.
6. No brokerage order execution in MVP.
7. Use BigDecimal for monetary values.
8. Make analysis explainable and versioned.
9. Keep secrets out of source control.
10. Ask before adding paid services, external credentials, or irreversible infrastructure changes.

## Required deliverables
- Clean, runnable monorepo.
- Backend and frontend with documented local startup.
- Docker Compose for local dependencies.
- Flyway migrations.
- OpenAPI docs.
- Automated tests.
- `.env.example` files.
- README with setup and troubleshooting.
- Clear list of known limitations, especially market-data licensing and freshness.

Begin by inspecting the repository. Then propose a concise implementation plan and wait for approval before large changes.
