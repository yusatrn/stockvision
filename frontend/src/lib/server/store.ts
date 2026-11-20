import crypto from "node:crypto";
import type {
  AlertConditionType,
  AlertEvaluationRunResponse,
  AlertNotificationResponse,
  AlertRuleResponse,
  AlertStatus,
  AlertTriggerState,
  AuthTokenResponse,
  CreateAlertRequest,
  CreatePortfolioRequest,
  CreateTransactionRequest,
  CreateWatchlistRequest,
  EvaluatedRuleResult,
  FeeTaxPolicy,
  IndicatorType,
  PortfolioHoldingResponse,
  PortfolioPerformanceResponse,
  PortfolioSummaryResponse,
  PortfolioTransactionResponse,
  RegisterRequest,
  RegisterResponse,
  TransactionSide,
  UpdateAlertRequest,
  UpdateWatchlistRequest,
  UserProfileResponse,
  WatchlistItemResponse,
  WatchlistResponse,
} from "@/lib/api/types";
import { BIST_INSTRUMENTS, findInstrument, normalizeSymbol } from "./bist-catalog";
import {
  buildIndicatorSeries,
  generateHistoricalBars,
  getDataSourceMetadata,
  getFreshnessMetadata,
  getLatestQuote,
  roundTo,
  STANDARD_DISCLAIMER,
} from "./market-engine";

export class StoreError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors?: Array<{ field: string; message: string }>;

  constructor(
    status: number,
    code: string,
    message: string,
    fieldErrors?: Array<{ field: string; message: string }>
  ) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

interface StoredUser {
  id: string;
  email: string;
  normalizedEmail: string;
  passwordSalt: string;
  passwordHash: string;
  displayName: string | null;
  role: "USER" | "ADMIN";
  status: "ACTIVE" | "SUSPENDED" | "LOCKED";
  timezone: string;
  createdAt: string;
  updatedAt: string;
}

interface StoredRefreshToken {
  tokenHash: string;
  familyId: string;
  userId: string;
  expiresAtMs: number;
  revoked: boolean;
}

interface StoredWatchlistItem {
  id: string;
  instrumentId: string;
  symbol: string;
  notes: string | null;
  createdAt: string;
}

interface StoredWatchlist {
  id: string;
  userId: string;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
  items: StoredWatchlistItem[];
}

export interface StoredTransaction {
  id: string;
  instrumentId: string;
  symbol: string;
  instrumentName: string;
  side: TransactionSide;
  quantity: number;
  unitPrice: number;
  feeAmount: number;
  taxAmount: number;
  currency: string;
  executedAt: string;
  notes: string | null;
  createdAt: string;
}

interface StoredPortfolio {
  id: string;
  userId: string;
  name: string;
  description: string | null;
  baseCurrency: string;
  feeTaxPolicy: FeeTaxPolicy;
  createdAt: string;
  updatedAt: string;
  transactions: StoredTransaction[];
}

interface StoredAlertRule {
  id: string;
  userId: string;
  instrumentId: string;
  symbol: string;
  instrumentName: string;
  conditionType: AlertConditionType;
  indicatorType: IndicatorType | null;
  indicatorField: string | null;
  interval: string;
  thresholdValue: number;
  cooldownMinutes: number;
  status: AlertStatus;
  triggerState: AlertTriggerState;
  lastEvaluatedAt: string | null;
  lastTriggeredAt: string | null;
  lastObservedValue: number | null;
  note: string | null;
  createdAt: string;
  updatedAt: string;
}

interface StoredNotification extends AlertNotificationResponse {
  userId: string;
}

interface StoreState {
  tokenSecret: string;
  usersByEmail: Map<string, StoredUser>;
  usersById: Map<string, StoredUser>;
  refreshTokens: Map<string, StoredRefreshToken>;
  watchlists: Map<string, StoredWatchlist>;
  portfolios: Map<string, StoredPortfolio>;
  alerts: Map<string, StoredAlertRule>;
  notifications: StoredNotification[];
  syncedSymbols: Set<string>;
}

const DEMO_USER_ID = "00000000-0000-0000-0000-000000000001";
export const DEMO_EMAIL = "demo@stockvision.ai";
export const DEMO_PASSWORD = "Demo123456";

function hashPassword(password: string, salt: string): string {
  return crypto.scryptSync(password, salt, 32).toString("hex");
}

