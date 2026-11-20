import type {
  AiExplanationResponse,
  BistMarginReferenceResponse,
  DataSourceMetadataResponse,
  FreshnessMetadataResponse,
  HistoricalBarsResponse,
  IndicatorPointResponse,
  IndicatorSeriesResponse,
  IndicatorType,
  InstrumentAnalysisResponse,
  InstrumentSetupResponse,
  OpportunityItemResponse,
  PriceBarResponse,
  PriceZoneResponse,
  QuoteSnapshotResponse,
  RiskRewardMetricsResponse,
  ScoreComponentResponse,
  ScoreRegime,
  SetupCategory,
  TechnicalIndicatorsResponse,
  TrendDirection,
} from "@/lib/api/types";
import { BIST_EXCHANGE, findInstrument, normalizeSymbol, type BistInstrumentRecord } from "./bist-catalog";

export const PROVIDER_ID = "MOCK_SIMULATED";
export const INDICATOR_ENGINE_VERSION = "ta-engine-v1.0.0";
export const SCORING_ALGORITHM_VERSION = "tech-score-v1.0.0";
export const SETUP_ENGINE_VERSION = "setup-engine-v1.0.0";
export const AI_PROMPT_VERSION = "ai-explain-v1.1.0-tr";

export const STANDARD_DISCLAIMER =
  "StockVision AI bir yatırım karar destek ve teknik analiz aracıdır; yatırım danışmanlığı, kâr garantisi veya kesin fiyat tahmini sunmaz.";

const FIXED_BIST_HOLIDAYS = new Set([
  "01-01",
  "04-23",
  "05-01",
  "05-19",
  "07-15",
  "08-30",
  "10-29",
]);

export function roundTo(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  return Math.round((value + Number.EPSILON) * factor) / factor;
}

function hashSeed(input: string): number {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 16777619) >>> 0;
  }
  return h >>> 0;
}

