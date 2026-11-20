import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type {
  PortfolioPerformanceResponse,
  PortfolioSummaryResponse,
  PortfolioTransactionResponse,
} from "@/lib/api/types";
import { PortfolioView } from "./portfolio-view";

const listMock = vi.fn();
const perfMock = vi.fn();
const txListMock = vi.fn();
const addTxMock = vi.fn();

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
    portfolios: {
      list: (...args: unknown[]) => listMock(...args),
      create: vi.fn(),
      remove: vi.fn(),
      getPerformance: (...args: unknown[]) => perfMock(...args),
      listTransactions: (...args: unknown[]) => txListMock(...args),
      addTransaction: (...args: unknown[]) => addTxMock(...args),
      removeTransaction: vi.fn(),
    },
  },
}));

function makeSummary(): PortfolioSummaryResponse {
  return {
    id: "pf-1",
    name: "BIST Virtual",
    description: "Long-only simulation",
    baseCurrency: "TRY",
    feeTaxPolicy: "EXCLUDED_MVP",
    openPositionsCount: 1,
    transactionCount: 2,
    createdAt: "2026-11-20T08:00:00Z",
    updatedAt: "2026-11-20T08:00:00Z",
  };
}

function makePerformance(): PortfolioPerformanceResponse {
  return {
    portfolioId: "pf-1",
    name: "BIST Virtual",
    description: "Long-only simulation",
    baseCurrency: "TRY",
    feeTaxPolicy: "EXCLUDED_MVP",
    feeTaxPolicyNote:
      "Commission fees and transaction taxes are explicitly excluded under EXCLUDED_MVP.",
    valuationStatus: "READY",
    totalCostBasis: 1200,
    totalMarketValue: 1500,
    totalUnrealizedPnl: 300,
    totalUnrealizedPnlPercent: 25,
    totalRealizedPnl: 180,
    totalNetPnl: 480,
    totalFees: 0,
    totalTaxes: 0,
    openPositionsCount: 1,
    closedPositionsCount: 0,
    transactionCount: 2,
    holdings: [
      {
        instrumentId: "inst-1",
        symbol: "THYAO",
        name: "Turkish Airlines",
        exchange: "BIST",
        currency: "TRY",
        openQuantity: 10,
        averageCost: 120,
        costBasis: 1200,
        latestPrice: 150,
        quotedAt: "2026-11-20T08:30:00Z",
        simulated: true,
        marketValue: 1500,
        unrealizedPnl: 300,
        unrealizedPnlPercent: 25,
        realizedPnl: 180,
        totalFees: 0,
        totalTaxes: 0,
      },
    ],
    calculatedAt: "2026-11-20T08:30:00Z",
    dataSource: {
      providerId: "mock",
      displayName: "Mock Provider",
      mode: "SIMULATED",
      simulated: true,
      delayMinutes: 0,
      sourceTimezone: "Europe/Istanbul",
      licenseNote: "Mock",
    },
    freshness: {
      status: "SIMULATED_ONLY",
      stale: false,
      simulated: true,
      latestDataTimestamp: "2026-11-20T08:30:00Z",
      lastSyncedAt: null,
      lastSyncStatus: null,
      ageMinutes: 0,
      note: "Simulated quotes",
    },
    disclaimer: "Virtual portfolio simulation for decision support only.",
  };
}

function makeTransactions(): PortfolioTransactionResponse[] {
  return [
    {
      id: "tx-1",
      instrumentId: "inst-1",
      symbol: "THYAO",
      instrumentName: "Turkish Airlines",
      side: "BUY",
      quantity: 10,
      unitPrice: 120,
      grossAmount: 1200,
      feeAmount: 0,
      taxAmount: 0,
      currency: "TRY",
      executedAt: "2026-11-18T10:00:00Z",
      notes: "Core buy",
      createdAt: "2026-11-18T10:00:00Z",
    },
  ];
}

describe("PortfolioView", () => {
  beforeEach(() => {
    listMock.mockReset();
    perfMock.mockReset();
    txListMock.mockReset();
    addTxMock.mockReset();
  });

  it("renders separate realized vs unrealized P&L, disables fees in EXCLUDED_MVP, and submits transaction", async () => {
    const user = userEvent.setup();
    listMock.mockResolvedValue([makeSummary()]);
    perfMock.mockResolvedValue(makePerformance());
    txListMock.mockResolvedValue(makeTransactions());
    addTxMock.mockResolvedValueOnce({
      ...makeTransactions()[0],
      id: "tx-2",
    });

    render(<PortfolioView />);

    await waitFor(() => {
      expect(screen.getByText("BIST Virtual Performans Özeti")).toBeInTheDocument();
    });

    expect(screen.getByText("Gerçekleşmemiş K/Z (Açık)")).toBeInTheDocument();
    expect(screen.getByText("Gerçekleşmiş K/Z (Kapalı İşlemler)")).toBeInTheDocument();
    expect(
      screen.getByText(/explicitly excluded under EXCLUDED_MVP/)
    ).toBeInTheDocument();

    expect(screen.getByLabelText(/Komisyon \(Hariç\)/)).toBeDisabled();
    expect(screen.getByLabelText(/Vergi \(Hariç\)/)).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "İşlem Ekle" }));

    await waitFor(() => {
      expect(addTxMock).toHaveBeenCalledWith(
        "pf-1",
        expect.objectContaining({
          symbol: "THYAO",
          side: "BUY",
          quantity: 10,
          unitPrice: 100,
          feeAmount: 0,
          taxAmount: 0,
        })
      );
    });
  });
});
