# Phase 9 — Quality, Security and Release

Perform a release-readiness pass.

Checklist:
- Run backend unit/integration tests and frontend lint/typecheck/tests.
- Review authentication, authorization and IDOR risks.
- Review CORS, CSRF posture, JWT expiry, secret management and rate limiting.
- Check SQL indexes, pagination limits and N+1 queries.
- Verify Flyway migrations from an empty database.
- Verify Docker Compose startup and health checks.
- Add structured logs with trace/correlation IDs; redact secrets and personal data.
- Add Actuator health/readiness endpoints with safe exposure.
- Add API and UI error states.
- Add backup/restore notes and environment configuration guide.
- Document market data provider licensing, latency, symbol mapping and production readiness prerequisites.
- Do not claim production-ready until all checks pass.

Return a release report with passed/failed checks, commands and known risks.