function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function isBistTradingDay(dateUtc: Date): boolean {
  const day = dateUtc.getUTCDay();
  if (day === 0 || day === 6) {
    return false;
  }
  const mm = String(dateUtc.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(dateUtc.getUTCDate()).padStart(2, "0");
  return !FIXED_BIST_HOLIDAYS.has(`${mm}-${dd}`);
}

export function getDataSourceMetadata(): DataSourceMetadataResponse {
  return {
    providerId: PROVIDER_ID,
    displayName: "StockVision Deterministik Simüle Sağlayıcı",
    mode: "SIMULATED",
    simulated: true,
    delayMinutes: 0,
    sourceTimezone: BIST_EXCHANGE.timezone,
    licenseNote:
      "Yalnızca geliştirme ve karar destek önizlemesi amaçlı simüle deterministik piyasa verisidir. Gerçek borsa verisi değildir.",
  };
}

export function getFreshnessMetadata(latestTimestamp: string | null): FreshnessMetadataResponse {
  const nowIso = new Date().toISOString();
  let ageMinutes: number | null = null;
  if (latestTimestamp) {
    const diffMs = Math.max(0, Date.now() - Date.parse(latestTimestamp));
    ageMinutes = Math.round(diffMs / 60000);
  }
  return {
    status: latestTimestamp ? "SIMULATED_ONLY" : "NO_DATA",
    stale: false,
    simulated: true,
    latestDataTimestamp: latestTimestamp,
    lastSyncedAt: nowIso,
    lastSyncStatus: "SUCCESS",
    ageMinutes,
    note: "Veriler yerleşik deterministik simüle sağlayıcı üzerinden anlık olarak üretilmiştir; canlı borsa akışı değildir.",
  };
}

interface DailyBarRaw {
  dateStr: string;
  timestampIso: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

const DAILY_SERIES_CACHE = new Map<string, { dayKey: string; bars: DailyBarRaw[] }>();

function getTodayUtcKey(now: Date = new Date()): string {
  return now.toISOString().slice(0, 10);
}

/**
 * Generates ~260 deterministic trading days (~1 year) ending on or before `now`
 * for a BIST symbol. Consistent across requests on the same calendar day.
 */
function generateDailySeries(symbol: string, now: Date = new Date()): DailyBarRaw[] {
  const norm = normalizeSymbol(symbol);
  const dayKey = getTodayUtcKey(now);
  const cached = DAILY_SERIES_CACHE.get(norm);
  if (cached && cached.dayKey === dayKey) {
    return cached.bars;
  }

  const seed = hashSeed(`${norm}|STOCKVISION_MOCK_V1`);
  const rng = mulberry32(seed);

  // Starting price between 22 TRY and 380 TRY, plus slight per-symbol drift regime so the universe has varied bullish/neutral/bearish/breakout setups
  const basePrice = 22 + (seed % 358);
  const driftBias = ((seed % 11) - 5) * 0.00055; // roughly -0.27% to +0.27% daily bias
  const volFactor = 0.014 + ((seed >>> 4) % 12) * 0.0014;

  // Walk backwards to find ~260 trading days
  const tradingDates: Date[] = [];
  const cursor = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate())
  );
  // Step back if before BIST close (15:00 UTC = 18:00 Europe/Istanbul)
  if (now.getUTCHours() < 15) {
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  while (tradingDates.length < 260) {
    if (isBistTradingDay(cursor)) {
      tradingDates.push(new Date(cursor.getTime()));
    }
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  tradingDates.reverse();

  let runningClose = basePrice;
  const bars: DailyBarRaw[] = [];
  const baseVolume = 750_000 + (seed % 4_200_000);

  for (let i = 0; i < tradingDates.length; i++) {
    const dt = tradingDates[i];
    const dateStr = dt.toISOString().slice(0, 10);
    // 18:00 Europe/Istanbul = 15:00 UTC
    const timestampIso = `${dateStr}T15:00:00.000Z`;

    // Add gentle cyclical wave + recent momentum character
    const wave = Math.sin(i / 18 + (seed % 7)) * 0.004;
    const recentPulse =
      i > tradingDates.length - 18
        ? (((seed >>> 8) % 9) - 4) * 0.0022
        : 0;

    const gapPct = (rng() - 0.5) * 0.011;
    const open = Math.max(1, roundTo(runningClose * (1 + gapPct), 2));

    const movePct = (rng() - 0.495) * 2 * volFactor + driftBias + wave + recentPulse;
    // Clamp daily move within BIST ±9.8% margin
    const clampedMove = Math.max(-0.095, Math.min(0.095, movePct));
    const close = Math.max(1, roundTo(open * (1 + clampedMove), 2));

    const maxOc = Math.max(open, close);
    const minOc = Math.min(open, close);
    const upperWick = 1 + (0.002 + rng() * 0.014);
    const lowerWick = 1 - (0.002 + rng() * 0.014);
    const high = roundTo(Math.max(maxOc, maxOc * upperWick), 2);
    const low = roundTo(Math.max(0.5, Math.min(minOc, minOc * lowerWick)), 2);

    const volumeBoost = Math.abs(clampedMove) > 0.025 ? 1.45 + rng() * 0.7 : 0.75 + rng() * 0.65;
    const volume = Math.max(10_000, Math.round(baseVolume * volumeBoost));

    bars.push({
      dateStr,
      timestampIso,
      open,
      high,
      low,
      close,
      volume,
    });
    runningClose = close;
  }

  DAILY_SERIES_CACHE.set(norm, { dayKey, bars });
  return bars;
}

export function generateHistoricalBars(
  symbol: string,
  interval: string = "1d",
  fromIso?: string,
  toIso?: string,
  adjusted: boolean = false,
  now: Date = new Date()
): HistoricalBarsResponse {
  const norm = normalizeSymbol(symbol);
  const effectiveInterval = interval === "1h" || interval === "15m" ? interval : "1d";
  const daily = generateDailySeries(norm, now);
  const ingestedAt = now.toISOString();
  const splitCutoff = "2025-07-01";

  const rawBars: PriceBarResponse[] = [];

  if (effectiveInterval === "1d") {
    for (const d of daily) {
      const factor = adjusted && d.dateStr < splitCutoff ? 0.95 : 1;
      const open = roundTo(d.open * factor, 2);
      const close = roundTo(d.close * factor, 2);
      const high = roundTo(Math.max(open, close, d.high * factor), 2);
      const low = roundTo(Math.max(0.5, Math.min(open, close, d.low * factor)), 2);
      rawBars.push({
        timestamp: d.timestampIso,
        open,
        high,
        low,
        close,
        volume: d.volume,
        currency: "TRY",
        interval: "1d",
        provider: PROVIDER_ID,
        adjusted,
        simulated: true,
        sourceTimezone: BIST_EXCHANGE.timezone,
        ingestedAt,
      });
    }
  } else {
    const slotMinutes = effectiveInterval === "1h" ? 60 : 15;
    const slots = effectiveInterval === "1h" ? 8 : 32;
    const daySlice = daily.slice(effectiveInterval === "1h" ? -35 : -10);

    for (const d of daySlice) {
      const rng = mulberry32(hashSeed(`${norm}|${d.dateStr}|${effectiveInterval}`));
      const factor = adjusted && d.dateStr < splitCutoff ? 0.95 : 1;
      const dayOpen = d.open * factor;
      const dayClose = d.close * factor;
      const delta = dayClose - dayOpen;
      let currOpen = roundTo(dayOpen, 2);
      const slotVolBase = Math.max(1000, Math.floor(d.volume / slots));

      // Session open 10:00 Europe/Istanbul = 07:00 UTC
      const baseMs = Date.parse(`${d.dateStr}T07:00:00.000Z`);

      for (let idx = 1; idx <= slots; idx++) {
        const ts = new Date(baseMs + idx * slotMinutes * 60_000).toISOString();
        const close =
          idx === slots
            ? roundTo(dayClose, 2)
            : Math.max(
                1,
                roundTo(
                  dayOpen + (delta * idx) / slots + (rng() - 0.5) * Math.max(0.2, dayOpen * 0.004),
                  2
                )
              );
        const maxOc = Math.max(currOpen, close);
        const minOc = Math.min(currOpen, close);
        const high = roundTo(maxOc * (1 + 0.0008 + rng() * 0.0025), 2);
        const low = roundTo(Math.max(0.5, minOc * (1 - 0.0008 - rng() * 0.0025)), 2);
        const volume = Math.max(500, Math.round(slotVolBase * (0.7 + rng() * 0.6)));

        rawBars.push({
          timestamp: ts,
          open: currOpen,
          high,
          low,
          close,
          volume,
          currency: "TRY",
          interval: effectiveInterval,
          provider: PROVIDER_ID,
          adjusted,
          simulated: true,
          sourceTimezone: BIST_EXCHANGE.timezone,
          ingestedAt,
        });
        currOpen = close;
      }
    }
  }

  const fromMs = fromIso ? Date.parse(fromIso) : Number.NaN;
  const toMs = toIso ? Date.parse(toIso) : Number.NaN;
  const filtered = rawBars.filter((bar) => {
    const ms = Date.parse(bar.timestamp);
    if (!Number.isNaN(fromMs) && ms < fromMs) return false;
    if (!Number.isNaN(toMs) && ms > toMs) return false;
    return true;
  });

  const firstTs = filtered[0]?.timestamp ?? fromIso ?? now.toISOString();
  const lastTs = filtered[filtered.length - 1]?.timestamp ?? toIso ?? now.toISOString();

  return {
    symbol: norm,
    exchange: BIST_EXCHANGE.code,
    interval: effectiveInterval,
    adjusted,
    from: firstTs,
    to: lastTs,
    count: filtered.length,
    missingDataNote:
      "Hafta sonları ve resmi BIST tatilleri ileri doldurulmaz (forward-fill yapılmaz). Tüm zaman damgaları UTC olarak saklanır.",
    dataSource: getDataSourceMetadata(),
    freshness: getFreshnessMetadata(filtered[filtered.length - 1]?.timestamp ?? null),
    bars: filtered,
  };
}

export function getLatestQuote(symbol: string, now: Date = new Date()): QuoteSnapshotResponse {
  const norm = normalizeSymbol(symbol);
  const daily = generateDailySeries(norm, now);
  const prev = daily[daily.length - 2];
  const latest = daily[daily.length - 1];
  const changeAmount = roundTo(latest.close - prev.close, 2);
  const changePercent = roundTo((changeAmount / prev.close) * 100, 2);

  return {
    price: latest.close,
    previousClose: prev.close,
    changeAmount,
    changePercent,
    volume: latest.volume,
    currency: "TRY",
    quotedAt: latest.timestampIso,
    provider: PROVIDER_ID,
    simulated: true,
    delayMinutes: 0,
    sourceTimezone: BIST_EXCHANGE.timezone,
  };
}

// ============================================================================
// Deterministic Technical Indicator Engine
// ============================================================================

function computeSmaArray(values: number[], period: number): Array<number | null> {
  const out: Array<number | null> = new Array(values.length).fill(null);
  let sum = 0;
  for (let i = 0; i < values.length; i++) {
    sum += values[i];
    if (i >= period) sum -= values[i - period];
    if (i >= period - 1) {
      out[i] = roundTo(sum / period, 4);
    }
  }
  return out;
}

function computeEmaArray(values: number[], period: number): Array<number | null> {
  const out: Array<number | null> = new Array(values.length).fill(null);
  if (values.length < period) return out;
  let sum = 0;
  for (let i = 0; i < period; i++) sum += values[i];
  let prev = sum / period;
  out[period - 1] = roundTo(prev, 4);
  const k = 2 / (period + 1);
  for (let i = period; i < values.length; i++) {
    prev = values[i] * k + prev * (1 - k);
    out[i] = roundTo(prev, 4);
  }
  return out;
}

function computeRsiArray(closes: number[], period: number = 14): Array<number | null> {
  const out: Array<number | null> = new Array(closes.length).fill(null);
  if (closes.length <= period) return out;

  let gainSum = 0;
  let lossSum = 0;
  for (let i = 1; i <= period; i++) {
    const delta = closes[i] - closes[i - 1];
    if (delta >= 0) gainSum += delta;
    else lossSum -= delta;
  }
  let avgGain = gainSum / period;
  let avgLoss = lossSum / period;
  out[period] = avgLoss === 0 ? 100 : roundTo(100 - 100 / (1 + avgGain / avgLoss), 2);

  for (let i = period + 1; i < closes.length; i++) {
    const delta = closes[i] - closes[i - 1];
    const gain = delta > 0 ? delta : 0;
    const loss = delta < 0 ? -delta : 0;
    avgGain = (avgGain * (period - 1) + gain) / period;
    avgLoss = (avgLoss * (period - 1) + loss) / period;
    out[i] = avgLoss === 0 ? 100 : roundTo(100 - 100 / (1 + avgGain / avgLoss), 2);
  }
  return out;
}

function computeAtrArray(bars: PriceBarResponse[], period: number = 14): Array<number | null> {
  const out: Array<number | null> = new Array(bars.length).fill(null);
  if (bars.length <= period) return out;
  const trs: number[] = [bars[0].high - bars[0].low];
  for (let i = 1; i < bars.length; i++) {
    const highLow = bars[i].high - bars[i].low;
    const highPrevClose = Math.abs(bars[i].high - bars[i - 1].close);
    const lowPrevClose = Math.abs(bars[i].low - bars[i - 1].close);
    trs.push(Math.max(highLow, highPrevClose, lowPrevClose));
  }
  let sum = 0;
  for (let i = 1; i <= period; i++) sum += trs[i];
  let prevAtr = sum / period;
  out[period] = roundTo(prevAtr, 4);
  for (let i = period + 1; i < bars.length; i++) {
    prevAtr = (prevAtr * (period - 1) + trs[i]) / period;
    out[i] = roundTo(prevAtr, 4);
  }
  return out;
}

export function buildIndicatorSeries(
  type: IndicatorType,
  bars: PriceBarResponse[]
): IndicatorSeriesResponse {
  const closes = bars.map((b) => b.close);
  const volumes = bars.map((b) => b.volume);
  const n = bars.length;

  const makeInsufficient = (
    warmUpBars: number,
    requiredBars: number,
    parameters: Record<string, unknown>
  ): IndicatorSeriesResponse => ({
    type,
    status: "INSUFFICIENT_DATA",
    warmUpBars,
    requiredBars,
    availableBars: n,
    parameters,
    latestValues: {},
    points: [],
  });

  if (type === "SMA") {
    const period = 20;
    const params = { period };
    if (n < period) return makeInsufficient(period - 1, period, params);
    const vals = computeSmaArray(closes, period);
    const points: IndicatorPointResponse[] = bars.map((b, i) => ({
      timestamp: b.timestamp,
      warmUp: vals[i] === null,
      values: { value: vals[i] },
    }));
    return {
      type,
      status: "READY",
      warmUpBars: period - 1,
      requiredBars: period,
      availableBars: n,
      parameters: params,
      latestValues: { value: vals[n - 1] },
      points,
    };
  }

  if (type === "EMA") {
    const period = 20;
    const params = { period };
    if (n < period) return makeInsufficient(period - 1, period, params);
    const vals = computeEmaArray(closes, period);
    const points: IndicatorPointResponse[] = bars.map((b, i) => ({
      timestamp: b.timestamp,
      warmUp: vals[i] === null,
      values: { value: vals[i] },
    }));
    return {
      type,
      status: "READY",
      warmUpBars: period - 1,
      requiredBars: period,
      availableBars: n,
      parameters: params,
      latestValues: { value: vals[n - 1] },
      points,
    };
  }

  if (type === "RSI") {
    const period = 14;
    const params = { period };
    if (n < period + 1) return makeInsufficient(period, period + 1, params);
    const vals = computeRsiArray(closes, period);
    const points: IndicatorPointResponse[] = bars.map((b, i) => ({
      timestamp: b.timestamp,
      warmUp: vals[i] === null,
      values: { value: vals[i] },
    }));
    return {
      type,
      status: "READY",
      warmUpBars: period,
      requiredBars: period + 1,
      availableBars: n,
      parameters: params,
      latestValues: { value: vals[n - 1] },
      points,
    };
  }

  if (type === "MACD") {
    const fast = 12;
    const slow = 26;
    const signalPeriod = 9;
    const required = slow + signalPeriod - 1;
    const params = { fastPeriod: fast, slowPeriod: slow, signalPeriod };
    if (n < required) return makeInsufficient(required - 1, required, params);

    const emaFast = computeEmaArray(closes, fast);
    const emaSlow = computeEmaArray(closes, slow);
    const macdLine: Array<number | null> = closes.map((_, i) =>
      emaFast[i] !== null && emaSlow[i] !== null ? roundTo(emaFast[i]! - emaSlow[i]!, 4) : null
    );
    const validMacd = macdLine.slice(slow - 1).map((v) => v ?? 0);
    const sigShort = computeEmaArray(validMacd, signalPeriod);

    const points: IndicatorPointResponse[] = bars.map((b, i) => {
      const m = macdLine[i];
      const s = i >= slow - 1 ? sigShort[i - (slow - 1)] : null;
      const h = m !== null && s !== null ? roundTo(m - s, 4) : null;
      return {
        timestamp: b.timestamp,
        warmUp: h === null,
        values: { macd: m, signal: s, histogram: h },
      };
    });
    const last = points[n - 1].values;
    return {
      type,
      status: "READY",
      warmUpBars: required - 1,
      requiredBars: required,
      availableBars: n,
      parameters: params,
      latestValues: last,
      points,
    };
  }

  if (type === "BOLLINGER") {
    const period = 20;
    const stdDevMult = 2;
    const params = { period, stdDevMultiplier: stdDevMult };
    if (n < period) return makeInsufficient(period - 1, period, params);

    const sma = computeSmaArray(closes, period);
    const points: IndicatorPointResponse[] = bars.map((b, i) => {
      if (i < period - 1 || sma[i] === null) {
        return {
          timestamp: b.timestamp,
          warmUp: true,
          values: { upper: null, middle: null, lower: null, bandwidth: null },
        };
      }
      const mean = sma[i]!;
      let sqSum = 0;
      for (let j = i - period + 1; j <= i; j++) {
        sqSum += (closes[j] - mean) ** 2;
      }
      const std = Math.sqrt(sqSum / period);
      const upper = roundTo(mean + stdDevMult * std, 4);
      const lower = roundTo(Math.max(0.5, mean - stdDevMult * std), 4);
      const bandwidth = mean > 0 ? roundTo(((upper - lower) / mean) * 100, 2) : 0;
      return {
        timestamp: b.timestamp,
        warmUp: false,
        values: { upper, middle: mean, lower, bandwidth },
      };
    });
    return {
      type,
      status: "READY",
      warmUpBars: period - 1,
      requiredBars: period,
      availableBars: n,
      parameters: params,
      latestValues: points[n - 1].values,
      points,
    };
  }

  if (type === "STOCHASTIC") {
    const kPeriod = 14;
    const dPeriod = 3;
    const required = kPeriod + dPeriod - 1;
    const params = { kPeriod, dPeriod };
    if (n < required) return makeInsufficient(required - 1, required, params);

    const kVals: Array<number | null> = bars.map((b, i) => {
      if (i < kPeriod - 1) return null;
      let highest = -Infinity;
      let lowest = Infinity;
      for (let j = i - kPeriod + 1; j <= i; j++) {
        if (bars[j].high > highest) highest = bars[j].high;
        if (bars[j].low < lowest) lowest = bars[j].low;
      }
      const span = highest - lowest;
      return span <= 0 ? 50 : roundTo(((b.close - lowest) / span) * 100, 2);
    });

    const points: IndicatorPointResponse[] = bars.map((b, i) => {
      const k = kVals[i];
      let d: number | null = null;
      if (i >= required - 1) {
        let sum = 0;
        let ok = true;
        for (let j = i - dPeriod + 1; j <= i; j++) {
          if (kVals[j] === null) ok = false;
          else sum += kVals[j]!;
        }
        if (ok) d = roundTo(sum / dPeriod, 2);
      }
      return {
        timestamp: b.timestamp,
        warmUp: d === null,
        values: { k, d },
      };
    });
    return {
      type,
      status: "READY",
      warmUpBars: required - 1,
      requiredBars: required,
      availableBars: n,
      parameters: params,
      latestValues: points[n - 1].values,
      points,
    };
  }

  if (type === "ATR") {
    const period = 14;
    const params = { period };
    if (n < period + 1) return makeInsufficient(period, period + 1, params);
    const vals = computeAtrArray(bars, period);
    const points: IndicatorPointResponse[] = bars.map((b, i) => ({
      timestamp: b.timestamp,
      warmUp: vals[i] === null,
      values: { value: vals[i] },
    }));
    return {
      type,
      status: "READY",
      warmUpBars: period,
      requiredBars: period + 1,
      availableBars: n,
      parameters: params,
      latestValues: { value: vals[n - 1] },
      points,
    };
  }

  // VOLUME_AVG
  const period = 20;
  const params = { period };
  if (n < period) return makeInsufficient(period - 1, period, params);
  const avgVols = computeSmaArray(volumes, period);
  const points: IndicatorPointResponse[] = bars.map((b, i) => {
    const avg = avgVols[i];
    const ratio = avg && avg > 0 ? roundTo(b.volume / avg, 2) : null;
    return {
      timestamp: b.timestamp,
      warmUp: avg === null,
      values: { average: avg !== null ? Math.round(avg) : null, ratio },
    };
  });
  return {
    type: "VOLUME_AVG",
    status: "READY",
    warmUpBars: period - 1,
    requiredBars: period,
    availableBars: n,
    parameters: params,
    latestValues: points[n - 1].values,
    points,
  };
}

export function calculateTechnicalIndicators(
  symbol: string,
  requested: IndicatorType[],
  interval: string = "1d",
  fromIso?: string,
  toIso?: string,
  adjusted: boolean = false,
  now: Date = new Date()
): TechnicalIndicatorsResponse {
  const barsRes = generateHistoricalBars(symbol, interval, fromIso, toIso, adjusted, now);
  const types: IndicatorType[] =
    requested.length > 0
      ? requested
      : ["SMA", "EMA", "RSI", "MACD", "BOLLINGER", "STOCHASTIC", "ATR", "VOLUME_AVG"];

  const seriesList = types.map((t) => buildIndicatorSeries(t, barsRes.bars));
  const readyCount = seriesList.filter((s) => s.status === "READY").length;
  const overallStatus =
    readyCount === 0
      ? "INSUFFICIENT_DATA"
      : readyCount === seriesList.length
      ? "READY"
      : "PARTIAL";

  const runSeed = hashSeed(`TA|${barsRes.symbol}|${barsRes.interval}|${barsRes.count}|${adjusted}`);
  return {
    analysisRunId: `ta-${runSeed.toString(16).padStart(8, "0")}`,
    symbol: barsRes.symbol,
    exchange: barsRes.exchange,
    interval: barsRes.interval,
    adjusted,
    from: barsRes.from,
    to: barsRes.to,
    barCount: barsRes.count,
    engineVersion: INDICATOR_ENGINE_VERSION,
    status: overallStatus,
    calculatedAt: now.toISOString(),
    disclaimer: STANDARD_DISCLAIMER,
    dataSource: barsRes.dataSource,
    freshness: barsRes.freshness,
    indicators: seriesList,
  };
}

// ============================================================================
// Explainable Deterministic Technical Scoring Engine (0 - 100)
// ============================================================================

const SCORE_WEIGHTS: Record<string, number> = {
  TREND: 25,
  RSI: 15,
  MACD: 15,
  VOLUME: 10,
  BOLLINGER: 10,
  MOMENTUM: 15,
  SUPPORT_RESISTANCE: 10,
};

function clampScore(v: number): number {
  return roundTo(Math.max(0, Math.min(100, v)), 2);
}

export function calculateInstrumentAnalysis(
  symbol: string,
  interval: string = "1d",
  fromIso?: string,
  toIso?: string,
  adjusted: boolean = false,
  now: Date = new Date()
): InstrumentAnalysisResponse {
  const norm = normalizeSymbol(symbol);
  const barsRes = generateHistoricalBars(norm, interval, fromIso, toIso, adjusted, now);
  const bars = barsRes.bars;

  const scoreRunId = `scr-${hashSeed(`${norm}|${barsRes.interval}|${barsRes.count}|${adjusted}|${getTodayUtcKey(now)}`).toString(16).padStart(8, "0")}`;
  const analysisRunId = `ta-${hashSeed(`${norm}|${barsRes.interval}|${barsRes.count}|${adjusted}`).toString(16).padStart(8, "0")}`;

  if (bars.length < 35) {
    return {
      scoreRunId,
      analysisRunId,
      symbol: norm,
      exchange: BIST_EXCHANGE.code,
      interval: barsRes.interval,
      adjusted,
      from: barsRes.from,
      to: barsRes.to,
      barCount: bars.length,
      algorithmVersion: SCORING_ALGORITHM_VERSION,
      indicatorEngineVersion: INDICATOR_ENGINE_VERSION,
      status: "INSUFFICIENT_DATA",
      staleData: false,
      finalScore: null,
      regime: "INSUFFICIENT_DATA",
      trendDirection: "INSUFFICIENT_DATA",
      latestClose: bars[bars.length - 1]?.close ?? null,
      latestRsi: null,
      insufficientDataReasons: [
        `Teknik skorlama için en az 35 bar gereklidir (mevcut: ${bars.length} bar).`,
      ],
      weights: SCORE_WEIGHTS,
      components: [],
      explanation: {
        headline: `${norm} için yeterli fiyat barı bulunmadığından teknik skor üretilmedi (INSUFFICIENT_DATA).`,
        keyDrivers: [],
        riskAndDataNotes: [
          "Eksik geçmiş veri durumunda yanıltıcı skor üretmek yerine INSUFFICIENT_DATA döndürülür.",
        ],
        methodologySummary:
          "7 ağırlıklı teknik bileşenin (Trend, RSI, MACD, Hacim, Bollinger, Momentum, Destek/Direnç) deterministik toplamı.",
        disclaimer: STANDARD_DISCLAIMER,
      },
      calculatedAt: now.toISOString(),
      dataSource: barsRes.dataSource,
      freshness: barsRes.freshness,
    };
  }

  const sma = buildIndicatorSeries("SMA", bars);
  const ema = buildIndicatorSeries("EMA", bars);
  const rsi = buildIndicatorSeries("RSI", bars);
  const macd = buildIndicatorSeries("MACD", bars);
  const bb = buildIndicatorSeries("BOLLINGER", bars);
  const stoch = buildIndicatorSeries("STOCHASTIC", bars);
  const volAvg = buildIndicatorSeries("VOLUME_AVG", bars);

  const latestClose = bars[bars.length - 1].close;
  const latestSma = sma.latestValues.value ?? latestClose;
  const latestEma = ema.latestValues.value ?? latestClose;
  const ema5Ago =
    ema.points[Math.max(0, ema.points.length - 6)]?.values.value ?? latestEma;
  const emaSlope5Pct = ema5Ago > 0 ? roundTo(((latestEma - ema5Ago) / ema5Ago) * 100, 2) : 0;

  const latestRsi = rsi.latestValues.value ?? 50;
  const macdVal = macd.latestValues.macd ?? 0;
  const macdSig = macd.latestValues.signal ?? 0;
  const macdHist = macd.latestValues.histogram ?? 0;

  const bbUpper = bb.latestValues.upper ?? latestClose * 1.05;
  const bbLower = bb.latestValues.lower ?? latestClose * 0.95;
  const bbMiddle = bb.latestValues.middle ?? latestClose;
  const percentB =
    bbUpper > bbLower ? roundTo((latestClose - bbLower) / (bbUpper - bbLower), 2) : 0.5;

  const volRatio = volAvg.latestValues.ratio ?? 1;
  const avgVolume = volAvg.latestValues.average ?? bars[bars.length - 1].volume;

  const close10Ago = bars[Math.max(0, bars.length - 11)].close;
  const roc10Pct = close10Ago > 0 ? roundTo(((latestClose - close10Ago) / close10Ago) * 100, 2) : 0;
  const stochK = stoch.latestValues.k ?? 50;
  const stochD = stoch.latestValues.d ?? 50;

  const last20 = bars.slice(-20);
  const support20 = Math.min(...last20.map((b) => b.low));
  const resistance20 = Math.max(...last20.map((b) => b.high));
  const rangePosition =
    resistance20 > support20
      ? roundTo((latestClose - support20) / (resistance20 - support20), 2)
      : 0.5;

  // 1. TREND (25%)
  let trendScore = 50;
  if (latestClose > latestEma) trendScore += 16;
  else trendScore -= 16;
  if (latestClose > latestSma) trendScore += 12;
  else trendScore -= 12;
  if (latestEma > latestSma) trendScore += 10;
  else trendScore -= 10;
  trendScore += Math.max(-12, Math.min(12, emaSlope5Pct * 4));
  trendScore = clampScore(trendScore);

  // 2. RSI (15%)
  let rsiScore = 50;
  if (latestRsi >= 50 && latestRsi <= 68) rsiScore = 65 + (latestRsi - 50) * 1.1;
  else if (latestRsi > 68) rsiScore = Math.max(45, 84 - (latestRsi - 68) * 1.6);
  else if (latestRsi >= 35) rsiScore = 35 + (latestRsi - 35) * 1.3;
  else rsiScore = Math.max(20, 42 - (35 - latestRsi) * 0.7);
  rsiScore = clampScore(rsiScore);

  // 3. MACD (15%)
  let macdScore = 50;
  if (macdVal > macdSig) macdScore += 18;
  else macdScore -= 18;
  if (macdVal > 0) macdScore += 10;
  else macdScore -= 8;
  macdScore += Math.max(-18, Math.min(18, (macdHist / Math.max(1, latestClose)) * 2500));
  macdScore = clampScore(macdScore);

  // 4. VOLUME (10%)
  const dailyUp = bars[bars.length - 1].close >= bars[bars.length - 2].close;
  let volumeScore = 50;
  if (dailyUp && volRatio >= 1.2) volumeScore = 65 + Math.min(30, (volRatio - 1) * 28);
  else if (!dailyUp && volRatio >= 1.2) volumeScore = 38 - Math.min(24, (volRatio - 1) * 20);
  else volumeScore = 48 + (dailyUp ? 6 : -4);
  volumeScore = clampScore(volumeScore);

  // 5. BOLLINGER (10%)
  let bbScore = 50;
  if (percentB >= 0.5 && percentB <= 0.85) bbScore = 62 + (percentB - 0.5) * 50;
  else if (percentB > 0.85) bbScore = Math.max(48, 78 - (percentB - 0.85) * 80);
  else if (percentB >= 0.2) bbScore = 36 + (percentB - 0.2) * 55;
  else bbScore = 32;
  bbScore = clampScore(bbScore);

  // 6. MOMENTUM (15%)
  let momScore = 50 + roc10Pct * 2.4 + (stochK - 50) * 0.35 + (stochK > stochD ? 6 : -6);
  momScore = clampScore(momScore);

  // 7. SUPPORT_RESISTANCE (10%)
  let srScore = 40 + rangePosition * 45;
  if (rangePosition > 0.92) srScore -= 8; // close to overhead resistance
  srScore = clampScore(srScore);

  const components: ScoreComponentResponse[] = [
    {
      type: "TREND",
      status: "READY",
      weightPercent: SCORE_WEIGHTS.TREND,
      normalizedScore: trendScore,
      weightedContribution: roundTo((trendScore * SCORE_WEIGHTS.TREND) / 100, 2),
      explanation:
        latestClose >= latestEma
          ? `Fiyat (${latestClose.toFixed(2)}) 20 barlık EMA (${latestEma.toFixed(2)}) üzerinde seyrediyor; 5 barlık EMA eğimi %${emaSlope5Pct.toFixed(2)}.`
          : `Fiyat (${latestClose.toFixed(2)}) 20 barlık EMA (${latestEma.toFixed(2)}) altında; kısa vadeli trend zayıf.`,
      metrics: {
        close: latestClose,
        sma: roundTo(latestSma, 2),
        ema: roundTo(latestEma, 2),
        emaSlope5BarPct: emaSlope5Pct,
      },
    },
    {
      type: "RSI",
      status: "READY",
      weightPercent: SCORE_WEIGHTS.RSI,
      normalizedScore: rsiScore,
      weightedContribution: roundTo((rsiScore * SCORE_WEIGHTS.RSI) / 100, 2),
      explanation:
        latestRsi > 70
          ? `RSI(14) ${latestRsi.toFixed(2)} ile aşırı alım bölgesinde; kısa vadeli ısınma riski gözleniyor.`
          : latestRsi < 30
          ? `RSI(14) ${latestRsi.toFixed(2)} ile aşırı satım bölgesinde; tepki potansiyeli veya zayıflık izlenmeli.`
          : `RSI(14) ${latestRsi.toFixed(2)} ile dengeli momentum bölgesinde.`,
      metrics: { rsi: latestRsi },
    },
    {
      type: "MACD",
      status: "READY",
      weightPercent: SCORE_WEIGHTS.MACD,
      normalizedScore: macdScore,
      weightedContribution: roundTo((macdScore * SCORE_WEIGHTS.MACD) / 100, 2),
      explanation:
        macdVal >= macdSig
          ? `MACD (${macdVal.toFixed(2)}) sinyal çizgisinin (${macdSig.toFixed(2)}) üzerinde pozitif histogram üretiyor.`
          : `MACD (${macdVal.toFixed(2)}) sinyal çizgisinin (${macdSig.toFixed(2)}) altında negatif bölgede.`,
      metrics: {
        macd: roundTo(macdVal, 2),
        signal: roundTo(macdSig, 2),
        histogram: roundTo(macdHist, 2),
      },
    },
    {
      type: "VOLUME",
      status: "READY",
      weightPercent: SCORE_WEIGHTS.VOLUME,
      normalizedScore: volumeScore,
      weightedContribution: roundTo((volumeScore * SCORE_WEIGHTS.VOLUME) / 100, 2),
      explanation: `Son seans hacmi 20 barlık ortalamanın ${volRatio.toFixed(2)}x katında gerçekleşti.`,
      metrics: {
        averageVolume: avgVolume,
        volumeRatio: volRatio,
      },
    },
    {
      type: "BOLLINGER",
      status: "READY",
      weightPercent: SCORE_WEIGHTS.BOLLINGER,
      normalizedScore: bbScore,
      weightedContribution: roundTo((bbScore * SCORE_WEIGHTS.BOLLINGER) / 100, 2),
      explanation: `Fiyat Bollinger bantları içinde %B=${percentB.toFixed(2)} konumunda (orta bant ${bbMiddle.toFixed(2)}).`,
      metrics: {
        upper: roundTo(bbUpper, 2),
        middle: roundTo(bbMiddle, 2),
        lower: roundTo(bbLower, 2),
        percentB,
      },
    },
    {
      type: "MOMENTUM",
      status: "READY",
      weightPercent: SCORE_WEIGHTS.MOMENTUM,
      normalizedScore: momScore,
      weightedContribution: roundTo((momScore * SCORE_WEIGHTS.MOMENTUM) / 100, 2),
      explanation: `10 barlık fiyat değişimi (ROC) %${roc10Pct.toFixed(2)}, Stokastik %K=${stochK.toFixed(1)} / %D=${stochD.toFixed(1)}.`,
      metrics: {
        roc10Pct,
        stochasticK: stochK,
        stochasticD: stochD,
      },
    },
    {
      type: "SUPPORT_RESISTANCE",
      status: "READY",
      weightPercent: SCORE_WEIGHTS.SUPPORT_RESISTANCE,
      normalizedScore: srScore,
      weightedContribution: roundTo((srScore * SCORE_WEIGHTS.SUPPORT_RESISTANCE) / 100, 2),
      explanation: `20 barlık ${support20.toFixed(2)} – ${resistance20.toFixed(2)} aralığının %${Math.round(rangePosition * 100)} seviyesinde.`,
      metrics: {
        support20: roundTo(support20, 2),
        resistance20: roundTo(resistance20, 2),
        rangePosition,
      },
    },
  ];

  const finalScore = clampScore(
    components.reduce((acc, c) => acc + (c.weightedContribution ?? 0), 0)
  );

  let regime: ScoreRegime = "NEUTRAL";
  if (finalScore >= 72) regime = "STRONG_BULLISH";
  else if (finalScore >= 58) regime = "BULLISH";
  else if (finalScore >= 42) regime = "NEUTRAL";
  else if (finalScore >= 28) regime = "BEARISH";
  else regime = "STRONG_BEARISH";

  const trendDirection: TrendDirection =
    trendScore >= 58 ? "BULLISH" : trendScore <= 42 ? "BEARISH" : "NEUTRAL";

  const sortedByScore = [...components].sort(
    (a, b) => (b.normalizedScore ?? 0) - (a.normalizedScore ?? 0)
  );
  const topDriver = sortedByScore[0];
  const weakestDriver = sortedByScore[sortedByScore.length - 1];

  const regimeTr =
    regime === "STRONG_BULLISH"
      ? "güçlü yükseliş"
      : regime === "BULLISH"
      ? "yükseliş"
      : regime === "NEUTRAL"
      ? "nötr / yatay"
      : regime === "BEARISH"
      ? "düşüş"
      : "güçlü düşüş";

  return {
    scoreRunId,
    analysisRunId,
    symbol: norm,
    exchange: BIST_EXCHANGE.code,
    interval: barsRes.interval,
    adjusted,
    from: barsRes.from,
    to: barsRes.to,
    barCount: bars.length,
    algorithmVersion: SCORING_ALGORITHM_VERSION,
    indicatorEngineVersion: INDICATOR_ENGINE_VERSION,
    status: "READY",
    staleData: false,
    finalScore,
    regime,
    trendDirection,
    latestClose,
    latestRsi,
    insufficientDataReasons: [],
    weights: SCORE_WEIGHTS,
    components,
    explanation: {
      headline: `${norm} bileşik teknik skoru ${finalScore.toFixed(1)}/100 ile ${regimeTr} görünümünde.`,
      keyDrivers: [
        topDriver.explanation,
        sortedByScore[1].explanation,
        weakestDriver.explanation,
      ],
      riskAndDataNotes: [
        "Veriler deterministik simüle sağlayıcıdan üretilmiştir; gerçek borsa fiyatı değildir.",
        latestRsi > 70
          ? "RSI(14) 70 üzerinde olduğundan aşırı alım kaynaklı oynaklık riski mevcuttur."
          : "Teknik göstergeler geçmiş fiyat serilerini özetler; gelecekteki performansı garanti etmez.",
      ],
      methodologySummary:
        "Skor; Trend (%25), RSI (%15), MACD (%15), Momentum (%15), Hacim (%10), Bollinger (%10) ve Destek/Direnç (%10) bileşenlerinin deterministik ağırlıklı toplamıdır.",
      disclaimer: STANDARD_DISCLAIMER,
    },
    calculatedAt: now.toISOString(),
    dataSource: barsRes.dataSource,
    freshness: barsRes.freshness,
  };
}

// ============================================================================
// Deterministic Technical Setup & Opportunities Engine
// ============================================================================

export function calculateInstrumentSetup(
  symbol: string,
  interval: string = "1d",
  fromIso?: string,
  toIso?: string,
  adjusted: boolean = false,
  now: Date = new Date()
): InstrumentSetupResponse {
  const norm = normalizeSymbol(symbol);
  const inst: BistInstrumentRecord | undefined = findInstrument(norm);
  const barsRes = generateHistoricalBars(norm, interval, fromIso, toIso, adjusted, now);
  const bars = barsRes.bars;
  const analysis = calculateInstrumentAnalysis(norm, interval, fromIso, toIso, adjusted, now);

  const latest = bars[bars.length - 1];
  const prev = bars[bars.length - 2];

  const bistMargin: BistMarginReferenceResponse | null =
    latest && prev
      ? (() => {
          const dailyChangePercent = roundTo(
            ((latest.close - prev.close) / prev.close) * 100,
            2
          );
          const ceiling = roundTo(prev.close * 1.1, 2);
          const floor = roundTo(prev.close * 0.9, 2);
          const distCeiling = roundTo(((ceiling - latest.close) / latest.close) * 100, 2);
          const distFloor = roundTo(((latest.close - floor) / latest.close) * 100, 2);
          return {
            referencePrice: latest.close,
            previousClose: prev.close,
            dailyChangePercent,
            theoreticalCeilingPrice: ceiling,
            theoreticalFloorPrice: floor,
            distanceToCeilingPercent: Math.max(0, distCeiling),
            distanceToFloorPercent: Math.max(0, distFloor),
            note: "BIST günlük ±%10 fiyat marjı teorik referansıdır; tavan/taban tahmini veya emir yönlendirmesi değildir.",
          };
        })()
      : null;

  if (analysis.status !== "READY" || bars.length < 35 || !latest) {
    return {
      symbol: norm,
      name: inst?.name ?? norm,
      exchange: BIST_EXCHANGE.code,
      sector: inst?.sector ?? null,
      currency: inst?.currency ?? "TRY",
      interval: barsRes.interval,
      adjusted,
      from: barsRes.from,
      to: barsRes.to,
      barCount: bars.length,
      setupEngineVersion: SETUP_ENGINE_VERSION,
      scoringAlgorithmVersion: SCORING_ALGORITHM_VERSION,
      indicatorEngineVersion: INDICATOR_ENGINE_VERSION,
      status: "INSUFFICIENT_DATA",
      category: "INSUFFICIENT_DATA",
      staleData: false,
      simulated: true,
      technicalScore: null,
      momentumBreakoutScore: null,
      regime: "INSUFFICIENT_DATA",
      trendDirection: "INSUFFICIENT_DATA",
      technicalReferencePrice: latest?.close ?? null,
      latestRsi: null,
      volumeRatio: null,
      atr14: null,
      atrPercent: null,
      breakoutTriggerLevel: null,
      distanceToBreakoutPercent: null,
      breakoutConfirmed: false,
      supportWatchZone: null,
      resistanceExitZone: null,
      invalidationRiskLevel: null,
      invalidationRationale: null,
      riskReward: null,
      bistMargin,
      usedIndicators: [],
      rationaleItems: [],
      riskWarnings: [],
      insufficientDataReasons: [
        `Teknik seviyeler için en az 35 bar gereklidir (mevcut: ${bars.length}).`,
      ],
      headline: `${norm} için yetersiz veri nedeniyle teknik seviyeler üretilemedi.`,
      calculatedAt: now.toISOString(),
      disclaimer: STANDARD_DISCLAIMER,
      dataSource: barsRes.dataSource,
      freshness: barsRes.freshness,
    };
  }

  const atrSeries = buildIndicatorSeries("ATR", bars);
  const emaSeries = buildIndicatorSeries("EMA", bars);
  const volSeries = buildIndicatorSeries("VOLUME_AVG", bars);

  const refPrice = latest.close;
  const atr14 = roundTo(atrSeries.latestValues.value ?? refPrice * 0.025, 2);
  const atrPercent = roundTo((atr14 / refPrice) * 100, 2);
  const ema20 = emaSeries.latestValues.value ?? refPrice;
  const volRatio = volSeries.latestValues.ratio ?? 1;
  const latestRsi = analysis.latestRsi ?? 50;

  const prior19 = bars.slice(-20, -1);
  const breakoutTrigger = roundTo(Math.max(...prior19.map((b) => b.high)), 2);
  const support20 = roundTo(Math.min(...bars.slice(-20).map((b) => b.low)), 2);
  const distanceToBreakoutPercent = roundTo(
    ((breakoutTrigger - refPrice) / refPrice) * 100,
    2
  );
  const breakoutConfirmed = refPrice > breakoutTrigger;

  const supportHigh = roundTo(Math.min(refPrice * 0.992, Math.max(ema20, support20 + atr14 * 0.4)), 2);
  const supportLow = roundTo(Math.max(0.5, Math.min(supportHigh - atr14 * 0.65, support20)), 2);

  const supportWatchZone: PriceZoneResponse = {
    low: supportLow,
    high: Math.max(supportLow + 0.05, supportHigh),
    label: "Destek / İzleme Bölgesi",
    rationale: "20 barlık dip seviye ve EMA(20) çevresindeki ATR tabanlı destek bandı.",
  };

  const r1 = roundTo(Math.max(refPrice + atr14 * 1.35, breakoutTrigger * 1.015), 2);
  const r2 = roundTo(r1 + atr14 * 1.25, 2);
  const resistanceExitZone: PriceZoneResponse = {
    low: r1,
    high: r2,
    label: "Direnç / Kâr Realizasyonu (R1–R2)",
    rationale: "Kırılım eşiği üzeri 1.35× ve 2.60× ATR projeksiyonlu kademeli direnç bandı.",
  };

  const invalidationRiskLevel = roundTo(
    Math.max(0.5, Math.min(supportLow - atr14 * 0.5, refPrice - atr14 * 1.5)),
    2
  );

  const riskDist = Math.max(0.01, refPrice - invalidationRiskLevel);
  const riskDistancePercent = roundTo((riskDist / refPrice) * 100, 2);
  const upsideToR1Percent = roundTo(((r1 - refPrice) / refPrice) * 100, 2);
  const upsideToR2Percent = roundTo(((r2 - refPrice) / refPrice) * 100, 2);
  const riskRewardToR1 = roundTo((r1 - refPrice) / riskDist, 2);
  const riskRewardToR2 = roundTo((r2 - refPrice) / riskDist, 2);

  const riskReward: RiskRewardMetricsResponse = {
    riskDistancePercent,
    upsideToR1Percent,
    upsideToR2Percent,
    riskRewardToR1,
    riskRewardToR2,
  };

  // Momentum breakout composite score (0-100)
  const techScore = analysis.finalScore ?? 50;
  let momentumBreakoutScore =
    techScore * 0.65 +
    (breakoutConfirmed ? 18 : Math.max(0, 12 - Math.abs(distanceToBreakoutPercent) * 2)) +
    Math.min(12, Math.max(0, (volRatio - 0.8) * 10)) +
    (latestRsi >= 52 && latestRsi <= 72 ? 5 : 0);
  momentumBreakoutScore = clampScore(momentumBreakoutScore);

  let category: SetupCategory = "CONSOLIDATION";
  if (latestRsi >= 73) {
    category = "OVEREXTENDED_RISK";
  } else if (
    momentumBreakoutScore >= 64 &&
    (breakoutConfirmed || Math.abs(distanceToBreakoutPercent) <= 2.5)
  ) {
    category = "HIGH_MOMENTUM_BREAKOUT";
  } else if (techScore >= 54 && refPrice <= supportWatchZone.high * 1.035) {
    category = "PULLBACK_WATCH";
  } else if (techScore <= 44 || analysis.trendDirection === "BEARISH") {
    category = "WEAK_BEARISH";
  }

  const rationaleItems: string[] = [
    `Bileşik teknik skor ${techScore.toFixed(1)}/100 ve momentum/kırılım skoru ${momentumBreakoutScore.toFixed(1)}/100.`,
    breakoutConfirmed
      ? `Fiyat (${refPrice.toFixed(2)} TRY), önceki 19 barlık yüksek seviye olan ${breakoutTrigger.toFixed(2)} TRY üzerinde kapanış gerçekleştirdi.`
      : `Önceki 19 barlık kırılım eşiği ${breakoutTrigger.toFixed(2)} TRY seviyesinde (mesafe: %${Math.abs(distanceToBreakoutPercent).toFixed(2)}).`,
    `ATR(14) oynaklığı ${atr14.toFixed(2)} TRY (%${atrPercent.toFixed(2)}); risk sınırı ${invalidationRiskLevel.toFixed(2)} TRY olarak hesaplandı.`,
  ];

  const riskWarnings: string[] = [];
  if (latestRsi >= 70) {
    riskWarnings.push(`RSI(14) ${latestRsi.toFixed(1)} ile aşırı alım bölgesinde; geri çekilme riski yüksek.`);
  }
  if (atrPercent >= 4) {
    riskWarnings.push(`Günlük ortalama oynaklık (ATR %${atrPercent.toFixed(2)}) yüksek seyrediyor.`);
  }
  if (riskRewardToR1 < 1.2) {
    riskWarnings.push(`R1 direncine göre Risk/Ödül oranı (1:${riskRewardToR1.toFixed(2)}) sınırlı marj gösteriyor.`);
  }
  if (riskWarnings.length === 0) {
    riskWarnings.push("Tüm seviyeler kural tabanlı istatistiksel referanslardır; piyasa koşulları hızla değişebilir.");
  }

  const headlineMap: Record<string, string> = {
    HIGH_MOMENTUM_BREAKOUT: `${norm} kırılım eşiği çevresinde yüksek momentum sergiliyor.`,
    PULLBACK_WATCH: `${norm} yükselen yapıda destek/izleme bandına yakın seyrediyor.`,
    OVEREXTENDED_RISK: `${norm} yüksek RSI nedeniyle kısa vadeli ısınma riski taşıyor.`,
    CONSOLIDATION: `${norm} yatay/sıkışma aralığında yön arayışında.`,
    WEAK_BEARISH: `${norm} zayıf momentum ve aşağı yönlü baskı altında.`,
  };

  return {
    symbol: norm,
    name: inst?.name ?? norm,
    exchange: BIST_EXCHANGE.code,
    sector: inst?.sector ?? null,
    currency: inst?.currency ?? "TRY",
    interval: barsRes.interval,
    adjusted,
    from: barsRes.from,
    to: barsRes.to,
    barCount: bars.length,
    setupEngineVersion: SETUP_ENGINE_VERSION,
    scoringAlgorithmVersion: SCORING_ALGORITHM_VERSION,
    indicatorEngineVersion: INDICATOR_ENGINE_VERSION,
    status: "READY",
    category,
    staleData: false,
    simulated: true,
    technicalScore: techScore,
    momentumBreakoutScore,
    regime: analysis.regime,
    trendDirection: analysis.trendDirection,
    technicalReferencePrice: refPrice,
    latestRsi,
    volumeRatio: volRatio,
    atr14,
    atrPercent,
    breakoutTriggerLevel: breakoutTrigger,
    distanceToBreakoutPercent,
    breakoutConfirmed,
    supportWatchZone,
    resistanceExitZone,
    invalidationRiskLevel,
    invalidationRationale:
      "Destek bandı altında 0.5× ATR ve referans fiyat altında 1.5× ATR kuralıyla türetilen risk sınırı.",
    riskReward,
    bistMargin,
    usedIndicators: ["EMA(20)", "SMA(20)", "RSI(14)", "MACD(12,26,9)", "ATR(14)", "VOLUME_AVG(20)", "DONCHIAN(19)"],
    rationaleItems,
    riskWarnings,
    insufficientDataReasons: [],
    headline: headlineMap[category] ?? `${norm} teknik kurulum özeti.`,
    calculatedAt: now.toISOString(),
    disclaimer: STANDARD_DISCLAIMER,
    dataSource: barsRes.dataSource,
    freshness: barsRes.freshness,
  };
}

export function toOpportunityItem(setup: InstrumentSetupResponse): OpportunityItemResponse {
  return {
    symbol: setup.symbol,
    name: setup.name,
    exchange: setup.exchange,
    sector: setup.sector,
    currency: setup.currency,
    status: setup.status,
    category: setup.category,
    staleData: setup.staleData,
    simulated: setup.simulated,
    technicalScore: setup.technicalScore,
    momentumBreakoutScore: setup.momentumBreakoutScore,
    regime: setup.regime,
    trendDirection: setup.trendDirection,
    technicalReferencePrice: setup.technicalReferencePrice,
    latestRsi: setup.latestRsi,
    volumeRatio: setup.volumeRatio,
    atr14: setup.atr14,
    atrPercent: setup.atrPercent,
    breakoutTriggerLevel: setup.breakoutTriggerLevel,
    distanceToBreakoutPercent: setup.distanceToBreakoutPercent,
    breakoutConfirmed: setup.breakoutConfirmed,
    supportWatchZone: setup.supportWatchZone,
    resistanceExitZone: setup.resistanceExitZone,
    invalidationRiskLevel: setup.invalidationRiskLevel,
    invalidationRationale: setup.invalidationRationale,
    riskReward: setup.riskReward,
    bistMargin: setup.bistMargin,
    usedIndicators: setup.usedIndicators,
    rationaleItems: setup.rationaleItems,
    riskWarnings: setup.riskWarnings,
    insufficientDataReasons: setup.insufficientDataReasons,
    headline: setup.headline,
    calculatedAt: setup.calculatedAt,
  };
}

// ============================================================================
// Deterministic Turkish AI Explanation Generator (Free Template Fallback)
// ============================================================================

export function generateAiExplanation(
  symbol: string,
  interval: string = "1d",
  adjusted: boolean = false,
  requestedScoreRunId?: string,
  now: Date = new Date()
): AiExplanationResponse {
  const analysis = calculateInstrumentAnalysis(symbol, interval, undefined, undefined, adjusted, now);
  const setup = calculateInstrumentSetup(symbol, interval, undefined, undefined, adjusted, now);
  const scoreRunId = requestedScoreRunId || analysis.scoreRunId;
  const explanationId = `ai-${hashSeed(`${analysis.symbol}|${scoreRunId}|${now.getTime()}`).toString(16).padStart(8, "0")}`;

  if (analysis.status !== "READY" || analysis.finalScore === null) {
    return {
      explanationId,
      analysisRunId: analysis.analysisRunId,
      scoreRunId,
      symbol: analysis.symbol,
      interval: analysis.interval,
      adjusted,
      providerId: "deterministic-template",
      requestedProviderId: "deterministic-template",
      modelName: "stockvision-template-tr-v1",
      promptVersion: AI_PROMPT_VERSION,
      status: "INSUFFICIENT_DATA",
      fallbackUsed: true,
      fallbackReason: "INSUFFICIENT_DATA_SHORT_CIRCUIT",
      simulatedData: true,
      staleData: false,
      generatedAt: now.toISOString(),
      explanation: {
        summary: `${analysis.symbol} için yeterli fiyat barı bulunmadığından doğal dil yorumu üretilmedi (INSUFFICIENT_DATA).`,
        observedFacts: analysis.insufficientDataReasons,
        interpretation: [],
        uncertaintiesAndRisks: [
          "Eksik veri ile üretilen yorumlar yanıltıcı olabileceğinden işlem kısa devre ile durduruldu.",
        ],
        confidenceNote: "Düşük / Veri yetersiz — yeni bar senkronizasyonu gerekir.",
      },
      dataSource: analysis.dataSource,
      freshness: analysis.freshness,
      disclaimer: STANDARD_DISCLAIMER,
    };
  }

  const topTwo = [...analysis.components]
    .sort((a, b) => (b.normalizedScore ?? 0) - (a.normalizedScore ?? 0))
    .slice(0, 2);
  const weakest = [...analysis.components].sort(
    (a, b) => (a.normalizedScore ?? 0) - (b.normalizedScore ?? 0)
  )[0];

  return {
    explanationId,
    analysisRunId: analysis.analysisRunId,
    scoreRunId,
    symbol: analysis.symbol,
    interval: analysis.interval,
    adjusted,
    providerId: "deterministic-template",
    requestedProviderId: "deterministic-template",
    modelName: "stockvision-template-tr-v1",
    promptVersion: AI_PROMPT_VERSION,
    status: "FALLBACK",
    fallbackUsed: true,
    fallbackReason: "TEMPLATE_MODE_SELECTED",
    simulatedData: true,
    staleData: false,
    generatedAt: now.toISOString(),
    explanation: {
      summary: `${analysis.symbol} için ${analysis.interval} ölçeğinde hesaplanan deterministik teknik skor ${analysis.finalScore.toFixed(1)}/100 düzeyindedir. ${analysis.explanation.headline}`,
      observedFacts: [
        `Son kapanış: ${analysis.latestClose?.toFixed(2) ?? "—"} TRY, RSI(14): ${analysis.latestRsi?.toFixed(2) ?? "—"}.`,
        ...topTwo.map((c) => `${c.type}: ${c.normalizedScore?.toFixed(1)}/100 — ${c.explanation}`),
        setup.breakoutTriggerLevel
          ? `19 barlık kırılım eşiği ${setup.breakoutTriggerLevel.toFixed(2)} TRY, ATR(14) risk sınırı ${setup.invalidationRiskLevel?.toFixed(2) ?? "—"} TRY.`
          : "Teknik seviye özeti hesaplandı.",
      ],
      interpretation: [
        `En güçlü teknik katkı ${topTwo[0]?.type ?? "TREND"} bileşeninden gelirken, en temkinli görünüm ${weakest?.type ?? "VOLUME"} bileşeninde izlenmektedir.`,
        setup.breakoutConfirmed
          ? "Fiyatın kısa vadeli direnç eşiğinin üzerinde kalıcılık sağlaması ve hacim oranının ortalamanın üzerinde korunması izlenebilir."
          : "Destek bandının korunup korunmadığı ve kırılım eşiğine yaklaşırken hacim artışı oluşup oluşmadığı takip edilmelidir.",
      ],
      uncertaintiesAndRisks: [
        ...setup.riskWarnings,
        "Bu özet yalnızca geçmiş/simüle fiyat verilerinden türetilen kural tabanlı bir açıklamadır; şirket haberleri, bilanço veya makro gelişmeleri kapsamaz.",
      ],
      confidenceNote:
        "Orta (Deterministik Şablon) — 7 teknik bileşen ve 20+ barlık veri seti üzerinden kural tabanlı olarak doğrulandı.",
    },
    dataSource: analysis.dataSource,
    freshness: analysis.freshness,
    disclaimer: STANDARD_DISCLAIMER,
  };
}
