# API Outline (initial proposal)

All endpoints are under `/api/v1`.

## Auth
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /users/me`

## Market and instruments
- `GET /markets`
- `GET /instruments?query=&exchange=&page=&size=`
- `GET /instruments/{symbol}`
- `GET /instruments/{symbol}/bars?interval=1d&from=&to=`
- `GET /markets/summary`

## Analysis
- `GET /instruments/{symbol}/analysis?interval=1d`
- `GET /instruments/{symbol}/indicators?interval=1d&indicators=RSI,MACD,EMA`
- `GET /screener?minScore=&maxScore=&rsiMax=&trend=`

## Watchlists
- `GET /watchlists`
- `POST /watchlists`
- `POST /watchlists/{id}/items`
- `DELETE /watchlists/{id}/items/{itemId}`

## Portfolio
- `GET /portfolios`
- `POST /portfolios`
- `GET /portfolios/{id}/transactions`
- `POST /portfolios/{id}/transactions`
- `GET /portfolios/{id}/performance`

## Alerts
- `GET /alerts`
- `POST /alerts`
- `PATCH /alerts/{id}`
- `DELETE /alerts/{id}`
- `GET /notifications`

This is a proposed contract. Confirm DTOs, authorization rules, pagination and error semantics during implementation.
