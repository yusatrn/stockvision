import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { OpportunitiesPageResponse } from "@/lib/api/types";
import { OpportunitiesView } from "./opportunities-view";

const listMock = vi.fn();
const getSyncStatusMock = vi.fn();
const syncBatchMock = vi.fn();

vi.mock("@/components/providers/auth-provider", () => ({
  useAuth: () => ({
    effectiveTimezone: "Europe/Istanbul",
    status: "authenticated",
    user: null,
    token: "mock-token",
    sessionNotice: null,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshProfile: vi.fn(),
    setDisplayTimezone: vi.fn(),
    clearSessionNotice: vi.fn(),
  }),
}));

vi.mock("@/lib/api/services", () => ({
  apiServices: {
    opportunities: {
      list: (...args: unknown[]) => listMock(...args),
    },
    marketData: {
      getSyncStatus: (...args: unknown[]) => getSyncStatusMock(...args),
      syncBatch: (...args: unknown[]) => syncBatchMock(...args),
    },
  },
}));

function makeOpportunitiesResponse(
  includeItem = true
): OpportunitiesPageResponse {
  return {
    exchange: "BIST",
    interval: "1d",
    adjusted: false,
    setupEngineVersion: "setup-engine-v1.0.0",
    scoringAlgorithmVersion: "score-engine-v1.0.0",
    page: 0,
    size: 20,
    totalElements: includeItem ? 1 : 0,
    totalPages: includeItem ? 1 : 0,
    categoryCounts: {
      HIGH_MOMENTUM_BREAKOUT: includeItem ? 1 : 0,
      PULLBACK_WATCH: 2,
      OVEREXTENDED_RISK: 1,
      CONSOLIDATION: 4,
      WEAK_BEARISH: 2,
      INSUFFICIENT_DATA: 0,
    },
    disclaimer: "Tavan tahmini veya yatırım tavsiyesi değildir.",
    dataSource: {
      providerId: "YAHOO_FINANCE_DELAYED",
      displayName: "Yahoo Finance Gecikmeli BIST Verisi",
      mode: "DELAYED",
      simulated: false,
      delayMinutes: 15,
      sourceTimezone: "Europe/Istanbul",
      licenseNote: "15 dk gecikmeli veri",
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
    items: includeItem
      ? [
          {
            symbol: "ASELS",
            name: "Aselsan Elektronik",
            exchange: "BIST",
            sector: "Defense & Electronics",
            currency: "TRY",
            status: "READY",
            category: "HIGH_MOMENTUM_BREAKOUT",
            staleData: false,
            simulated: false,
            technicalScore: 79.4,
            momentumBreakoutScore: 84.2,
            regime: "STRONG_BULLISH_TECHNICAL",
            trendDirection: "BULLISH",
            technicalReferencePrice: 337.0,
            latestRsi: 64.5,
            volumeRatio: 1.85,
            atr14: 9.2,
            atrPercent: 2.73,
            breakoutTriggerLevel: 334.5,
            distanceToBreakoutPercent: -0.74,
            breakoutConfirmed: true,
            supportWatchZone: {
              low: 322.0,
              high: 329.0,
              label: "Teknik Destek / İzleme Bölgesi",
              rationale: "EMA ve destek bandı.",
            },
            resistanceExitZone: {
              low: 348.0,
              high: 361.8,
              label: "Teknik Direnç / Kâr Realizasyonu Referans Bölgesi",
              rationale: "R1 ve R2 ATR referansı.",
            },
            invalidationRiskLevel: 317.4,
            invalidationRationale: "Destek altı stop referansı.",
            riskReward: {
              riskDistancePercent: 5.82,
              upsideToR1Percent: 3.26,
              upsideToR2Percent: 7.36,
              riskRewardToR1: 0.56,
              riskRewardToR2: 1.27,
            },
            bistMargin: {
              referencePrice: 337.0,
              previousClose: 330.0,
              dailyChangePercent: 2.12,
              theoreticalCeilingPrice: 363.0,
              theoreticalFloorPrice: 297.0,
              distanceToCeilingPercent: 7.72,
              distanceToFloorPercent: 11.87,
              note: "BIST ±%10 günlük teorik fiyat marjı referansıdır; tavan veya taban tahmini değildir.",
            },
            usedIndicators: ["EMA(20)", "RSI(14)", "ATR(14)"],
            rationaleItems: ["Önceki 19 barlık eşik üzerinde kırılım."],
            riskWarnings: [],
            insufficientDataReasons: [],
            headline: "ASELS: Momentum Skoru 84.20/100 · Kırılım profili.",
            calculatedAt: "2026-11-19T15:05:00Z",
          },
        ]
      : [],
  };
}

describe("OpportunitiesView", () => {
  beforeEach(() => {
    listMock.mockReset();
    getSyncStatusMock.mockReset();
    syncBatchMock.mockReset();
    getSyncStatusMock.mockResolvedValue({
      exchange: "BIST",
      interval: "1d",
      providerId: "YAHOO_FINANCE_DELAYED",
      totalActiveInstruments: 625,
      syncedInstruments: 30,
      remainingInstruments: 595,
    });
    syncBatchMock.mockResolvedValue({
      exchange: "BIST",
      interval: "1d",
      providerId: "YAHOO_FINANCE_DELAYED",
      totalActiveInstruments: 625,
      syncedInstruments: 55,
      remainingInstruments: 570,
      processedInBatch: 25,
      succeededInBatch: 25,
      failedInBatch: 0,
      syncedSymbols: ["SASA", "PGSUS"],
      failedSymbols: [],
    });
  });

  it("renders KPI category counts, sync strip, and updates query on scope, search, category, and sort controls", async () => {
    const user = userEvent.setup();
    listMock.mockResolvedValue(makeOpportunitiesResponse(true));

    render(<OpportunitiesView />);

    await waitFor(() => {
      expect(screen.getByText("ASELS")).toBeInTheDocument();
    });

    expect(
      screen.getByText("Tavan tahmini veya yatırım tavsiyesi değildir.")
    ).toBeInTheDocument();
    expect(screen.getByText("Momentum: 84,2 / 100")).toBeInTheDocument();
    expect(screen.getAllByText(/363,00/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("30 / 625")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "+25 Hisse Senkronize Et" })
    );
    await waitFor(() => {
      expect(syncBatchMock).toHaveBeenCalledTimes(1);
    });
    expect(screen.getByText(/25 yeni hisse verisi çekildi/)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Hisse kapsamı"), "BIST100");
    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ scope: "BIST100" }),
        expect.anything()
      );
    });

    await user.type(screen.getByLabelText("Tüm BIST içinde ara"), "PGSUS");
    await user.click(screen.getByRole("button", { name: "Ara" }));
    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ query: "PGSUS", page: 0 }),
        expect.anything()
      );
    });

    await user.click(
      screen.getByRole("button", { name: "Destek / Geri Çekilme (2)" })
    );

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        expect.objectContaining({
          exchange: "BIST",
          interval: "1d",
          category: "PULLBACK_WATCH",
          page: 0,
        }),
        expect.anything()
      );
    });

    await user.selectOptions(
      screen.getByLabelText("Sıralama ölçütü"),
      "ceilingDistance"
    );

    await waitFor(() => {
      expect(listMock).toHaveBeenLastCalledWith(
        expect.objectContaining({
          sortBy: "ceilingDistance",
          sortDir: "asc",
        }),
        expect.anything()
      );
    });
  });

  it("renders honest empty state and allows resetting filters", async () => {
    listMock.mockResolvedValueOnce(makeOpportunitiesResponse(false));

    render(<OpportunitiesView />);

    await waitFor(() => {
      expect(
        screen.getByText(
          "Seçili kategori veya filtre ölçütleriyle eşleşen teknik kurulum bulunamadı."
        )
      ).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: "Filtreleri Sıfırla" })
    ).toBeInTheDocument();
  });
});
