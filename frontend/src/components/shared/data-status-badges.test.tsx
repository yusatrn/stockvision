import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  DataProvenanceBar,
  FreshnessBadge,
  ProviderModeBadge,
  RegimeBadge,
  ScorePill,
} from "./data-status-badges";

describe("data-status-badges", () => {
  it("renders provider mode badges accurately", () => {
    const { rerender } = render(<ProviderModeBadge mode="SIMULATED" simulated />);
    expect(screen.getByText("Simüle Veri")).toBeInTheDocument();

    rerender(<ProviderModeBadge mode="DELAYED" delayMinutes={15} />);
    expect(screen.getByText("Gecikmeli (15 dk)")).toBeInTheDocument();

    rerender(<ProviderModeBadge mode="REALTIME" />);
    expect(screen.getByText("Gerçek Zamanlı")).toBeInTheDocument();
  });

  it("renders freshness states including STALE and NO_DATA", () => {
    const { rerender } = render(
      <FreshnessBadge
        freshness={{
          status: "STALE",
          stale: true,
          simulated: false,
          latestDataTimestamp: "2026-11-18T15:00:00Z",
          lastSyncedAt: null,
          lastSyncStatus: "FAILED",
          ageMinutes: 125,
          note: "Feed delayed past threshold",
        }}
      />
    );
    expect(screen.getByText(/Bayat Veri/)).toBeInTheDocument();
    expect(screen.getByText(/2 sa 5 dk önce/)).toBeInTheDocument();

    rerender(
      <FreshnessBadge
        freshness={{
          status: "NO_DATA",
          stale: true,
          simulated: false,
          latestDataTimestamp: null,
          lastSyncedAt: null,
          lastSyncStatus: null,
          ageMinutes: null,
          note: "No market bars found",
        }}
      />
    );
    expect(screen.getByText("Veri Yok")).toBeInTheDocument();
  });

  it("renders INSUFFICIENT_DATA explicitly in score and regime badges", () => {
    render(
      <div>
        <ScorePill score={null} status="INSUFFICIENT_DATA" />
        <RegimeBadge regime="INSUFFICIENT_DATA" status="INSUFFICIENT_DATA" />
      </div>
    );

    expect(screen.getAllByText("Yetersiz Veri (INSUFFICIENT_DATA)")).toHaveLength(2);
  });

  it("renders provenance bar metadata and user timezone timestamp", () => {
    render(
      <DataProvenanceBar
        dataSource={{
          providerId: "mock-bist",
          displayName: "Mock BIST Provider",
          mode: "SIMULATED",
          simulated: true,
          delayMinutes: 0,
          sourceTimezone: "Europe/Istanbul",
          licenseNote: "Synthetic demo dataset",
        }}
        freshness={{
          status: "SIMULATED_ONLY",
          stale: false,
          simulated: true,
          latestDataTimestamp: "2026-11-19T10:00:00Z",
          lastSyncedAt: "2026-11-19T10:01:00Z",
          lastSyncStatus: "SUCCESS",
          ageMinutes: 5,
          note: "Simulated market snapshot",
        }}
        timezone="UTC"
      />
    );

    expect(screen.getByText(/Mock BIST Provider/)).toBeInTheDocument();
    expect(screen.getByText(/10:00/)).toBeInTheDocument();
    expect(screen.getByText(/Synthetic demo dataset/)).toBeInTheDocument();
  });
});
