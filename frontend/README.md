# StockVision AI — Frontend Dashboard

Next.js + TypeScript web interface for **StockVision AI**, a Borsa İstanbul-first market analysis and explainable decision-support platform.

> **Decision-Support Notice:** StockVision AI does not provide personalized investment advice, does not execute brokerage orders, and makes no claims of guaranteed profit or risk-free returns.

## Requirements

- Node.js 20+ (verified with Node 24 LTS) and npm
- Running StockVision AI backend API (default: `http://localhost:8080`, CORS configured for `http://localhost:3000`)

## Configuration

Copy the environment template before local startup:

```bash
cp .env.example .env.local
```

Available public variables (`frontend/.env.example`):

- `NEXT_PUBLIC_API_BASE_URL` — Backend API base URL without trailing slash (default: `http://localhost:8080`)
- `NEXT_PUBLIC_DEFAULT_EXCHANGE` — Default exchange shown before user selection (default: `BIST`)
- `NEXT_PUBLIC_DEFAULT_TIMEZONE` — Default IANA timezone used before user profile/settings overrides (default: `Europe/Istanbul`)

## Scripts

Run inside `frontend/` (or with `npm --prefix frontend ...` from repo root):

- `npm install` — Install dependencies
- `npm run dev` — Start development server on `http://localhost:3000`
- `npm run lint` — Run Next.js / ESLint checks
- `npm run typecheck` — Run TypeScript compiler type check (`tsc --noEmit`)
- `npm test` — Run Vitest + Testing Library unit and component tests
- `npm run build` — Build production Next.js bundle

## Architecture Overview

- `src/lib/api/` — Typed API client (`ApiClient`, `ApiClientError`), bearer JWT injection, structured backend error parsing, and grouped service methods (`services.ts`). No scattered `fetch` calls in UI components.
- `src/lib/format.ts` — Explicit financial rounding (`2` decimals for prices/indicators, `1` decimal for scores, signed percentages) and UTC-to-user-timezone timestamp conversion (`Intl.DateTimeFormat`).
- `src/components/providers/` — `ThemeProvider` (dark/light toggle persisted in browser storage) and `AuthProvider` (JWT session lifecycle, automatic refresh token rotation via `/api/v1/auth/refresh`, `/api/v1/users/me` hydration, revocation-backed logout, and display timezone preference).
- `src/app/error.tsx` & `src/app/not-found.tsx` — Safe global error boundary (no stack traces leaked) and 404 navigation page.
- `next.config.ts` — HTTP security headers (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`).
- `src/components/instruments/candlestick-chart.tsx` — TradingView Lightweight Charts v5 interactive OHLCV candlestick, volume histogram, and selectable SMA/EMA/Bollinger overlay component.
- `src/components/instruments/score-breakdown.tsx` — Explainable `0–100` score components, `INSUFFICIENT_DATA` reasons, stale-data warnings, key drivers, and risk notes.
- `src/components/instruments/ai-explanation-card.tsx` — Optional AI / deterministic template explanation card displaying separated Observed Facts, Technical Interpretation, Uncertainties & Risks, audit metadata (`providerId`, `modelName`, `promptVersion`, `analysisRunId`), fallback warnings, and mandatory disclaimer.
- `src/app/` — App Router pages:
  - `/` (Dashboard summary, freshness diagnostics, active market movers, recent screener runs)
  - `/login`, `/register` (Authentication flows)
  - `/markets` (Exchange switcher, movers, searchable instrument directory)
  - `/screener` (Filterable quantitative screener)
  - `/instruments/[symbol]` (Candlestick chart, selectable indicators, explainable score, AI/template explanation card, quick Add-to-Watchlist & Create-Alert actions)
  - `/watchlist` (Multi-list CRUD, duplicate ticker protection, live quotes with freshness/source bar)
  - `/portfolio` (Virtual portfolio containers, chronological BUY/SELL ledger, derived holdings, average cost, separated Realized vs. Unrealized P&L, fee/tax policy disclosure)
  - `/alerts` (Price & indicator threshold rules, Pause/Resume & cooldown state, manual rule evaluation trigger, notifications inbox with provider & freshness badges)
  - `/settings` (Profile overview, dark/light theme, timezone override, third-party license notices)

## Open-Source Chart Attribution

Interactive financial charts are powered by **TradingView Lightweight Charts™** (`lightweight-charts`, Apache License 2.0). Per the Apache 2.0 LICENSE and NOTICE requirements:

- Built-in chart attribution (`attributionLogo: true`) remains enabled on chart canvases.
- Prominent attribution links to [https://www.tradingview.com](https://www.tradingview.com) appear alongside chart containers, in the global shell footer, and under `/settings#licenses`.
