import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AiExplanationResponse } from "@/lib/api/types";
import { AiExplanationCard } from "./ai-explanation-card";

const getAiMock = vi.fn();
const regenAiMock = vi.fn();

vi.mock("@/lib/api/services", () => ({
  apiServices: {
    instruments: {
      getAiExplanation: (...args: unknown[]) => getAiMock(...args),
      regenerateAiExplanation: (...args: unknown[]) => regenAiMock(...args),
    },
  },
}));

function makeExplanation(overrides: Partial<AiExplanationResponse> = {}): AiExplanationResponse {
  return {
    explanationId: "exp-1",
    analysisRunId: "ana-123",
    scoreRunId: "sr-456",
    symbol: "THYAO",
    interval: "1d",
    adjusted: false,
    providerId: "deterministic-template",
    requestedProviderId: "ollama",
    modelName: "template-v1.0.0",
    promptVersion: "ai-explain-v1.0.0",
    status: "FALLBACK",
    fallbackUsed: true,
    fallbackReason: "PROVIDER_TIMEOUT",
    simulatedData: true,
    staleData: false,
    generatedAt: "2026-11-20T08:45:00Z",
    explanation: {
      summary: "THYAO recorded a 72.50/100 technical score on 1d simulated bars.",
      observedFacts: ["Latest simulated close is 298.50 TRY."],
      interpretation: ["Short-term moving averages align positively."],
      uncertaintiesAndRisks: ["Simulated market feed; excludes company fundamentals."],
      confidenceNote: "Deterministic fallback grounded in persisted run facts.",
    },
    dataSource: {
      providerId: "mock",
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
      latestDataTimestamp: "2026-11-20T08:45:00Z",
      lastSyncedAt: null,
      lastSyncStatus: null,
      ageMinutes: 0,
      note: "Simulated snapshot",
    },
    disclaimer: "Informational summary only; may be wrong and is not investment advice.",
    ...overrides,
  };
}

describe("AiExplanationCard", () => {
  beforeEach(() => {
    getAiMock.mockReset();
    regenAiMock.mockReset();
  });

  it("renders structured facts, interpretation, risks, audit metadata, fallback banner, and regenerates cleanly", async () => {
    const user = userEvent.setup();
    getAiMock.mockResolvedValueOnce(makeExplanation());
    regenAiMock.mockResolvedValueOnce(
      makeExplanation({
        explanationId: "exp-2",
        providerId: "ollama",
        modelName: "llama3.2:3b",
        status: "GENERATED",
        fallbackUsed: false,
        fallbackReason: null,
      })
    );

    render(
      <AiExplanationCard
        symbol="THYAO"
        interval="1d"
        adjusted={false}
        scoreRunId="sr-456"
        timezone="Europe/Istanbul"
      />
    );

    await waitFor(() => {
      expect(
        screen.getByText("Deterministik Şablon Yedeği Etkin")
      ).toBeInTheDocument();
    });

    expect(screen.getByText("Gözlenen Gerçekler")).toBeInTheDocument();
    expect(screen.getByText("Teknik Yorum")).toBeInTheDocument();
    expect(screen.getByText("Belirsizlikler ve Riskler")).toBeInTheDocument();
    expect(screen.getByText("Latest simulated close is 298.50 TRY.")).toBeInTheDocument();
    expect(screen.getByText("ai-explain-v1.0.0")).toBeInTheDocument();
    expect(screen.getByText("template-v1.0.0")).toBeInTheDocument();
    expect(screen.getByText(/ana-123/)).toBeInTheDocument();
    expect(
      screen.getByText("Informational summary only; may be wrong and is not investment advice.")
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Yeniden Üret" }));

    await waitFor(() => {
      expect(regenAiMock).toHaveBeenCalledWith("THYAO", {
        interval: "1d",
        adjusted: false,
        scoreRunId: "sr-456",
      });
    });
    expect(screen.getByText("llama3.2:3b")).toBeInTheDocument();
    expect(screen.getByText("ÜRETİLDİ")).toBeInTheDocument();
  });
});
