# Phase 2 — Authentication and Users

Implement registration, login and current-user profile.

Requirements:
- User entity with unique normalized email, password hash, role, status and audit timestamps.
- Password hashing with BCrypt or Argon2.
- Spring Security configuration; stateless access-token authentication.
- JWT secret and expiry from environment variables; never commit secrets.
- Validate registration/login DTOs.
- Prevent users from enumerating registered emails through overly detailed responses.
- `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/users/me`.
- Add authorization tests and service tests.
- Add rate-limit design note; implement a practical basic protection if available in current stack.
- Do not expose password hashes in any response.

Document token handling and local test credentials strategy. Run all tests.
