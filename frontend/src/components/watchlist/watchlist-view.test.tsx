import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "@/lib/api/client";
import type { WatchlistCollectionResponse } from "@/lib/api/types";
import { WatchlistView } from "./watchlist-view";

const listMock = vi.fn();
const addItemMock = vi.fn();

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
    watchlists: {
      list: (...args: unknown[]) => listMock(...args),
      create: vi.fn(),
      update: vi.fn(),
      remove: vi.fn(),
      addItem: (...args: unknown[]) => addItemMock(...args),
      removeItem: vi.fn(),
    },
  },
}));

function makeCollection(): WatchlistCollectionResponse {
  return {
    watchlists: [
      {
        id: "wl-1",
        name: "BIST Core",
        description: "Core large-cap tracking",
        createdAt: "2026-11-20T08:00:00Z",
        updatedAt: "2026-11-20T08:00:00Z",
        items: [
          {
            id: "item-1",
            instrumentId: "inst-1",
            symbol: "THYAO",
            name: "Turkish Airlines",
            exchange: "BIST",
            sector: "Transportation",
            currency: "TRY",
            notes: "Support watch",
            createdAt: "2026-11-20T08:01:00Z",
            latestQuote: {
              price: 305.5,
              changeAmount: 4.5,
              changePercent: 1.49,
              volume: 12500000,
              currency: "TRY",
              quotedAt: "2026-11-20T08:30:00Z",
              simulated: true,
            },
          },
        ],
      },
      {
        id: "wl-2",
        name: "Banking Watch",
        description: null,
        createdAt: "2026-11-20T08:02:00Z",
        updatedAt: "2026-11-20T08:02:00Z",
        items: [],
      },
    ],
    dataSource: {
      providerId: "mock",
      displayName: "Mock BIST Provider",
      mode: "SIMULATED",
      simulated: true,
      delayMinutes: 0,
      sourceTimezone: "Europe/Istanbul",
      licenseNote: "Simulated test feed",
    },
    freshness: {
      status: "SIMULATED_ONLY",
      stale: false,
      simulated: true,
      latestDataTimestamp: "2026-11-20T08:30:00Z",
      lastSyncedAt: null,
      lastSyncStatus: null,
      ageMinutes: 1,
      note: "Simulated deterministic feed",
    },
  };
}

describe("WatchlistView", () => {
  beforeEach(() => {
    listMock.mockReset();
    addItemMock.mockReset();
  });

  it("renders multiple watchlists, switches active tab, and handles duplicate item conflict", async () => {
    const user = userEvent.setup();
    listMock.mockResolvedValue(makeCollection());
    addItemMock.mockRejectedValueOnce(
      new ApiClientError({
        status: 409,
        code: "CONFLICT",
        message: "Instrument 'THYAO' is already present in this watchlist",
      })
    );

    render(<WatchlistView />);

    await waitFor(() => {
      expect(screen.getByText("THYAO")).toBeInTheDocument();
    });

    expect(screen.getByRole("tab", { name: /BIST Core/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Banking Watch/ })).toBeInTheDocument();

    await user.type(screen.getByLabelText("Hisse Sembolü Ekle"), "thyao");
    await user.click(screen.getByRole("button", { name: "Sembol Ekle" }));

    await waitFor(() => {
      expect(
        screen.getByText("Instrument 'THYAO' is already present in this watchlist")
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("tab", { name: /Banking Watch/ }));
    expect(
      screen.getByText(/Bu izleme listesinde henüz enstrüman bulunmuyor/)
    ).toBeInTheDocument();
  });
});