function sha256(value: string): string {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function toUserProfile(u: StoredUser): UserProfileResponse {
  return {
    id: u.id,
    email: u.email,
    displayName: u.displayName,
    role: u.role,
    status: u.status,
    timezone: u.timezone,
    createdAt: u.createdAt,
    updatedAt: u.updatedAt,
  };
}

// ============================================================================
// Pure Portfolio Ledger Calculator (Weighted Average Cost + Short-Sale Guard)
// ============================================================================

export interface LedgerPositionState {
  instrumentId: string;
  symbol: string;
  name: string;
  openQuantity: number;
  costBasis: number;
  realizedPnl: number;
  totalFees: number;
  totalTaxes: number;
}

export function calculatePortfolioLedger(
  transactions: StoredTransaction[],
  feeTaxPolicy: FeeTaxPolicy
): {
  positions: Map<string, LedgerPositionState>;
  totalRealizedPnl: number;
  totalFees: number;
  totalTaxes: number;
  closedPositionsCount: number;
} {
  const ordered = [...transactions].sort((a, b) => {
    const dt = Date.parse(a.executedAt) - Date.parse(b.executedAt);
    return dt !== 0 ? dt : Date.parse(a.createdAt) - Date.parse(b.createdAt);
  });

  const positions = new Map<string, LedgerPositionState>();
  let totalRealizedPnl = 0;
  let totalFees = 0;
  let totalTaxes = 0;

  for (const tx of ordered) {
    const fee = feeTaxPolicy === "EXPLICIT_PER_TXN" ? Math.max(0, tx.feeAmount) : 0;
    const tax = feeTaxPolicy === "EXPLICIT_PER_TXN" ? Math.max(0, tx.taxAmount) : 0;
    const key = tx.symbol;
    let pos = positions.get(key);
    if (!pos) {
      pos = {
        instrumentId: tx.instrumentId,
        symbol: tx.symbol,
        name: tx.instrumentName,
        openQuantity: 0,
        costBasis: 0,
        realizedPnl: 0,
        totalFees: 0,
        totalTaxes: 0,
      };
      positions.set(key, pos);
    }

    if (tx.side === "BUY") {
      pos.openQuantity = roundTo(pos.openQuantity + tx.quantity, 4);
      pos.costBasis = roundTo(pos.costBasis + tx.quantity * tx.unitPrice + fee + tax, 2);
      pos.totalFees = roundTo(pos.totalFees + fee, 2);
      pos.totalTaxes = roundTo(pos.totalTaxes + tax, 2);
    } else {
      if (tx.quantity > pos.openQuantity + 1e-6) {
        throw new StoreError(
          409,
          "CONFLICT",
          `${tx.symbol} için satış miktarı (${tx.quantity}) açık pozisyonu (${pos.openQuantity}) aşıyor; açığa satış desteklenmez.`
        );
      }
      const avgCost = pos.openQuantity > 0 ? pos.costBasis / pos.openQuantity : 0;
      const soldCost = avgCost * tx.quantity;
      const proceeds = tx.quantity * tx.unitPrice - fee - tax;
      const realized = roundTo(proceeds - soldCost, 2);

      pos.openQuantity = roundTo(Math.max(0, pos.openQuantity - tx.quantity), 4);
      pos.costBasis =
        pos.openQuantity <= 1e-6 ? 0 : roundTo(Math.max(0, pos.costBasis - soldCost), 2);
      pos.realizedPnl = roundTo(pos.realizedPnl + realized, 2);
      pos.totalFees = roundTo(pos.totalFees + fee, 2);
      pos.totalTaxes = roundTo(pos.totalTaxes + tax, 2);
      totalRealizedPnl = roundTo(totalRealizedPnl + realized, 2);
    }

    totalFees = roundTo(totalFees + fee, 2);
    totalTaxes = roundTo(totalTaxes + tax, 2);
  }

  let closedPositionsCount = 0;
  for (const pos of positions.values()) {
    if (pos.openQuantity <= 1e-6) {
      closedPositionsCount += 1;
    }
  }

  return { positions, totalRealizedPnl, totalFees, totalTaxes, closedPositionsCount };
}

function createInitialStore(): StoreState {
  const nowIso = new Date().toISOString();
  const salt = "stockvision-demo-salt-v1";
  const demoUser: StoredUser = {
    id: DEMO_USER_ID,
    email: DEMO_EMAIL,
    normalizedEmail: DEMO_EMAIL.toLowerCase(),
    passwordSalt: salt,
    passwordHash: hashPassword(DEMO_PASSWORD, salt),
    displayName: "BIST Analisti (Demo)",
    role: "ADMIN",
    status: "ACTIVE",
    timezone: "Europe/Istanbul",
    createdAt: nowIso,
    updatedAt: nowIso,
  };

  const state: StoreState = {
    tokenSecret: process.env.JWT_SECRET || crypto.randomBytes(32).toString("hex"),
    usersByEmail: new Map([[demoUser.normalizedEmail, demoUser]]),
    usersById: new Map([[demoUser.id, demoUser]]),
    refreshTokens: new Map(),
    watchlists: new Map(),
    portfolios: new Map(),
    alerts: new Map(),
    notifications: [],
    syncedSymbols: new Set(BIST_INSTRUMENTS.slice(0, 30).map((i) => i.symbol)),
  };

  // Seed default watchlist for demo user
  const wlId = "wl-demo-bist30";
  const seedWatchSymbols = ["THYAO", "ASELS", "GARAN", "TUPRS", "BIMAS"];
  state.watchlists.set(wlId, {
    id: wlId,
    userId: DEMO_USER_ID,
    name: "BIST30 Takip",
    description: "Borsa İstanbul önde gelen likit hisseler izleme listesi.",
    createdAt: nowIso,
    updatedAt: nowIso,
    items: seedWatchSymbols.reduce<StoredWatchlistItem[]>((acc, sym, idx) => {
      const inst = findInstrument(sym);
      if (inst) {
        acc.push({
          id: `wli-demo-${idx + 1}`,
          instrumentId: inst.id,
          symbol: inst.symbol,
          notes: "BIST30 örnek takip",
          createdAt: nowIso,
        });
      }
      return acc;
    }, []),
  });

  // Seed virtual portfolio for demo user
  const pfId = "pf-demo-main";
  const thyao = findInstrument("THYAO")!;
  const asels = findInstrument("ASELS")!;
  const thyaoQuote = getLatestQuote("THYAO");
  const aselsQuote = getLatestQuote("ASELS");
  const tenDaysAgo = new Date(Date.now() - 10 * 86400_000).toISOString();
  const fiveDaysAgo = new Date(Date.now() - 5 * 86400_000).toISOString();

  state.portfolios.set(pfId, {
    id: pfId,
    userId: DEMO_USER_ID,
    name: "BIST Sanal Portföy",
    description: "Karar destek amaçlı örnek sanal işlem defteri.",
    baseCurrency: "TRY",
    feeTaxPolicy: "EXCLUDED_MVP",
    createdAt: tenDaysAgo,
    updatedAt: nowIso,
    transactions: [
      {
        id: "tx-demo-1",
        instrumentId: thyao.id,
        symbol: thyao.symbol,
        instrumentName: thyao.name,
        side: "BUY",
        quantity: 100,
        unitPrice: roundTo(thyaoQuote.price * 0.96, 2),
        feeAmount: 0,
        taxAmount: 0,
        currency: "TRY",
        executedAt: tenDaysAgo,
        notes: "Örnek ilk alım kaydı",
        createdAt: tenDaysAgo,
      },
      {
        id: "tx-demo-2",
        instrumentId: asels.id,
        symbol: asels.symbol,
        instrumentName: asels.name,
        side: "BUY",
        quantity: 150,
        unitPrice: roundTo(aselsQuote.price * 0.98, 2),
        feeAmount: 0,
        taxAmount: 0,
        currency: "TRY",
        executedAt: fiveDaysAgo,
        notes: "Savunma sektörü örnek kayıt",
        createdAt: fiveDaysAgo,
      },
    ],
  });

  // Seed sample alerts for demo user
  const alert1Id = "al-demo-1";
  state.alerts.set(alert1Id, {
    id: alert1Id,
    userId: DEMO_USER_ID,
    instrumentId: thyao.id,
    symbol: thyao.symbol,
    instrumentName: thyao.name,
    conditionType: "PRICE_ABOVE",
    indicatorType: null,
    indicatorField: null,
    interval: "1d",
    thresholdValue: roundTo(thyaoQuote.price * 0.95, 2),
    cooldownMinutes: 60,
    status: "ACTIVE",
    triggerState: "ARMED",
    lastEvaluatedAt: null,
    lastTriggeredAt: null,
    lastObservedValue: thyaoQuote.price,
    note: "THYAO fiyat eşiği takip alarmı",
    createdAt: nowIso,
    updatedAt: nowIso,
  });

  const alert2Id = "al-demo-2";
  state.alerts.set(alert2Id, {
    id: alert2Id,
    userId: DEMO_USER_ID,
    instrumentId: asels.id,
    symbol: asels.symbol,
    instrumentName: asels.name,
    conditionType: "INDICATOR_ABOVE",
    indicatorType: "RSI",
    indicatorField: "value",
    interval: "1d",
    thresholdValue: 65,
    cooldownMinutes: 120,
    status: "ACTIVE",
    triggerState: "ARMED",
    lastEvaluatedAt: null,
    lastTriggeredAt: null,
    lastObservedValue: null,
    note: "ASELS RSI(14) 65 üzeri izleme",
    createdAt: nowIso,
    updatedAt: nowIso,
  });

  return state;
}

const globalForStore = globalThis as typeof globalThis & {
  __stockvisionStore?: StoreState;
};

export function getStore(): StoreState {
  if (!globalForStore.__stockvisionStore) {
    globalForStore.__stockvisionStore = createInitialStore();
  }
  return globalForStore.__stockvisionStore;
}

// ============================================================================
// Auth Helpers
// ============================================================================

function signAccessToken(user: StoredUser, secret: string): {
  accessToken: string;
  expiresInSeconds: number;
  expiresAt: string;
} {
  const expiresInSeconds = 3600 * 6;
  const exp = Math.floor(Date.now() / 1000) + expiresInSeconds;
  const header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url");
  const payload = Buffer.from(
    JSON.stringify({ sub: user.id, email: user.email, role: user.role, exp })
  ).toString("base64url");
  const signature = crypto
    .createHmac("sha256", secret)
    .update(`${header}.${payload}`)
    .digest("base64url");
  return {
    accessToken: `${header}.${payload}.${signature}`,
    expiresInSeconds,
    expiresAt: new Date(exp * 1000).toISOString(),
  };
}

export function verifyAccessToken(authHeader: string | null): StoredUser {
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    throw new StoreError(401, "UNAUTHORIZED", "Kimlik doğrulama belirteci gerekli.");
  }
  const token = authHeader.slice(7).trim();
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new StoreError(401, "UNAUTHORIZED", "Geçersiz erişim belirteci.");
  }
  const store = getStore();
  const [header, payload, sig] = parts;
  const expectedSig = crypto
    .createHmac("sha256", store.tokenSecret)
    .update(`${header}.${payload}`)
    .digest("base64url");
  if (sig !== expectedSig) {
    throw new StoreError(401, "UNAUTHORIZED", "Erişim belirteci doğrulanamadı.");
  }
  let parsed: { sub?: string; exp?: number };
  try {
    parsed = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
  } catch {
    throw new StoreError(401, "UNAUTHORIZED", "Bozuk erişim belirteci.");
  }
  if (!parsed.sub || !parsed.exp || parsed.exp * 1000 <= Date.now()) {
    throw new StoreError(401, "UNAUTHORIZED", "Oturum süresi doldu.");
  }
  const user = store.usersById.get(parsed.sub);
  if (!user || user.status !== "ACTIVE") {
    throw new StoreError(401, "UNAUTHORIZED", "Kullanıcı aktif değil.");
  }
  return user;
}

