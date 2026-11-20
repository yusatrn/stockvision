import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ScreenerPageResponse } from "@/lib/api/types";
import { ScreenerView } from "./screener-view";

const screenMock = vi.fn();

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
    screener: {
      screen: (...args: unknown[]) => screenMock(...args),
    },
  },
}));

function makeScreenerResponse(items = [
  {
    scoreRunId: "sr-1",
    symbol: "THYAO",
    name: "Turkish Airlines",
    exchange: "BIST",
    sector: "Transportation",
    currency: "TRY",
    status: "READY" as const,
    finalScore: 76.2,
    regime: "BULLISH",
    trendDirection: "BULLISH",
    latestClose: 310.5,
    latestRsi: 61.2,
    simulated: true,
    staleData: false,
    headline: "Strong upward momentum above moving averages.",
    calculatedAt: "2026-11-19T12:00:00Z",
  },
]): ScreenerPageResponse {
  return {
    exchange: "BIST",
    interval: "1d",
    adjusted: false,
    algorithmVersion: "score-engine-v1.0.0",
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length > 0 ? 1 : 0,
    disclaimer: "Educational decision support only.",
    dataSource: {
      providerId: "mock-bist",
      displayName: "Mock Provider",
      mode: "SIMULATED",
      simulated: true,
      delayMinutes: 0,
      sourceTimezone: "Europe/Istanbul",
      licenseNote: "Mock feed",
    },
    freshness: {
      status: "SIMULATED_ONLY",
      stale: false,
      simulated: true,
      latestDataTimestamp: "2026-11-19T12:00:00Z",
      lastSyncedAt: "2026-11-19T12:00:00Z",
      lastSyncStatus: "SUCCESS",
      ageMinutes: 5,
      note: "Simulated market snapshot",
    },
    items,
  };
}

describe("ScreenerView", () => {
  beforeEach(() => {
    screenMock.mockReset();
  });

  it("renders screened instruments and allows submitting filter updates", async () => {
    const user = userEvent.setup();
    screenMock.mockResolvedValue(makeScreenerResponse());

    render(<ScreenerView />);

    await waitFor(() => {
      expect(screen.getByText("THYAO")).toBeInTheDocument();
    });
    expect(screen.getByText("76,2 / 100")).toBeInTheDocument();
    expect(screen.getByText("Educational decision support only.")).toBeInTheDocument();

    const minScoreInput = screen.getByLabelText("Min. Skor (0–100)");
    await user.clear(minScoreInput);
    await user.type(minScoreInput, "60");
    await user.selectOptions(screen.getByLabelText("Hisse Kapsamı"), "BIST100");
    await user.type(screen.getByLabelText("Sembol / Şirket"), "PGSUS");
    await user.selectOptions(screen.getByLabelText("Trend Yönü"), "BULLISH");
    await user.click(screen.getByRole("button", { name: "Filtreleri Uygula" }));

    await waitFor(() => {
      expect(screenMock).toHaveBeenCalledTimes(2);
    });
    expect(screenMock.mock.calls[1][0]).toMatchObject({
      exchange: "BIST",
      interval: "1d",
      scope: "BIST100",
      query: "PGSUS",
      minScore: 60,
      trend: "BULLISH",
      page: 0,
    });
  });

  it("blocks invalid score bounds locally before calling API", async () => {
    const user = userEvent.setup();
    screenMock.mockResolvedValue(makeScreenerResponse());

    render(<ScreenerView />);
    await waitFor(() => {
      expect(screen.getByText("THYAO")).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText("Min. Skor (0–100)"), "80");
    await user.type(screen.getByLabelText("Maks. Skor (0–100)"), "40");
    await user.click(screen.getByRole("button", { name: "Filtreleri Uygula" }));

    expect(
      screen.getByText("Minimum skor, maksimum skordan büyük olamaz.")
    ).toBeInTheDocument();
    expect(screenMock).toHaveBeenCalledTimes(1);
  });

  it("shows honest empty state when screener returns zero rows", async () => {
    screenMock.mockResolvedValueOnce(makeScreenerResponse([]));

    render(<ScreenerView />);

    await waitFor(() => {
      expect(
        screen.getByText("Tarayıcı ölçütlerinizle eşleşen enstrüman bulunamadı.")
      ).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: "Tüm Filtreleri Sıfırla" })
    ).toBeInTheDocument();
  });
});
