import crypto from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import type {
  ApiErrorResponse,
  BatchSyncResponse,
  HealthResponse,
  IndicatorType,
  InstrumentDetailResponse,
  InstrumentPageResponse,
  MarketMoverResponse,
  MarketSummaryResponse,
  OpportunitiesPageResponse,
  OpportunityItemResponse,
  ScreenerItemResponse,
  ScreenerPageResponse,
  UniverseScope,
  UniverseSyncStatusResponse,
  WatchlistCollectionResponse,
} from "@/lib/api/types";
import { BIST_EXCHANGE, BIST_INSTRUMENTS, findInstrument, normalizeSymbol } from "@/lib/server/bist-catalog";
import {
  calculateInstrumentAnalysis,
  calculateInstrumentSetup,
  calculateTechnicalIndicators,
  generateAiExplanation,
  generateHistoricalBars,
  getDataSourceMetadata,
  getFreshnessMetadata,
  getLatestQuote,
  PROVIDER_ID,
  SCORING_ALGORITHM_VERSION,
  SETUP_ENGINE_VERSION,
  STANDARD_DISCLAIMER,
  toOpportunityItem,
} from "@/lib/server/market-engine";
import {
  addPortfolioTransaction,
  addWatchlistItem,
  createAlert,
  createPortfolio,
  createWatchlist,
  deleteAlert,
  deletePortfolio,
  deleteWatchlist,
  evaluateUserAlerts,
  getPortfolioPerformance,
  getStore,
  listAlerts,
  listNotifications,
  listPortfolios,
  listPortfolioTransactions,
  listWatchlists,
  loginUser,
  logoutUserSession,
  markAllNotificationsRead,
  markNotificationRead,
  refreshUserSession,
  registerUser,
  removePortfolioTransaction,
  removeWatchlistItem,
  StoreError,
  updateAlert,
  updateWatchlist,
  verifyAccessToken,
} from "@/lib/server/store";

export const dynamic = "force-dynamic";

function apiError(
  status: number,
  code: string,
  message: string,
  path: string,
  fieldErrors?: Array<{ field: string; message: string }>
): NextResponse<ApiErrorResponse> {
  return NextResponse.json(
    {
      timestamp: new Date().toISOString(),
      status,
      code,
      message,
      path,
      traceId: crypto.randomUUID().slice(0, 12),
      ...(fieldErrors ? { fieldErrors } : {}),
    },
    { status }
  );
}

function parseIntParam(value: string | null, fallback: number, min: number, max: number): number {
  if (value === null || value === "") return fallback;
  const n = Number.parseInt(value, 10);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, n));
}

function parseFloatParam(value: string | null): number | undefined {
  if (value === null || value === "") return undefined;
  const n = Number.parseFloat(value);
  return Number.isFinite(n) ? n : undefined;
}

function parseBoolParam(value: string | null, fallback: boolean): boolean {
  if (value === null || value === "") return fallback;
  return value === "true" || value === "1";
}

function selectUniverse(scope: UniverseScope, query?: string | null) {
  const store = getStore();
  let list = BIST_INSTRUMENTS;
  if (scope === "BIST30") {
    list = BIST_INSTRUMENTS.slice(0, 30);
  } else if (scope === "BIST100") {
    list = BIST_INSTRUMENTS.slice(0, 100);
  } else if (scope === "SYNCED") {
    list = BIST_INSTRUMENTS.filter((i) => store.syncedSymbols.has(i.symbol));
  }
  const q = query?.trim().toLowerCase();
  if (q) {
    list = list.filter(
      (i) => i.symbol.toLowerCase().includes(q) || i.name.toLowerCase().includes(q)
    );
  }
  return list;
}

async function readJsonBody<T>(req: NextRequest): Promise<T> {
  try {
    return (await req.json()) as T;
  } catch {
    return {} as T;
  }
}