function issueAuthBundle(user: StoredUser, familyId?: string): AuthTokenResponse {
  const store = getStore();
  const { accessToken, expiresInSeconds, expiresAt } = signAccessToken(user, store.tokenSecret);
  const rawRefresh = crypto.randomBytes(32).toString("base64url");
  const refreshExpiresMs = Date.now() + 7 * 24 * 3600 * 1000;
  store.refreshTokens.set(sha256(rawRefresh), {
    tokenHash: sha256(rawRefresh),
    familyId: familyId ?? crypto.randomUUID(),
    userId: user.id,
    expiresAtMs: refreshExpiresMs,
    revoked: false,
  });
  return {
    accessToken,
    tokenType: "Bearer",
    expiresInSeconds,
    expiresAt,
    user: toUserProfile(user),
    refreshToken: rawRefresh,
    refreshExpiresAt: new Date(refreshExpiresMs).toISOString(),
  };
}

export function registerUser(req: RegisterRequest): RegisterResponse {
  const email = (req.email ?? "").trim();
  const password = req.password ?? "";
  const fieldErrors: Array<{ field: string; message: string }> = [];

  if (!email || !email.includes("@")) {
    fieldErrors.push({ field: "email", message: "Geçerli bir e-posta adresi girin." });
  }
  if (password.length < 10 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
    fieldErrors.push({
      field: "password",
      message: "Parola en az 10 karakter olmalı, harf ve rakam içermelidir.",
    });
  }
  if (fieldErrors.length > 0) {
    throw new StoreError(400, "VALIDATION_ERROR", "Kayıt bilgileri geçersiz.", fieldErrors);
  }

  const store = getStore();
  const normEmail = email.toLowerCase();
  if (!store.usersByEmail.has(normEmail)) {
    const nowIso = new Date().toISOString();
    const salt = crypto.randomBytes(16).toString("hex");
    const newUser: StoredUser = {
      id: crypto.randomUUID(),
      email,
      normalizedEmail: normEmail,
      passwordSalt: salt,
      passwordHash: hashPassword(password, salt),
      displayName: req.displayName?.trim() || null,
      role: "USER",
      status: "ACTIVE",
      timezone: req.timezone?.trim() || "Europe/Istanbul",
      createdAt: nowIso,
      updatedAt: nowIso,
    };
    store.usersByEmail.set(normEmail, newUser);
    store.usersById.set(newUser.id, newUser);
  }

  return {
    status: "ACCEPTED",
    message: "Kayıt talebiniz alındı. Bilgilerinizle şimdi giriş yapabilirsiniz.",
  };
}

