import test from "node:test";
import assert from "node:assert/strict";
import {
  buildIndicatorSeries,
  calculateInstrumentAnalysis,
  calculateInstrumentSetup,
  generateAiExplanation,
  generateHistoricalBars,
  getLatestQuote,
  isBistTradingDay,
} from "./market-engine";
import { calculatePortfolioLedger, StoreError } from "./store";

test("generateHistoricalBars produces valid deterministic OHLCV bars excluding weekends and BIST holidays", () => {
  const fixedNow = new Date("2026-11-20T16:00:00.000Z");
  const res1 = generateHistoricalBars("THYAO", "1d", undefined, undefined, false, fixedNow);
  const res2 = generateHistoricalBars("THYAO", "1d", undefined, undefined, false, fixedNow);

  assert.equal(res1.symbol, "THYAO");
  assert.ok(res1.bars.length >= 200);
  assert.deepEqual(res1.bars, res2.bars);

  for (const bar of res1.bars) {
    assert.ok(bar.low <= Math.min(bar.open, bar.close), "low <= min(open, close)");
    assert.ok(bar.high >= Math.max(bar.open, bar.close), "high >= max(open, close)");
    assert.ok(bar.volume > 0, "positive volume");
    assert.equal(isBistTradingDay(new Date(bar.timestamp)), true);
  }
});

test("buildIndicatorSeries marks INSUFFICIENT_DATA when bars are below required warm-up", () => {
  const fixedNow = new Date("2026-11-20T16:00:00.000Z");
  const barsRes = generateHistoricalBars("ASELS", "1d", undefined, undefined, false, fixedNow);
  const shortSlice = barsRes.bars.slice(0, 10);

  const smaShort = buildIndicatorSeries("SMA", shortSlice);
  assert.equal(smaShort.status, "INSUFFICIENT_DATA");

  const rsiFull = buildIndicatorSeries("RSI", barsRes.bars);
  assert.equal(rsiFull.status, "READY");
  assert.ok(
    typeof rsiFull.latestValues.value === "number" &&
      rsiFull.latestValues.value >= 0 &&
      rsiFull.latestValues.value <= 100
  );
});

test("calculateInstrumentAnalysis produces 0-100 score with component weights summing to 100", () => {
  const fixedNow = new Date("2026-11-20T16:00:00.000Z");
  const analysis = calculateInstrumentAnalysis("GARAN", "1d", undefined, undefined, false, fixedNow);

  assert.equal(analysis.status, "READY");
  assert.ok(analysis.finalScore !== null && analysis.finalScore >= 0 && analysis.finalScore <= 100);
  const totalWeight = analysis.components.reduce((sum, c) => sum + c.weightPercent, 0);
  assert.equal(totalWeight, 100);
  assert.ok(analysis.explanation.disclaimer.includes("yatırım danışmanlığı"));
});

test("calculateInstrumentSetup computes valid stop < support < resistance ladder and BIST ±10% margin", () => {
  const fixedNow = new Date("2026-11-20T16:00:00.000Z");
  const setup = calculateInstrumentSetup("TUPRS", "1d", undefined, undefined, false, fixedNow);

  assert.equal(setup.status, "READY");
  assert.ok(setup.supportWatchZone !== null);
  assert.ok(setup.resistanceExitZone !== null);
  assert.ok(setup.invalidationRiskLevel !== null);
  assert.ok(setup.bistMargin !== null);
  assert.ok(setup.invalidationRiskLevel! < setup.technicalReferencePrice!);
  assert.ok(setup.resistanceExitZone!.low < setup.resistanceExitZone!.high);
  assert.ok(setup.bistMargin!.theoreticalFloorPrice < setup.bistMargin!.theoreticalCeilingPrice);
});

test("generateAiExplanation returns deterministic Turkish template grounded in analysis", () => {
  const fixedNow = new Date("2026-11-20T16:00:00.000Z");
  const ai = generateAiExplanation("THYAO", "1d", false, undefined, fixedNow);
  assert.equal(ai.providerId, "deterministic-template");
  assert.equal(ai.status, "FALLBACK");
  assert.ok(ai.explanation.summary.includes("THYAO"));
  assert.ok(ai.explanation.observedFacts.length > 0);
});

test("calculatePortfolioLedger computes weighted average cost, realized PnL, and blocks short sales", () => {
  const q = getLatestQuote("THYAO");
  assert.ok(q.price > 0);

  const ledger = calculatePortfolioLedger(
    [
      {
        id: "t1",
        instrumentId: "id-1",
        symbol: "THYAO",
        instrumentName: "THY",
        side: "BUY",
        quantity: 100,
        unitPrice: 200,
        feeAmount: 10,
        taxAmount: 0,
        currency: "TRY",
        executedAt: "2026-11-01T10:00:00.000Z",
        notes: null,
        createdAt: "2026-11-01T10:00:00.000Z",
      },
      {
        id: "t2",
        instrumentId: "id-1",
        symbol: "THYAO",
        instrumentName: "THY",
        side: "SELL",
        quantity: 40,
        unitPrice: 250,
        feeAmount: 5,
        taxAmount: 0,
        currency: "TRY",
        executedAt: "2026-11-10T10:00:00.000Z",
        notes: null,
        createdAt: "2026-11-10T10:00:00.000Z",
      },
    ],
    "EXPLICIT_PER_TXN"
  );

  const pos = ledger.positions.get("THYAO")!;
  assert.equal(pos.openQuantity, 60);
  // Buy cost = 20000 + 10 = 20010 -> avg 200.1 -> sold 40 = 8004 -> proceeds 10000 - 5 = 9995 -> realized = 1991
  assert.equal(ledger.totalRealizedPnl, 1991);

  assert.throws(
    () =>
      calculatePortfolioLedger(
        [
          {
            id: "t3",
            instrumentId: "id-1",
            symbol: "THYAO",
            instrumentName: "THY",
            side: "SELL",
            quantity: 10,
            unitPrice: 200,
            feeAmount: 0,
            taxAmount: 0,
            currency: "TRY",
            executedAt: "2026-11-12T10:00:00.000Z",
            notes: null,
            createdAt: "2026-11-12T10:00:00.000Z",
          },
        ],
        "EXCLUDED_MVP"
      ),
    (err: unknown) => err instanceof StoreError && err.status === 409
  );
});
