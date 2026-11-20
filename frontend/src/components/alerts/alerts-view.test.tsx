import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type {
  AlertEvaluationRunResponse,
  AlertRuleListResponse,
  NotificationPageResponse,
} from "@/lib/api/types";
import { AlertsView } from "./alerts-view";

const listAlertsMock = vi.fn();
const evaluateNowMock = vi.fn();
const listNotifsMock = vi.fn();
const markReadMock = vi.fn();

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
    alerts: {
      list: (...args: unknown[]) => listAlertsMock(...args),
      create: vi.fn(),
      update: vi.fn(),
      remove: vi.fn(),
      evaluateNow: (...args: unknown[]) => evaluateNowMock(...args),
    },
    notifications: {
      list: (...args: unknown[]) => listNotifsMock(...args),
      markRead: (...args: unknown[]) => markReadMock(...args),
      markAllRead: vi.fn(),
    },
  },
}));

function makeRules(): AlertRuleListResponse {
  return {
    alerts: [
      {
        id: "rule-1",
        instrumentId: "inst-1",
        symbol: "THYAO",
        instrumentName: "Turkish Airlines",
        conditionType: "PRICE_ABOVE",
        indicatorType: null,
        indicatorField: null,
        interval: "1d",
        thresholdValue: 300,
        cooldownMinutes: 60,
        status: "ACTIVE",
        triggerState: "TRIGGERED",
        lastEvaluatedAt: "2026-11-20T08:30:00Z",
        lastTriggeredAt: "2026-11-20T08:30:00Z",
        lastObservedValue: 305.5,
        note: "Breakout level",
        createdAt: "2026-11-20T08:00:00Z",
        updatedAt: "2026-11-20T08:30:00Z",
      },
    ],
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
  };
}

function makeNotifications(): NotificationPageResponse {
  return {
    items: [
      {
        id: "notif-1",
        alertRuleId: "rule-1",
        symbol: "THYAO",
        conditionType: "PRICE_ABOVE",
        indicatorType: null,
        indicatorField: null,
        thresholdValue: 300,
        observedValue: 305.5,
        dataTimestamp: "2026-11-20T08:30:00Z",
        providerId: "mock",
        providerDisplayName: "Mock BIST Provider",
        providerMode: "SIMULATED",
        simulated: true,
        freshnessStatus: "SIMULATED_ONLY",
        staleData: false,
        message: "THYAO triggered: Price observed 305.5 >= threshold 300.",
        read: false,
        readAt: null,
        createdAt: "2026-11-20T08:30:00Z",
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    unreadCount: 1,
  };
}

describe("AlertsView", () => {
  beforeEach(() => {
    listAlertsMock.mockReset();
    evaluateNowMock.mockReset();
    listNotifsMock.mockReset();
    markReadMock.mockReset();
  });

  it("renders cooldown/provenance badges, switches indicator fields, evaluates alerts, and marks notification read", async () => {
    const user = userEvent.setup();
    listAlertsMock.mockResolvedValue(makeRules());
    listNotifsMock.mockResolvedValue(makeNotifications());

    const evalRes: AlertEvaluationRunResponse = {
      evaluatedCount: 1,
      triggeredCount: 0,
      suppressedCount: 1,
      evaluatedAt: "2026-11-20T08:35:00Z",
      dataSource: makeRules().dataSource,
      results: [
        {
          alertRuleId: "rule-1",
          symbol: "THYAO",
          outcome: "COOLDOWN_SUPPRESSED",
          observedValue: 306,
          reason: "Active cooldown window",
        },
      ],
    };
    evaluateNowMock.mockResolvedValueOnce(evalRes);
    markReadMock.mockResolvedValueOnce({
      ...makeNotifications().items[0],
      read: true,
    });

    render(<AlertsView initialSymbol="GARAN" />);

    await waitFor(() => {
      expect(screen.getByText("TETİKLENDİ")).toBeInTheDocument();
    });

    expect(screen.getByLabelText("Hisse Sembolü")).toHaveValue("GARAN");
    expect(screen.getByText("1 okunmamış")).toBeInTheDocument();
    expect(screen.getByText(/Mock BIST Provider/)).toBeInTheDocument();

    // Switching to indicator alert reveals MACD field selector options
    await user.selectOptions(screen.getByLabelText("Koşul Türü"), "INDICATOR_BELOW");
    await user.selectOptions(screen.getByLabelText("İndikatör"), "MACD");
    expect(screen.getByLabelText("İndikatör Alanı")).toHaveValue("macd");
    expect(screen.getByRole("option", { name: "Histogram" })).toBeInTheDocument();

    // Trigger manual evaluation
    await user.click(
      screen.getByRole("button", { name: "Aktif Alarmları Şimdi Değerlendir" })
    );
    await waitFor(() => {
      expect(screen.getByText("Değerlendirme Özeti")).toBeInTheDocument();
    });
    expect(
      screen.getByText(/bekleme süresi\/idempotency nedeniyle bastırıldı/)
    ).toBeInTheDocument();

    // Mark notification read
    await user.click(screen.getByRole("button", { name: "Okundu İşaretle" }));
    await waitFor(() => {
      expect(markReadMock).toHaveBeenCalledWith("notif-1");
    });
  });
});