export function loginUser(email: string, password: string): AuthTokenResponse {
  const store = getStore();
  const user = store.usersByEmail.get(email.trim().toLowerCase());
  if (!user) {
    throw new StoreError(401, "INVALID_CREDENTIALS", "E-posta adresi veya parola hatalı.");
  }
  const candidateHash = hashPassword(password, user.passwordSalt);
  if (candidateHash !== user.passwordHash) {
    throw new StoreError(401, "INVALID_CREDENTIALS", "E-posta adresi veya parola hatalı.");
  }
  return issueAuthBundle(user);
}

export function refreshUserSession(refreshToken: string): AuthTokenResponse {
  const store = getStore();
  const tokenHash = sha256(refreshToken);
  const record = store.refreshTokens.get(tokenHash);
  if (!record || record.expiresAtMs <= Date.now()) {
    throw new StoreError(401, "UNAUTHORIZED", "Refresh token geçersiz veya süresi dolmuş.");
  }
  if (record.revoked) {
    // Replay detection: revoke whole family
    for (const r of store.refreshTokens.values()) {
      if (r.familyId === record.familyId) r.revoked = true;
    }
    throw new StoreError(401, "UNAUTHORIZED", "Tekrar kullanılan refresh token tespit edildi; oturum iptal edildi.");
  }
  record.revoked = true;
  const user = store.usersById.get(record.userId);
  if (!user) {
    throw new StoreError(401, "UNAUTHORIZED", "Kullanıcı bulunamadı.");
  }
  return issueAuthBundle(user, record.familyId);
}

export function logoutUserSession(refreshToken?: string | null): void {
  if (!refreshToken) return;
  const store = getStore();
  const record = store.refreshTokens.get(sha256(refreshToken));
  if (record) record.revoked = true;
}

// ============================================================================
// Watchlist Operations
// ============================================================================

function toWatchlistResponse(wl: StoredWatchlist): WatchlistResponse {
  const items: WatchlistItemResponse[] = wl.items.map((item) => {
    const inst = findInstrument(item.symbol);
    const quote = getLatestQuote(item.symbol);
    return {
      id: item.id,
      instrumentId: item.instrumentId,
      symbol: item.symbol,
      name: inst?.name ?? item.symbol,
      exchange: inst?.exchange ?? "BIST",
      sector: inst?.sector ?? null,
      currency: inst?.currency ?? "TRY",
      notes: item.notes,
      createdAt: item.createdAt,
      latestQuote: {
        price: quote.price,
        changeAmount: quote.changeAmount,
        changePercent: quote.changePercent,
        volume: quote.volume,
        currency: quote.currency,
        quotedAt: quote.quotedAt,
        simulated: quote.simulated,
      },
    };
  });

  return {
    id: wl.id,
    name: wl.name,
    description: wl.description,
    createdAt: wl.createdAt,
    updatedAt: wl.updatedAt,
    items,
  };
}

export function listWatchlists(userId: string): WatchlistResponse[] {
  const store = getStore();
  return Array.from(store.watchlists.values())
    .filter((wl) => wl.userId === userId)
    .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt))
    .map(toWatchlistResponse);
}

export function createWatchlist(userId: string, req: CreateWatchlistRequest): WatchlistResponse {
  const name = (req.name ?? "").trim();
  if (!name) {
    throw new StoreError(400, "VALIDATION_ERROR", "İzleme listesi adı zorunludur.");
  }
  const store = getStore();
  const duplicate = Array.from(store.watchlists.values()).some(
    (wl) => wl.userId === userId && wl.name.toLowerCase() === name.toLowerCase()
  );
  if (duplicate) {
    throw new StoreError(409, "CONFLICT", `"${name}" adlı bir izleme listeniz zaten mevcut.`);
  }
  const nowIso = new Date().toISOString();
  const wl: StoredWatchlist = {
    id: crypto.randomUUID(),
    userId,
    name,
    description: req.description?.trim() || null,
    createdAt: nowIso,
    updatedAt: nowIso,
    items: [],
  };
  store.watchlists.set(wl.id, wl);
  return toWatchlistResponse(wl);
}

export function updateWatchlist(
  userId: string,
  id: string,
  req: UpdateWatchlistRequest
): WatchlistResponse {
  const store = getStore();
  const wl = store.watchlists.get(id);
  if (!wl || wl.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "İzleme listesi bulunamadı.");
  }
  const name = (req.name ?? "").trim();
  if (!name) {
    throw new StoreError(400, "VALIDATION_ERROR", "İzleme listesi adı boş olamaz.");
  }
  const dup = Array.from(store.watchlists.values()).some(
    (other) =>
      other.userId === userId &&
      other.id !== id &&
      other.name.toLowerCase() === name.toLowerCase()
  );
  if (dup) {
    throw new StoreError(409, "CONFLICT", `"${name}" adlı başka bir izleme listeniz var.`);
  }
  wl.name = name;
  wl.description = req.description?.trim() || null;
  wl.updatedAt = new Date().toISOString();
  return toWatchlistResponse(wl);
}