async function dispatch(
  req: NextRequest,
  segments: string[]
): Promise<NextResponse> {
  const method = req.method.toUpperCase();
  const path = `/api/v1/${segments.join("/")}`;
  const sp = req.nextUrl.searchParams;

  // -------------------------------------------------------------------------
  // Public routes: /health, /auth/*
  // -------------------------------------------------------------------------
  if (method === "GET" && segments.length === 1 && segments[0] === "health") {
    const body: HealthResponse = {
      status: "UP",
      service: "stockvision-ai-api",
      timestamp: new Date().toISOString(),
      dataProviderMode: "SIMULATED",
      disclaimer: STANDARD_DISCLAIMER,
    };
    return NextResponse.json(body);
  }

  if (segments[0] === "auth" && segments.length === 2 && method === "POST") {
    const action = segments[1];
    if (action === "register") {
      const body = await readJsonBody<Parameters<typeof registerUser>[0]>(req);
      const res = registerUser(body);
      return NextResponse.json(res, { status: 202 });
    }
    if (action === "login") {
      const body = await readJsonBody<{ email?: string; password?: string }>(req);
      const res = loginUser(body.email ?? "", body.password ?? "");
      return NextResponse.json(res);
    }
    if (action === "refresh") {
      const body = await readJsonBody<{ refreshToken?: string }>(req);
      const res = refreshUserSession(body.refreshToken ?? "");
      return NextResponse.json(res);
    }
    if (action === "logout") {
      const body = await readJsonBody<{ refreshToken?: string }>(req);
      logoutUserSession(body.refreshToken);
      return new NextResponse(null, { status: 204 });
    }
  }

  // -------------------------------------------------------------------------
  // Authenticated routes
  // -------------------------------------------------------------------------
  const user = verifyAccessToken(req.headers.get("authorization"));

  // /users/me
  if (method === "GET" && segments[0] === "users" && segments[1] === "me" && segments.length === 2) {
    return NextResponse.json({
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      role: user.role,
      status: user.status,
      timezone: user.timezone,
      createdAt: user.createdAt,
      updatedAt: user.updatedAt,
    });
  }

  // /markets & /markets/summary
  if (method === "GET" && segments[0] === "markets") {
    if (segments.length === 1) {
      return NextResponse.json([BIST_EXCHANGE]);
    }
    if (segments.length === 2 && segments[1] === "summary") {
      const movers: MarketMoverResponse[] = BIST_INSTRUMENTS.slice(0, 12)
        .map((inst) => {
          const q = getLatestQuote(inst.symbol);
          return {
            symbol: inst.symbol,
            name: inst.name,
            price: q.price,
            changeAmount: q.changeAmount,
            changePercent: q.changePercent,
            volume: q.volume,
            currency: q.currency,
            quotedAt: q.quotedAt,
            simulated: q.simulated,
          };
        })
        .sort((a, b) => Math.abs(b.changePercent) - Math.abs(a.changePercent));

      const body: MarketSummaryResponse = {
        exchange: BIST_EXCHANGE,
        activeInstrumentCount: BIST_INSTRUMENTS.length,
        simulated: true,
        summaryNote:
          "Borsa İstanbul öncelikli deterministik simüle piyasa özeti. Gösterilen fiyat ve değişimler karar destek önizleme amaçlıdır.",
        dataSource: getDataSourceMetadata(),
        freshness: getFreshnessMetadata(movers[0]?.quotedAt ?? new Date().toISOString()),
        movers,
      };
      return NextResponse.json(body);
    }
  }

  // /instruments/*
  if (segments[0] === "instruments") {
    if (method === "GET" && segments.length === 1) {
      const query = sp.get("query")?.trim().toLowerCase() ?? "";
      const page = parseIntParam(sp.get("page"), 0, 0, 1000);
      const size = parseIntParam(sp.get("size"), 20, 1, 100);
      const filtered = query
        ? BIST_INSTRUMENTS.filter(
            (i) =>
              i.symbol.toLowerCase().includes(query) ||
              i.name.toLowerCase().includes(query) ||
              (i.sector && i.sector.toLowerCase().includes(query))
          )
        : BIST_INSTRUMENTS;
      const start = page * size;
      const items = filtered.slice(start, start + size).map((inst) => ({
        id: inst.id,
        symbol: inst.symbol,
        name: inst.name,
        exchange: inst.exchange,
        sector: inst.sector,
        currency: inst.currency,
        type: inst.type,
        active: inst.active,
      }));
      const body: InstrumentPageResponse = {
        items,
        page,
        size,
        totalElements: filtered.length,
        totalPages: Math.max(1, Math.ceil(filtered.length / size)),
        dataSource: getDataSourceMetadata(),
      };
      return NextResponse.json(body);
    }

    const rawSymbol = decodeURIComponent(segments[1] ?? "");
    const symbol = normalizeSymbol(rawSymbol);
    const inst = findInstrument(symbol);
    if (!inst) {
      return apiError(404, "NOT_FOUND", `${symbol} enstrümanı bulunamadı.`, path);
    }

    if (method === "GET" && segments.length === 2) {
      const latestQuote = getLatestQuote(inst.symbol);
      const body: InstrumentDetailResponse = {
        id: inst.id,
        symbol: inst.symbol,
        name: inst.name,
        exchangeCode: BIST_EXCHANGE.code,
        exchangeName: BIST_EXCHANGE.name,
        exchangeTimezone: BIST_EXCHANGE.timezone,
        sector: inst.sector,
        currency: inst.currency,
        type: inst.type,
        active: inst.active,
        latestQuote,
        dataSource: getDataSourceMetadata(),
        freshness: getFreshnessMetadata(latestQuote.quotedAt),
      };
      return NextResponse.json(body);
    }

    const sub = segments[2];
    const interval = sp.get("interval") || "1d";
    const adjusted = parseBoolParam(sp.get("adjusted"), false);
    const from = sp.get("from") || undefined;
    const to = sp.get("to") || undefined;

    if (method === "GET" && segments.length === 3 && sub === "bars") {
      return NextResponse.json(
        generateHistoricalBars(inst.symbol, interval, from, to, adjusted)
      );
    }

    if (method === "GET" && segments.length === 3 && sub === "indicators") {
      const rawInds = sp.get("indicators");
      const allowed = new Set<IndicatorType>([
        "SMA",
        "EMA",
        "RSI",
        "MACD",
        "BOLLINGER",
        "STOCHASTIC",
        "ATR",
        "VOLUME_AVG",
      ]);
      const requested: IndicatorType[] = rawInds
        ? rawInds
            .split(",")
            .map((s) => s.trim().toUpperCase() as IndicatorType)
            .filter((s) => allowed.has(s))
        : [];
      return NextResponse.json(
        calculateTechnicalIndicators(inst.symbol, requested, interval, from, to, adjusted)
      );
    }

    if (method === "GET" && segments.length === 3 && sub === "analysis") {
      return NextResponse.json(
        calculateInstrumentAnalysis(inst.symbol, interval, from, to, adjusted)
      );
    }

    if (method === "GET" && segments.length === 3 && sub === "setups") {
      return NextResponse.json(
        calculateInstrumentSetup(inst.symbol, interval, from, to, adjusted)
      );
    }

    if (
      segments[2] === "ai-explanation" &&
      ((method === "GET" && segments.length === 3) ||
        (method === "POST" && segments.length === 4 && segments[3] === "regenerate"))
    ) {
      const scoreRunId = sp.get("scoreRunId") || undefined;
      return NextResponse.json(
        generateAiExplanation(inst.symbol, interval, adjusted, scoreRunId)
      );
    }
  }

  // /screener
  if (method === "GET" && segments.length === 1 && segments[0] === "screener") {
    const interval = sp.get("interval") || "1d";
    const scope = (sp.get("scope")?.toUpperCase() as UniverseScope) || "BIST30";
    const query = sp.get("query");
    const minScore = parseFloatParam(sp.get("minScore"));
    const maxScore = parseFloatParam(sp.get("maxScore"));
    const rsiMin = parseFloatParam(sp.get("rsiMin"));
    const rsiMax = parseFloatParam(sp.get("rsiMax"));
    const trend = sp.get("trend")?.toUpperCase() || undefined;
    const sortBy = sp.get("sortBy") || "score";
    const sortDir = sp.get("sortDir") === "asc" ? "asc" : "desc";
    const page = parseIntParam(sp.get("page"), 0, 0, 1000);
    const size = parseIntParam(sp.get("size"), 20, 1, 100);

    const universe = selectUniverse(scope, query);
    const evaluated: ScreenerItemResponse[] = universe.map((inst) => {
      const a = calculateInstrumentAnalysis(inst.symbol, interval);
      return {
        scoreRunId: a.scoreRunId,
        symbol: inst.symbol,
        name: inst.name,
        exchange: inst.exchange,
        sector: inst.sector,
        currency: inst.currency,
        status: a.status,
        finalScore: a.finalScore,
        regime: a.regime,
        trendDirection: a.trendDirection,
        latestClose: a.latestClose,
        latestRsi: a.latestRsi,
        simulated: true,
        staleData: a.staleData,
        headline: a.explanation.headline,
        calculatedAt: a.calculatedAt,
      };
    });

    const filtered = evaluated.filter((item) => {
      if (minScore !== undefined && (item.finalScore === null || item.finalScore < minScore)) {
        return false;
      }
      if (maxScore !== undefined && (item.finalScore === null || item.finalScore > maxScore)) {
        return false;
      }
      if (rsiMin !== undefined && (item.latestRsi === null || item.latestRsi < rsiMin)) {
        return false;
      }
      if (rsiMax !== undefined && (item.latestRsi === null || item.latestRsi > rsiMax)) {
        return false;
      }
      if (trend && item.trendDirection !== trend) {
        return false;
      }
      return true;
    });

    filtered.sort((a, b) => {
      let cmp = 0;
      if (sortBy === "symbol") {
        cmp = a.symbol.localeCompare(b.symbol);
      } else if (sortBy === "rsi") {
        cmp = (a.latestRsi ?? -1) - (b.latestRsi ?? -1);
      } else {
        cmp = (a.finalScore ?? -1) - (b.finalScore ?? -1);
      }
      return sortDir === "asc" ? cmp : -cmp;
    });

    const start = page * size;
    const paged = filtered.slice(start, start + size);
    const body: ScreenerPageResponse = {
      exchange: BIST_EXCHANGE.code,
      interval,
      adjusted: false,
      algorithmVersion: SCORING_ALGORITHM_VERSION,
      page,
      size,
      totalElements: filtered.length,
      totalPages: Math.max(1, Math.ceil(filtered.length / size)),
      disclaimer: STANDARD_DISCLAIMER,
      dataSource: getDataSourceMetadata(),
      freshness: getFreshnessMetadata(new Date().toISOString()),
      items: paged,
    };
    return NextResponse.json(body);
  }

  // /opportunities
  if (method === "GET" && segments.length === 1 && segments[0] === "opportunities") {
    const interval = sp.get("interval") || "1d";
    const scope = (sp.get("scope")?.toUpperCase() as UniverseScope) || "BIST30";
    const query = sp.get("query");
    const category = sp.get("category")?.toUpperCase() || undefined;
    const minMomentumScore = parseFloatParam(sp.get("minMomentumScore"));
    const sortBy = sp.get("sortBy") || "momentum";
    const sortDir = sp.get("sortDir") === "asc" ? "asc" : "desc";
    const page = parseIntParam(sp.get("page"), 0, 0, 1000);
    const size = parseIntParam(sp.get("size"), 15, 1, 100);

    const universe = selectUniverse(scope, query);
    const allItems: OpportunityItemResponse[] = universe.map((inst) =>
      toOpportunityItem(calculateInstrumentSetup(inst.symbol, interval))
    );

    const categoryCounts: Record<string, number> = {};
    for (const item of allItems) {
      categoryCounts[item.category] = (categoryCounts[item.category] ?? 0) + 1;
    }

    const filtered = allItems.filter((item) => {
      if (category && item.category !== category) return false;
      if (
        minMomentumScore !== undefined &&
        (item.momentumBreakoutScore === null || item.momentumBreakoutScore < minMomentumScore)
      ) {
        return false;
      }
      return true;
    });

    filtered.sort((a, b) => {
      let cmp = 0;
      if (sortBy === "symbol") cmp = a.symbol.localeCompare(b.symbol);
      else if (sortBy === "score") cmp = (a.technicalScore ?? -1) - (b.technicalScore ?? -1);
      else if (sortBy === "volumeRatio") cmp = (a.volumeRatio ?? -1) - (b.volumeRatio ?? -1);
      else if (sortBy === "ceilingDistance")
        cmp =
          (a.bistMargin?.distanceToCeilingPercent ?? 999) -
          (b.bistMargin?.distanceToCeilingPercent ?? 999);
      else if (sortBy === "riskReward")
        cmp = (a.riskReward?.riskRewardToR1 ?? -1) - (b.riskReward?.riskRewardToR1 ?? -1);
      else cmp = (a.momentumBreakoutScore ?? -1) - (b.momentumBreakoutScore ?? -1);
      return sortDir === "asc" ? cmp : -cmp;
    });

    const start = page * size;
    const body: OpportunitiesPageResponse = {
      exchange: BIST_EXCHANGE.code,
      interval,
      adjusted: false,
      setupEngineVersion: SETUP_ENGINE_VERSION,
      scoringAlgorithmVersion: SCORING_ALGORITHM_VERSION,
      page,
      size,
      totalElements: filtered.length,
      totalPages: Math.max(1, Math.ceil(filtered.length / size)),
      categoryCounts,
      disclaimer: STANDARD_DISCLAIMER,
      dataSource: getDataSourceMetadata(),
      freshness: getFreshnessMetadata(new Date().toISOString()),
      items: filtered.slice(start, start + size),
    };
    return NextResponse.json(body);
  }

  // /market-data/sync-status & /market-data/sync-batch
  if (segments[0] === "market-data") {
    const store = getStore();
    const interval = sp.get("interval") || "1d";

    if (method === "GET" && segments[1] === "sync-status") {
      const total = BIST_INSTRUMENTS.length;
      const synced = store.syncedSymbols.size;
      const body: UniverseSyncStatusResponse = {
        exchange: BIST_EXCHANGE.code,
        interval,
        providerId: PROVIDER_ID,
        totalActiveInstruments: total,
        syncedInstruments: synced,
        remainingInstruments: Math.max(0, total - synced),
      };
      return NextResponse.json(body);
    }

    if (method === "POST" && segments[1] === "sync-batch") {
      const batchSize = parseIntParam(sp.get("batchSize"), 20, 1, 50);
      const remaining = BIST_INSTRUMENTS.filter((i) => !store.syncedSymbols.has(i.symbol));
      const batch = remaining.slice(0, batchSize);
      const syncedSymbols: string[] = [];
      for (const inst of batch) {
        store.syncedSymbols.add(inst.symbol);
        syncedSymbols.push(inst.symbol);
      }
      const total = BIST_INSTRUMENTS.length;
      const synced = store.syncedSymbols.size;
      const body: BatchSyncResponse = {
        exchange: BIST_EXCHANGE.code,
        interval,
        providerId: PROVIDER_ID,
        totalActiveInstruments: total,
        syncedInstruments: synced,
        remainingInstruments: Math.max(0, total - synced),
        processedInBatch: batch.length,
        succeededInBatch: batch.length,
        failedInBatch: 0,
        syncedSymbols,
        failedSymbols: [],
      };
      return NextResponse.json(body);
    }
  }

  // /watchlists/*
  if (segments[0] === "watchlists") {
    if (segments.length === 1) {
      if (method === "GET") {
        const body: WatchlistCollectionResponse = {
          watchlists: listWatchlists(user.id),
          dataSource: getDataSourceMetadata(),
          freshness: getFreshnessMetadata(new Date().toISOString()),
        };
        return NextResponse.json(body);
      }
      if (method === "POST") {
        const payload = await readJsonBody<Parameters<typeof createWatchlist>[1]>(req);
        return NextResponse.json(createWatchlist(user.id, payload), { status: 201 });
      }
    }
    if (segments.length === 2) {
      const wlId = decodeURIComponent(segments[1]);
      if (method === "PATCH") {
        const payload = await readJsonBody<Parameters<typeof updateWatchlist>[2]>(req);
        return NextResponse.json(updateWatchlist(user.id, wlId, payload));
      }
      if (method === "DELETE") {
        deleteWatchlist(user.id, wlId);
        return new NextResponse(null, { status: 204 });
      }
    }
    if (segments.length === 3 && segments[2] === "items" && method === "POST") {
      const wlId = decodeURIComponent(segments[1]);
      const payload = await readJsonBody<{ symbol?: string; notes?: string }>(req);
      return NextResponse.json(
        addWatchlistItem(user.id, wlId, payload.symbol ?? "", payload.notes),
        { status: 201 }
      );
    }
    if (segments.length === 4 && segments[2] === "items" && method === "DELETE") {
      const wlId = decodeURIComponent(segments[1]);
      const itemId = decodeURIComponent(segments[3]);
      removeWatchlistItem(user.id, wlId, itemId);
      return new NextResponse(null, { status: 204 });
    }
  }

  // /portfolios/*
  if (segments[0] === "portfolios") {
    if (segments.length === 1) {
      if (method === "GET") {
        return NextResponse.json(listPortfolios(user.id));
      }
      if (method === "POST") {
        const payload = await readJsonBody<Parameters<typeof createPortfolio>[1]>(req);
        return NextResponse.json(createPortfolio(user.id, payload), { status: 201 });
      }
    }
    if (segments.length === 2 && method === "DELETE") {
      deletePortfolio(user.id, decodeURIComponent(segments[1]));
      return new NextResponse(null, { status: 204 });
    }
    if (segments.length === 3 && segments[2] === "transactions") {
      const pfId = decodeURIComponent(segments[1]);
      if (method === "GET") {
        return NextResponse.json(listPortfolioTransactions(user.id, pfId));
      }
      if (method === "POST") {
        const payload = await readJsonBody<Parameters<typeof addPortfolioTransaction>[2]>(req);
        return NextResponse.json(addPortfolioTransaction(user.id, pfId, payload), { status: 201 });
      }
    }
    if (segments.length === 4 && segments[2] === "transactions" && method === "DELETE") {
      removePortfolioTransaction(
        user.id,
        decodeURIComponent(segments[1]),
        decodeURIComponent(segments[3])
      );
      return new NextResponse(null, { status: 204 });
    }
    if (segments.length === 3 && segments[2] === "performance" && method === "GET") {
      return NextResponse.json(getPortfolioPerformance(user.id, decodeURIComponent(segments[1])));
    }
  }

  // /alerts/*
  if (segments[0] === "alerts") {
    if (segments.length === 1) {
      if (method === "GET") {
        return NextResponse.json({
          alerts: listAlerts(user.id),
          dataSource: getDataSourceMetadata(),
          freshness: getFreshnessMetadata(new Date().toISOString()),
        });
      }
      if (method === "POST") {
        const payload = await readJsonBody<Parameters<typeof createAlert>[1]>(req);
        return NextResponse.json(createAlert(user.id, payload), { status: 201 });
      }
    }
    if (segments.length === 2 && segments[1] === "evaluate" && method === "POST") {
      return NextResponse.json(evaluateUserAlerts(user.id));
    }
    if (segments.length === 2) {
      const alertId = decodeURIComponent(segments[1]);
      if (method === "PATCH") {
        const payload = await readJsonBody<Parameters<typeof updateAlert>[2]>(req);
        return NextResponse.json(updateAlert(user.id, alertId, payload));
      }
      if (method === "DELETE") {
        deleteAlert(user.id, alertId);
        return new NextResponse(null, { status: 204 });
      }
    }
  }

  // /notifications/*
  if (segments[0] === "notifications") {
    if (segments.length === 1 && method === "GET") {
      const unreadOnly = parseBoolParam(sp.get("unreadOnly"), false);
      const page = parseIntParam(sp.get("page"), 0, 0, 1000);
      const size = parseIntParam(sp.get("size"), 20, 1, 100);
      return NextResponse.json(listNotifications(user.id, unreadOnly, page, size));
    }
    if (segments.length === 2 && segments[1] === "read-all" && method === "POST") {
      return NextResponse.json(markAllNotificationsRead(user.id));
    }
    if (segments.length === 3 && segments[2] === "read" && method === "PATCH") {
      return NextResponse.json(markNotificationRead(user.id, decodeURIComponent(segments[1])));
    }
  }

  return apiError(404, "NOT_FOUND", `Bilinmeyen API uç noktası: ${method} ${path}`, path);
}

async function handle(
  req: NextRequest,
  context: { params: Promise<{ path: string[] }> }
): Promise<NextResponse> {
  const { path: segments } = await context.params;
  const fullPath = `/api/v1/${segments.join("/")}`;
  try {
    return await dispatch(req, segments);
  } catch (err) {
    if (err instanceof StoreError) {
      return apiError(err.status, err.code, err.message, fullPath, err.fieldErrors);
    }
    return apiError(
      500,
      "INTERNAL_SERVER_ERROR",
      "İstek işlenirken beklenmeyen bir sunucu hatası oluştu.",
      fullPath
    );
  }
}

export const GET = handle;
export const POST = handle;
export const PATCH = handle;
export const PUT = handle;
export const DELETE = handle;
