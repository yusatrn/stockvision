import { describe, expect, it } from "vitest";
import {
  formatCurrency,
  formatDateTime,
  formatNumber,
  formatRelativeAgeMinutes,
  formatSignedPercent,
  toNumeric,
} from "./format";

describe("format helpers", () => {
  it("parses numeric inputs safely including BigDecimal strings", () => {
    expect(toNumeric(123.45)).toBe(123.45);
    expect(toNumeric("298.5000")).toBe(298.5);
    expect(toNumeric(null)).toBeNull();
    expect(toNumeric(undefined)).toBeNull();
    expect(toNumeric("")).toBeNull();
    expect(toNumeric("NaN")).toBeNull();
  });

  it("formats currency and decimals with explicit rounding", () => {
    const formatted = formatCurrency(298.5, "TRY").replace(/\u00a0/g, " ");
    expect(formatted).toContain("TRY");
    expect(formatted).toContain("298,50");
    expect(formatCurrency("42.126", "TRY").replace(/\u00a0/g, " ")).toContain("42,13");
    expect(formatCurrency(null, "TRY")).toBe("—");
    expect(formatNumber(64.236, 1)).toBe("64,2");
  });

  it("formats signed percentages clearly", () => {
    expect(formatSignedPercent(2.456)).toBe("+2,46%");
    expect(formatSignedPercent(-1.1)).toBe("-1,10%");
    expect(formatSignedPercent(0)).toBe("0,00%");
    expect(formatSignedPercent(null)).toBe("—");
  });

  it("converts UTC timestamps into target user timezone", () => {
    const utcIso = "2026-11-19T10:15:00Z";
    const istanbul = formatDateTime(utcIso, "Europe/Istanbul");
    const utc = formatDateTime(utcIso, "UTC");

    expect(istanbul).toContain("13:15");
    expect(utc).toContain("10:15");
    expect(formatDateTime("invalid-date", "UTC")).toBe("—");
  });

  it("formats relative freshness age cleanly", () => {
    expect(formatRelativeAgeMinutes(0)).toBe("< 1 dk önce");
    expect(formatRelativeAgeMinutes(25)).toBe("25 dk önce");
    expect(formatRelativeAgeMinutes(135)).toBe("2 sa 15 dk önce");
    expect(formatRelativeAgeMinutes(60 * 50)).toBe("2 gün 2 sa önce");
    expect(formatRelativeAgeMinutes(null)).toBe("Bilinmiyor");
  });
});