export function deleteWatchlist(userId: string, id: string): void {
  const store = getStore();
  const wl = store.watchlists.get(id);
  if (!wl || wl.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "İzleme listesi bulunamadı.");
  }
  store.watchlists.delete(id);
}

export function addWatchlistItem(
  userId: string,
  watchlistId: string,
  symbolRaw: string,
  notes?: string
): WatchlistItemResponse {
  const store = getStore();
  const wl = store.watchlists.get(watchlistId);
  if (!wl || wl.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "İzleme listesi bulunamadı.");
  }
  const symbol = normalizeSymbol(symbolRaw ?? "");
  const inst = findInstrument(symbol);
  if (!inst) {
    throw new StoreError(404, "NOT_FOUND", `${symbol || "Belirtilen"} sembolü katalogda bulunamadı.`);
  }
  if (wl.items.some((i) => i.symbol === inst.symbol)) {
    throw new StoreError(409, "CONFLICT", `${inst.symbol} bu izleme listesinde zaten bulunuyor.`);
  }
  const nowIso = new Date().toISOString();
  const item: StoredWatchlistItem = {
    id: crypto.randomUUID(),
    instrumentId: inst.id,
    symbol: inst.symbol,
    notes: notes?.trim() || null,
    createdAt: nowIso,
  };
  wl.items.push(item);
  wl.updatedAt = nowIso;
  const quote = getLatestQuote(inst.symbol);
  return {
    id: item.id,
    instrumentId: item.instrumentId,
    symbol: inst.symbol,
    name: inst.name,
    exchange: inst.exchange,
    sector: inst.sector,
    currency: inst.currency,
    notes: item.notes,
    createdAt: item.createdAt,
    latestQuote: {
      price: quote.price,
      changeAmount: quote.changeAmount,
      changePercent: quote.changePercent,
      volume: quote.volume,
      currency: quote.currency,
      quotedAt: quote.quotedAt,
      simulated: quote.simulated,
    },
  };
}

export function removeWatchlistItem(userId: string, watchlistId: string, itemId: string): void {
  const store = getStore();
  const wl = store.watchlists.get(watchlistId);
  if (!wl || wl.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "İzleme listesi bulunamadı.");
  }
  const idx = wl.items.findIndex((i) => i.id === itemId);
  if (idx === -1) {
    throw new StoreError(404, "NOT_FOUND", "İzleme listesi öğesi bulunamadı.");
  }
  wl.items.splice(idx, 1);
  wl.updatedAt = new Date().toISOString();
}

// ============================================================================
// Portfolio Operations
// ============================================================================

function toPortfolioSummary(pf: StoredPortfolio): PortfolioSummaryResponse {
  const ledger = calculatePortfolioLedger(pf.transactions, pf.feeTaxPolicy);
  let openCount = 0;
  for (const pos of ledger.positions.values()) {
    if (pos.openQuantity > 1e-6) openCount++;
  }
  return {
    id: pf.id,
    name: pf.name,
    description: pf.description,
    baseCurrency: pf.baseCurrency,
    feeTaxPolicy: pf.feeTaxPolicy,
    openPositionsCount: openCount,
    transactionCount: pf.transactions.length,
    createdAt: pf.createdAt,
    updatedAt: pf.updatedAt,
  };
}

export function listPortfolios(userId: string): PortfolioSummaryResponse[] {
  const store = getStore();
  return Array.from(store.portfolios.values())
    .filter((pf) => pf.userId === userId)
    .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt))
    .map(toPortfolioSummary);
}

export function createPortfolio(
  userId: string,
  req: CreatePortfolioRequest
): PortfolioSummaryResponse {
  const name = (req.name ?? "").trim();
  if (!name) {
    throw new StoreError(400, "VALIDATION_ERROR", "Portföy adı zorunludur.");
  }
  const store = getStore();
  const nowIso = new Date().toISOString();
  const pf: StoredPortfolio = {
    id: crypto.randomUUID(),
    userId,
    name,
    description: req.description?.trim() || null,
    baseCurrency: (req.baseCurrency || "TRY").toUpperCase(),
    feeTaxPolicy: req.feeTaxPolicy === "EXPLICIT_PER_TXN" ? "EXPLICIT_PER_TXN" : "EXCLUDED_MVP",
    createdAt: nowIso,
    updatedAt: nowIso,
    transactions: [],
  };
  store.portfolios.set(pf.id, pf);
  return toPortfolioSummary(pf);
}

export function deletePortfolio(userId: string, portfolioId: string): void {
  const store = getStore();
  const pf = store.portfolios.get(portfolioId);
  if (!pf || pf.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "Portföy bulunamadı.");
  }
  store.portfolios.delete(portfolioId);
}

function toTransactionResponse(tx: StoredTransaction): PortfolioTransactionResponse {
  return {
    id: tx.id,
    instrumentId: tx.instrumentId,
    symbol: tx.symbol,
    instrumentName: tx.instrumentName,
    side: tx.side,
    quantity: tx.quantity,
    unitPrice: tx.unitPrice,
    grossAmount: roundTo(tx.quantity * tx.unitPrice, 2),
    feeAmount: tx.feeAmount,
    taxAmount: tx.taxAmount,
    currency: tx.currency,
    executedAt: tx.executedAt,
    notes: tx.notes,
    createdAt: tx.createdAt,
  };
}

export function listPortfolioTransactions(
  userId: string,
  portfolioId: string
): PortfolioTransactionResponse[] {
  const store = getStore();
  const pf = store.portfolios.get(portfolioId);
  if (!pf || pf.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "Portföy bulunamadı.");
  }
  return [...pf.transactions]
    .sort((a, b) => Date.parse(b.executedAt) - Date.parse(a.executedAt))
    .map(toTransactionResponse);
}

