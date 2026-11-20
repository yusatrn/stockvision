import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { InstrumentAnalysisResponse } from "@/lib/api/types";
import { ScoreBreakdown } from "./score-breakdown";

function makeBaseAnalysis(): InstrumentAnalysisResponse {
  return {
    scoreRunId: "run-1",
    analysisRunId: "ana-1",
    symbol: "THYAO",
    exchange: "BIST",
    interval: "1d",
    adjusted: false,
    from: "2026-08-01T00:00:00Z",
    to: "2026-11-19T00:00:00Z",
    barCount: 90,
    algorithmVersion: "score-engine-v1.0.0",
    indicatorEngineVersion: "ta-engine-v1.0.0",
    status: "READY",
    staleData: false,
    finalScore: 72.5,
    regime: "BULLISH",
    trendDirection: "BULLISH",
    latestClose: 298.5,
    latestRsi: 58.4,
    insufficientDataReasons: [],
    weights: { TREND: 25, RSI: 20 },
    components: [
      {
        type: "TREND",
        status: "READY",
        weightPercent: 25,
        normalizedScore: 80,
        weightedContribution: 20,
        explanation: "Price trades above short and medium moving averages.",
        metrics: { sma20: 285.1, close: 298.5 },
      },
      {
        type: "RSI",
        status: "READY",
        weightPercent: 20,
        normalizedScore: 65,
        weightedContribution: 13,
        explanation: "RSI rests in constructive bullish momentum zone.",
        metrics: { rsi14: 58.4 },
      },
    ],
    explanation: {
      headline: "Constructive bullish alignment across trend and momentum.",
      keyDrivers: ["Close remains above SMA(20) and EMA(20)."],
      riskAndDataNotes: ["Simulated market feed active."],
      methodologySummary: "Deterministic weighted composite across 7 technical dimensions.",
      disclaimer: "Decision-support output only; not investment advice.",
    },
    calculatedAt: "2026-11-19T12:00:00Z",
    dataSource: {
      providerId: "mock-bist",
      displayName: "Mock Provider",
      mode: "SIMULATED",
      simulated: true,
      delayMinutes: 0,
      sourceTimezone: "Europe/Istanbul",
      licenseNote: "Simulated",
    },
    freshness: {
      status: "SIMULATED_ONLY",
      stale: false,
      simulated: true,
      latestDataTimestamp: "2026-11-19T12:00:00Z",
      lastSyncedAt: "2026-11-19T12:00:00Z",
      lastSyncStatus: "SUCCESS",
      ageMinutes: 2,
      note: "Mock dataset",
    },
  };
}

describe("ScoreBreakdown", () => {
  it("renders explainable composite score, drivers, components, and disclaimer", () => {
    render(<ScoreBreakdown analysis={makeBaseAnalysis()} timezone="Europe/Istanbul" />);

    expect(screen.getByText("72,5 / 100")).toBeInTheDocument();
    expect(
      screen.getByText("Constructive bullish alignment across trend and momentum.")
    ).toBeInTheDocument();
    expect(
      screen.getByText("Close remains above SMA(20) and EMA(20).")
    ).toBeInTheDocument();
    expect(screen.getByText("Ağırlık %25")).toBeInTheDocument();
    expect(
      screen.getByText("Decision-support output only; not investment advice.")
    ).toBeInTheDocument();
  });

  it("displays explicit INSUFFICIENT_DATA and stale data warnings when flagged", () => {
    const base = makeBaseAnalysis();
    const insufficient: InstrumentAnalysisResponse = {
      ...base,
      status: "INSUFFICIENT_DATA",
      staleData: true,
      finalScore: null,
      regime: "INSUFFICIENT_DATA",
      trendDirection: "INSUFFICIENT_DATA",
      insufficientDataReasons: [
        "Available bar count (12) is below required 35 bars for composite scoring.",
      ],
      components: [
        {
          type: "TREND",
          status: "INSUFFICIENT_DATA",
          weightPercent: 25,
          normalizedScore: null,
          weightedContribution: null,
          explanation: "Missing EMA(20) warm-up history.",
          metrics: {},
        },
      ],
    };

    render(<ScoreBreakdown analysis={insufficient} timezone="UTC" />);

    expect(
      screen.getByText("Analiz Durumu: Yetersiz Veri (INSUFFICIENT_DATA)")
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Available bar count (12) is below required 35 bars for composite scoring."
      )
    ).toBeInTheDocument();
    expect(screen.getByText("Bayat Piyasa Verisi Uyarısı")).toBeInTheDocument();
  });
});
