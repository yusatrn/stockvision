import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { InstrumentSetupResponse } from "@/lib/api/types";
import { TechnicalLevelsCard } from "./technical-levels-card";

function makeSetup(
  overrides: Partial<InstrumentSetupResponse> = {}
): InstrumentSetupResponse {
  return {
    symbol: "THYAO",
    name: "Türk Hava Yolları",
    exchange: "BIST",
    sector: "Transportation",
    currency: "TRY",
    interval: "1d",
    adjusted: false,
    from: "2026-08-20T00:00:00Z",
    to: "2026-11-19T15:00:00Z",
    barCount: 62,
    setupEngineVersion: "setup-engine-v1.0.0",
    scoringAlgorithmVersion: "score-engine-v1.0.0",
    indicatorEngineVersion: "ind-v1.0.0",
    status: "READY",
    category: "HIGH_MOMENTUM_BREAKOUT",
    staleData: false,
    simulated: false,
    technicalScore: 78.5,
    momentumBreakoutScore: 81.25,
    regime: "STRONG_BULLISH_TECHNICAL",
    trendDirection: "BULLISH",
    technicalReferencePrice: 328.25,
    latestRsi: 63.4,
    volumeRatio: 1.65,
    atr14: 8.5,
    atrPercent: 2.59,
    breakoutTriggerLevel: 325.0,
    distanceToBreakoutPercent: -0.99,
    breakoutConfirmed: true,
    supportWatchZone: {
      low: 314.0,
      high: 320.0,
      label: "Teknik Destek / İzleme Bölgesi",
      rationale: "EMA(20) ve ATR tabanlı destek kümesi.",
    },
    resistanceExitZone: {
      low: 336.75,
      high: 349.5,
      label: "Teknik Direnç / Kâr Realizasyonu Referans Bölgesi",
      rationale: "R1 direnç ve R2 ATR uzama referansı.",
    },
    invalidationRiskLevel: 309.75,
    invalidationRationale: "Destek altı 0,50×ATR risk sınırı.",
    riskReward: {
      riskDistancePercent: 5.64,
      upsideToR1Percent: 2.59,
      upsideToR2Percent: 6.47,
      riskRewardToR1: 0.46,
      riskRewardToR2: 1.15,
    },
    bistMargin: {
      referencePrice: 328.25,
      previousClose: 320.0,
      dailyChangePercent: 2.58,
      theoreticalCeilingPrice: 352.0,
      theoreticalFloorPrice: 288.0,
      distanceToCeilingPercent: 7.24,
      distanceToFloorPercent: 12.26,
      note: "BIST ±%10 günlük teorik fiyat marjı referansıdır; tavan veya taban tahmini değildir.",
    },
    usedIndicators: ["SMA(20)", "EMA(20)", "RSI(14)", "ATR(14)", "BOLLINGER(20,2)"],
    rationaleItems: ["Kapanış 19 barlık direnç eşiği üzerinde kırılım gerçekleştirdi."],
    riskWarnings: ["R1 risk/ödül oranı 1,00 altındadır."],
    insufficientDataReasons: [],
    headline: "THYAO: Momentum Skoru 81.25/100 · Kırılım profili.",
    calculatedAt: "2026-11-19T15:05:00Z",
    disclaimer: "Bu analiz tavan tahmini veya al/sat tavsiyesi değildir.",
    dataSource: {
      providerId: "YAHOO_FINANCE_DELAYED",
      displayName: "Yahoo Finance Gecikmeli BIST Verisi",
      mode: "DELAYED",
      simulated: false,
      delayMinutes: 15,
      sourceTimezone: "Europe/Istanbul",
      licenseNote: "15 dk gecikmeli BIST verisi.",
    },
    freshness: {
      status: "FRESH",
      stale: false,
      simulated: false,
      latestDataTimestamp: "2026-11-19T15:00:00Z",
      lastSyncedAt: "2026-11-19T15:05:00Z",
      lastSyncStatus: "SUCCESS",
      ageMinutes: 5,
      note: "Güncel veri",
    },
    ...overrides,
  };
}

describe("TechnicalLevelsCard", () => {
  it("renders support watch zone, resistance realization zone, stop reference, risk/reward, and BIST margin", () => {
    render(<TechnicalLevelsCard setup={makeSetup()} timezone="Europe/Istanbul" />);

    expect(
      screen.getByText("Teknik Seviyeler, Destek/Direnç Bölgeleri ve Risk Sınırı")
    ).toBeInTheDocument();
    expect(screen.getByText("Yüksek Momentum / Kırılım Adayı")).toBeInTheDocument();
    expect(screen.getByText("Destek / İzleme Bölgesi")).toBeInTheDocument();
    expect(screen.getByText("Direnç / Kâr Realizasyonu (R1–R2)")).toBeInTheDocument();
    expect(screen.getByText("Risk Sınırı (Stop Ref.) & R/Ö")).toBeInTheDocument();
    expect(screen.getAllByText(/352,00/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/288,00/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/1 : 1,15/)).toBeInTheDocument();
    expect(screen.getByText("ATR(14)")).toBeInTheDocument();
  });

  it("renders INSUFFICIENT_DATA warning without fabricated zones when data is insufficient", () => {
    const insufficient = makeSetup({
      status: "INSUFFICIENT_DATA",
      category: "INSUFFICIENT_DATA",
      momentumBreakoutScore: null,
      technicalScore: null,
      supportWatchZone: null,
      resistanceExitZone: null,
      invalidationRiskLevel: null,
      riskReward: null,
      insufficientDataReasons: ["ATR(14) serisi hazır ve pozitif olmalıdır."],
      headline: "THYAO teknik seviye analizi INSUFFICIENT_DATA olarak durduruldu.",
    });

    render(<TechnicalLevelsCard setup={insufficient} timezone="Europe/Istanbul" />);

    expect(
      screen.getByText("Yetersiz Veri (INSUFFICIENT_DATA) · Teknik Seviyeler Üretilmedi")
    ).toBeInTheDocument();
    expect(
      screen.getByText("ATR(14) serisi hazır ve pozitif olmalıdır.")
    ).toBeInTheDocument();
  });
});