export function addPortfolioTransaction(
  userId: string,
  portfolioId: string,
  req: CreateTransactionRequest
): PortfolioTransactionResponse {
  const store = getStore();
  const pf = store.portfolios.get(portfolioId);
  if (!pf || pf.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "Portföy bulunamadı.");
  }
  const symbol = normalizeSymbol(req.symbol ?? "");
  const inst = findInstrument(symbol);
  if (!inst) {
    throw new StoreError(404, "NOT_FOUND", `${symbol || "Belirtilen"} sembolü bulunamadı.`);
  }
  if (!Number.isFinite(req.quantity) || req.quantity <= 0) {
    throw new StoreError(400, "VALIDATION_ERROR", "İşlem miktarı sıfırdan büyük olmalıdır.");
  }
  if (!Number.isFinite(req.unitPrice) || req.unitPrice <= 0) {
    throw new StoreError(400, "VALIDATION_ERROR", "Birim fiyat sıfırdan büyük olmalıdır.");
  }
  const side: TransactionSide = req.side === "SELL" ? "SELL" : "BUY";
  const nowIso = new Date().toISOString();
  const newTx: StoredTransaction = {
    id: crypto.randomUUID(),
    instrumentId: inst.id,
    symbol: inst.symbol,
    instrumentName: inst.name,
    side,
    quantity: roundTo(req.quantity, 4),
    unitPrice: roundTo(req.unitPrice, 2),
    feeAmount: pf.feeTaxPolicy === "EXPLICIT_PER_TXN" ? roundTo(Math.max(0, req.feeAmount ?? 0), 2) : 0,
    taxAmount: pf.feeTaxPolicy === "EXPLICIT_PER_TXN" ? roundTo(Math.max(0, req.taxAmount ?? 0), 2) : 0,
    currency: inst.currency,
    executedAt: req.executedAt || nowIso,
    notes: req.notes?.trim() || null,
    createdAt: nowIso,
  };

  // Validate ledger integrity (no short-selling)
  calculatePortfolioLedger([...pf.transactions, newTx], pf.feeTaxPolicy);

  pf.transactions.push(newTx);
  pf.updatedAt = nowIso;
  return toTransactionResponse(newTx);
}

export function removePortfolioTransaction(
  userId: string,
  portfolioId: string,
  txId: string
): void {
  const store = getStore();
  const pf = store.portfolios.get(portfolioId);
  if (!pf || pf.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "Portföy bulunamadı.");
  }
  const remaining = pf.transactions.filter((t) => t.id !== txId);
  if (remaining.length === pf.transactions.length) {
    throw new StoreError(404, "NOT_FOUND", "İşlem kaydı bulunamadı.");
  }
  // Ensure removing a BUY does not invalidate a subsequent SELL
  calculatePortfolioLedger(remaining, pf.feeTaxPolicy);
  pf.transactions = remaining;
  pf.updatedAt = new Date().toISOString();
}

export function getPortfolioPerformance(
  userId: string,
  portfolioId: string
): PortfolioPerformanceResponse {
  const store = getStore();
  const pf = store.portfolios.get(portfolioId);
  if (!pf || pf.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "Portföy bulunamadı.");
  }
  const ledger = calculatePortfolioLedger(pf.transactions, pf.feeTaxPolicy);
  const holdings: PortfolioHoldingResponse[] = [];
  let totalCostBasis = 0;
  let totalMarketValue = 0;
  let totalUnrealizedPnl = 0;
  let latestQuotedAt: string | null = null;

  for (const pos of ledger.positions.values()) {
    if (pos.openQuantity <= 1e-6) continue;
    const inst = findInstrument(pos.symbol);
    const quote = getLatestQuote(pos.symbol);
    const averageCost = roundTo(pos.costBasis / pos.openQuantity, 2);
    const marketValue = roundTo(pos.openQuantity * quote.price, 2);
    const unrealizedPnl = roundTo(marketValue - pos.costBasis, 2);
    const unrealizedPnlPercent =
      pos.costBasis > 0 ? roundTo((unrealizedPnl / pos.costBasis) * 100, 2) : 0;

    totalCostBasis = roundTo(totalCostBasis + pos.costBasis, 2);
    totalMarketValue = roundTo(totalMarketValue + marketValue, 2);
    totalUnrealizedPnl = roundTo(totalUnrealizedPnl + unrealizedPnl, 2);
    if (!latestQuotedAt || quote.quotedAt > latestQuotedAt) {
      latestQuotedAt = quote.quotedAt;
    }

    holdings.push({
      instrumentId: pos.instrumentId,
      symbol: pos.symbol,
      name: pos.name,
      exchange: inst?.exchange ?? "BIST",
      currency: inst?.currency ?? pf.baseCurrency,
      openQuantity: pos.openQuantity,
      averageCost,
      costBasis: pos.costBasis,
      latestPrice: quote.price,
      quotedAt: quote.quotedAt,
      simulated: quote.simulated,
      marketValue,
      unrealizedPnl,
      unrealizedPnlPercent,
      realizedPnl: pos.realizedPnl,
      totalFees: pos.totalFees,
      totalTaxes: pos.totalTaxes,
    });
  }

  const isEmpty = pf.transactions.length === 0;
  const totalUnrealizedPnlPercent =
    totalCostBasis > 0 ? roundTo((totalUnrealizedPnl / totalCostBasis) * 100, 2) : null;
  const totalNetPnl = isEmpty
    ? 0
    : roundTo(totalUnrealizedPnl + ledger.totalRealizedPnl, 2);

  return {
    portfolioId: pf.id,
    name: pf.name,
    description: pf.description,
    baseCurrency: pf.baseCurrency,
    feeTaxPolicy: pf.feeTaxPolicy,
    feeTaxPolicyNote:
      pf.feeTaxPolicy === "EXPLICIT_PER_TXN"
        ? "Komisyon ve vergi tutarları her işlem kaydından açıkça düşülerek maliyet/P&L hesabına dahil edilir."
        : "MVP varsayılanı: aracı kurum komisyonu ve vergi 0 kabul edilir.",
    valuationStatus: isEmpty ? "EMPTY" : "READY",
    totalCostBasis,
    totalMarketValue: isEmpty ? 0 : totalMarketValue,
    totalUnrealizedPnl: isEmpty ? 0 : totalUnrealizedPnl,
    totalUnrealizedPnlPercent,
    totalRealizedPnl: ledger.totalRealizedPnl,
    totalNetPnl,
    totalFees: ledger.totalFees,
    totalTaxes: ledger.totalTaxes,
    openPositionsCount: holdings.length,
    closedPositionsCount: ledger.closedPositionsCount,
    transactionCount: pf.transactions.length,
    holdings,
    calculatedAt: new Date().toISOString(),
    dataSource: getDataSourceMetadata(),
    freshness: getFreshnessMetadata(latestQuotedAt ?? new Date().toISOString()),
    disclaimer: STANDARD_DISCLAIMER,
  };
}

// ============================================================================
// Alerts & Notifications Operations
// ============================================================================

function toAlertRuleResponse(a: StoredAlertRule): AlertRuleResponse {
  return {
    id: a.id,
    instrumentId: a.instrumentId,
    symbol: a.symbol,
    instrumentName: a.instrumentName,
    conditionType: a.conditionType,
    indicatorType: a.indicatorType,
    indicatorField: a.indicatorField,
    interval: a.interval,
    thresholdValue: a.thresholdValue,
    cooldownMinutes: a.cooldownMinutes,
    status: a.status,
    triggerState: a.triggerState,
    lastEvaluatedAt: a.lastEvaluatedAt,
    lastTriggeredAt: a.lastTriggeredAt,
    lastObservedValue: a.lastObservedValue,
    note: a.note,
    createdAt: a.createdAt,
    updatedAt: a.updatedAt,
  };
}

export function listAlerts(userId: string): AlertRuleResponse[] {
  const store = getStore();
  return Array.from(store.alerts.values())
    .filter((a) => a.userId === userId)
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
    .map(toAlertRuleResponse);
}

export function createAlert(userId: string, req: CreateAlertRequest): AlertRuleResponse {
  const symbol = normalizeSymbol(req.symbol ?? "");
  const inst = findInstrument(symbol);
  if (!inst) {
    throw new StoreError(404, "NOT_FOUND", `${symbol || "Belirtilen"} sembolü bulunamadı.`);
  }
  if (!Number.isFinite(req.thresholdValue)) {
    throw new StoreError(400, "VALIDATION_ERROR", "Geçerli bir eşik değeri girin.");
  }
  const isIndicator =
    req.conditionType === "INDICATOR_ABOVE" || req.conditionType === "INDICATOR_BELOW";
  if (isIndicator && !req.indicatorType) {
    throw new StoreError(400, "VALIDATION_ERROR", "İndikatör alarmlarında indikatör türü zorunludur.");
  }

  const nowIso = new Date().toISOString();
  const alert: StoredAlertRule = {
    id: crypto.randomUUID(),
    userId,
    instrumentId: inst.id,
    symbol: inst.symbol,
    instrumentName: inst.name,
    conditionType: req.conditionType,
    indicatorType: isIndicator ? req.indicatorType ?? "RSI" : null,
    indicatorField: isIndicator ? req.indicatorField || "value" : null,
    interval: req.interval || "1d",
    thresholdValue: roundTo(req.thresholdValue, 4),
    cooldownMinutes: Math.max(1, Math.min(10080, Math.round(req.cooldownMinutes ?? 60))),
    status: "ACTIVE",
    triggerState: "ARMED",
    lastEvaluatedAt: null,
    lastTriggeredAt: null,
    lastObservedValue: null,
    note: req.note?.trim() || null,
    createdAt: nowIso,
    updatedAt: nowIso,
  };
  getStore().alerts.set(alert.id, alert);
  return toAlertRuleResponse(alert);
}

export function updateAlert(
  userId: string,
  alertId: string,
  req: UpdateAlertRequest
): AlertRuleResponse {
  const store = getStore();
  const alert = store.alerts.get(alertId);
  if (!alert || alert.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "Alarm kuralı bulunamadı.");
  }
  if (req.status === "ACTIVE" || req.status === "PAUSED") {
    alert.status = req.status;
  }
  if (typeof req.thresholdValue === "number" && Number.isFinite(req.thresholdValue)) {
    alert.thresholdValue = roundTo(req.thresholdValue, 4);
    alert.triggerState = "ARMED";
  }
  if (typeof req.cooldownMinutes === "number" && Number.isFinite(req.cooldownMinutes)) {
    alert.cooldownMinutes = Math.max(1, Math.min(10080, Math.round(req.cooldownMinutes)));
  }
  if (req.note !== undefined) {
    alert.note = req.note?.trim() || null;
  }
  alert.updatedAt = new Date().toISOString();
  return toAlertRuleResponse(alert);
}

export function deleteAlert(userId: string, alertId: string): void {
  const store = getStore();
  const alert = store.alerts.get(alertId);
  if (!alert || alert.userId !== userId) {
    throw new StoreError(404, "NOT_FOUND", "Alarm kuralı bulunamadı.");
  }
  store.alerts.delete(alertId);
}

export function evaluateUserAlerts(userId: string): AlertEvaluationRunResponse {
  const store = getStore();
  const now = new Date();
  const nowIso = now.toISOString();
  const ds = getDataSourceMetadata();
  const userAlerts = Array.from(store.alerts.values()).filter(
    (a) => a.userId === userId && a.status === "ACTIVE"
  );

  const results: EvaluatedRuleResult[] = [];
  let triggeredCount = 0;
  let suppressedCount = 0;

  for (const alert of userAlerts) {
    let observedValue: number | null = null;
    let dataTimestamp = nowIso;

    if (alert.conditionType === "PRICE_ABOVE" || alert.conditionType === "PRICE_BELOW") {
      const q = getLatestQuote(alert.symbol, now);
      observedValue = q.price;
      dataTimestamp = q.quotedAt;
    } else if (alert.indicatorType) {
      const barsRes = generateHistoricalBars(alert.symbol, alert.interval, undefined, undefined, false, now);
      const series = buildIndicatorSeries(alert.indicatorType, barsRes.bars);
      if (series.status === "READY") {
        const field = alert.indicatorField || "value";
        const val = series.latestValues[field];
        observedValue = typeof val === "number" ? val : null;
        dataTimestamp = barsRes.bars[barsRes.bars.length - 1]?.timestamp ?? nowIso;
      }
    }

    alert.lastEvaluatedAt = nowIso;
    alert.lastObservedValue = observedValue;

    if (observedValue === null) {
      results.push({
        alertRuleId: alert.id,
        symbol: alert.symbol,
        outcome: "SKIPPED_INSUFFICIENT_DATA",
        observedValue: null,
        reason: "İndikatör veya fiyat verisi hesaplamak için yeterli bar yok.",
      });
      continue;
    }

    const conditionMet =
      alert.conditionType === "PRICE_ABOVE" || alert.conditionType === "INDICATOR_ABOVE"
        ? observedValue >= alert.thresholdValue
        : observedValue <= alert.thresholdValue;

    if (!conditionMet) {
      alert.triggerState = "ARMED";
      results.push({
        alertRuleId: alert.id,
        symbol: alert.symbol,
        outcome: "NOT_MET",
        observedValue,
        reason: `Gözlenen değer (${observedValue}) eşik koşulunu (${alert.thresholdValue}) sağlamadı.`,
      });
      continue;
    }

    if (alert.triggerState === "TRIGGERED") {
      suppressedCount++;
      results.push({
        alertRuleId: alert.id,
        symbol: alert.symbol,
        outcome: "DUPLICATE_SUPPRESSED",
        observedValue,
        reason: "Koşul hâlâ aktif; tekrar bildirim baskılandı.",
      });
      continue;
    }

    if (alert.lastTriggeredAt) {
      const elapsedMin = (now.getTime() - Date.parse(alert.lastTriggeredAt)) / 60000;
      if (elapsedMin < alert.cooldownMinutes) {
        suppressedCount++;
        results.push({
          alertRuleId: alert.id,
          symbol: alert.symbol,
          outcome: "COOLDOWN_SUPPRESSED",
          observedValue,
          reason: `Bekleme süresi (${alert.cooldownMinutes} dk) içinde olduğundan baskılandı.`,
        });
        continue;
      }
    }

    alert.triggerState = "TRIGGERED";
    alert.lastTriggeredAt = nowIso;
    alert.updatedAt = nowIso;
    triggeredCount++;

    const metricLabel =
      alert.conditionType === "PRICE_ABOVE" || alert.conditionType === "PRICE_BELOW"
        ? "Fiyat"
        : `${alert.indicatorType}(${alert.indicatorField ?? "value"})`;

    const notification: StoredNotification = {
      id: crypto.randomUUID(),
      userId,
      alertRuleId: alert.id,
      symbol: alert.symbol,
      conditionType: alert.conditionType,
      indicatorType: alert.indicatorType,
      indicatorField: alert.indicatorField,
      thresholdValue: alert.thresholdValue,
      observedValue,
      dataTimestamp,
      providerId: ds.providerId,
      providerDisplayName: ds.displayName,
      providerMode: ds.mode,
      simulated: ds.simulated,
      freshnessStatus: "SIMULATED_ONLY",
      staleData: false,
      message: `${alert.symbol} için ${metricLabel} gözlenen değeri (${observedValue.toFixed(2)}) belirlenen eşiği (${alert.thresholdValue.toFixed(2)}) sağladı.`,
      read: false,
      readAt: null,
      createdAt: nowIso,
    };
    store.notifications.unshift(notification);

    results.push({
      alertRuleId: alert.id,
      symbol: alert.symbol,
      outcome: "TRIGGERED",
      observedValue,
      reason: notification.message,
    });
  }

  return {
    evaluatedCount: userAlerts.length,
    triggeredCount,
    suppressedCount,
    evaluatedAt: nowIso,
    dataSource: ds,
    results,
  };
}

function toNotificationResponse(n: StoredNotification): AlertNotificationResponse {
  return {
    id: n.id,
    alertRuleId: n.alertRuleId,
    symbol: n.symbol,
    conditionType: n.conditionType,
    indicatorType: n.indicatorType,
    indicatorField: n.indicatorField,
    thresholdValue: n.thresholdValue,
    observedValue: n.observedValue,
    dataTimestamp: n.dataTimestamp,
    providerId: n.providerId,
    providerDisplayName: n.providerDisplayName,
    providerMode: n.providerMode,
    simulated: n.simulated,
    freshnessStatus: n.freshnessStatus,
    staleData: n.staleData,
    message: n.message,
    read: n.read,
    readAt: n.readAt,
    createdAt: n.createdAt,
  };
}

export function listNotifications(
  userId: string,
  unreadOnly: boolean,
  page: number,
  size: number
) {
  const store = getStore();
  const allUser = store.notifications.filter((n) => n.userId === userId);
  const unreadCount = allUser.filter((n) => !n.read).length;
  const filtered = unreadOnly ? allUser.filter((n) => !n.read) : allUser;
  const start = page * size;
  const items = filtered.slice(start, start + size).map(toNotificationResponse);
  return {
    items,
    page,
    size,
    totalElements: filtered.length,
    totalPages: Math.max(1, Math.ceil(filtered.length / size)),
    unreadCount,
  };
}

export function markNotificationRead(
  userId: string,
  notificationId: string
): AlertNotificationResponse {
  const store = getStore();
  const n = store.notifications.find((item) => item.id === notificationId && item.userId === userId);
  if (!n) {
    throw new StoreError(404, "NOT_FOUND", "Bildirim bulunamadı.");
  }
  if (!n.read) {
    n.read = true;
    n.readAt = new Date().toISOString();
  }
  return toNotificationResponse(n);
}

export function markAllNotificationsRead(userId: string): { updatedCount: number } {
  const store = getStore();
  const nowIso = new Date().toISOString();
  let updatedCount = 0;
  for (const n of store.notifications) {
    if (n.userId === userId && !n.read) {
      n.read = true;
      n.readAt = nowIso;
      updatedCount++;
    }
  }
  return { updatedCount };
}
