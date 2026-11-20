"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  LineSeries,
  createChart,
  type CandlestickData,
  type HistogramData,
  type IChartApi,
  type LineData,
  type UTCTimestamp,
} from "lightweight-charts";
import { useTheme } from "@/components/providers/theme-provider";
import type { IndicatorSeriesResponse, PriceBarResponse } from "@/lib/api/types";
import {
  formatCompactNumber,
  formatCurrency,
  formatDateTime,
  toNumeric,
} from "@/lib/format";

export type ChartOverlayKey = "SMA" | "EMA" | "BOLLINGER";

interface CandlestickChartProps {
  symbol: string;
  currency: string;
  timezone: string;
  bars: PriceBarResponse[];
  indicators: IndicatorSeriesResponse[];
  activeOverlays: ChartOverlayKey[];
}

interface BarHoverSnapshot {
  timestampIso: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

function toUtcEpochSeconds(iso: string): UTCTimestamp | null {
  const ms = Date.parse(iso);
  if (Number.isNaN(ms)) {
    return null;
  }
  return Math.floor(ms / 1000) as UTCTimestamp;
}

function buildSortedLineSeries(
  series: IndicatorSeriesResponse | undefined,
  valueKey: string
): LineData<UTCTimestamp>[] {
  if (!series || series.status !== "READY") {
    return [];
  }
  const byTime = new Map<number, number>();
  for (const point of series.points) {
    if (point.warmUp) {
      continue;
    }
    const ts = toUtcEpochSeconds(point.timestamp);
    const val = toNumeric(point.values?.[valueKey]);
    if (ts !== null && val !== null) {
      byTime.set(ts, val);
    }
  }
  return Array.from(byTime.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([time, value]) => ({ time: time as UTCTimestamp, value }));
}

export function CandlestickChart({
  symbol,
  currency,
  timezone,
  bars,
  indicators,
  activeOverlays,
}: CandlestickChartProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const { theme } = useTheme();

  const { candleData, volumeData, barMapByTime, latestBar } = useMemo(() => {
    const dedup = new Map<number, { bar: PriceBarResponse; time: UTCTimestamp }>();
    for (const bar of bars) {
      const time = toUtcEpochSeconds(bar.timestamp);
      const open = toNumeric(bar.open);
      const high = toNumeric(bar.high);
      const low = toNumeric(bar.low);
      const close = toNumeric(bar.close);
      if (
        time === null ||
        open === null ||
        high === null ||
        low === null ||
        close === null
      ) {
        continue;
      }
      dedup.set(time, { bar, time });
    }

    const sorted = Array.from(dedup.values()).sort((a, b) => a.time - b.time);

    const candles: CandlestickData<UTCTimestamp>[] = [];
    const volumes: HistogramData<UTCTimestamp>[] = [];
    const lookup = new Map<number, BarHoverSnapshot>();

    for (const entry of sorted) {
      const open = toNumeric(entry.bar.open) ?? 0;
      const high = toNumeric(entry.bar.high) ?? 0;
      const low = toNumeric(entry.bar.low) ?? 0;
      const close = toNumeric(entry.bar.close) ?? 0;
      const volume = toNumeric(entry.bar.volume) ?? 0;
      const isUp = close >= open;

      candles.push({
        time: entry.time,
        open,
        high,
        low,
        close,
      });

      volumes.push({
        time: entry.time,
        value: volume,
        color: isUp ? "rgba(16, 185, 129, 0.35)" : "rgba(244, 63, 94, 0.35)",
      });

      lookup.set(entry.time, {
        timestampIso: entry.bar.timestamp,
        open,
        high,
        low,
        close,
        volume,
      });
    }

    const last = sorted.length > 0 ? lookup.get(sorted[sorted.length - 1].time) ?? null : null;
    return {
      candleData: candles,
      volumeData: volumes,
      barMapByTime: lookup,
      latestBar: last,
    };
  }, [bars]);

  const [hoveredBar, setHoveredBar] = useState<BarHoverSnapshot | null>(null);
  const displayBar = hoveredBar ?? latestBar;

  useEffect(() => {
    const container = containerRef.current;
    if (!container || candleData.length === 0) {
      return;
    }

    const isDark = theme === "dark";
    const chart: IChartApi = createChart(container, {
      autoSize: false,
      width: container.clientWidth || 720,
      height: 400,
      layout: {
        attributionLogo: true,
        background: {
          type: ColorType.Solid,
          color: isDark ? "#0f172a" : "#ffffff",
        },
        textColor: isDark ? "#94a3b8" : "#475569",
      },
      grid: {
        vertLines: {
          color: isDark ? "rgba(51, 65, 85, 0.45)" : "rgba(226, 232, 240, 0.8)",
        },
        horzLines: {
          color: isDark ? "rgba(51, 65, 85, 0.45)" : "rgba(226, 232, 240, 0.8)",
        },
      },
      rightPriceScale: {
        borderColor: isDark ? "#334155" : "#cbd5e1",
      },
      timeScale: {
        borderColor: isDark ? "#334155" : "#cbd5e1",
        timeVisible: true,
        secondsVisible: false,
      },
    });

    const candlestickSeries = chart.addSeries(CandlestickSeries, {
      upColor: isDark ? "#34d399" : "#059669",
      downColor: isDark ? "#fb7185" : "#e11d48",
      borderUpColor: isDark ? "#34d399" : "#059669",
      borderDownColor: isDark ? "#fb7185" : "#e11d48",
      wickUpColor: isDark ? "#34d399" : "#059669",
      wickDownColor: isDark ? "#fb7185" : "#e11d48",
    });
    candlestickSeries.priceScale().applyOptions({
      scaleMargins: { top: 0.08, bottom: 0.24 },
    });
    candlestickSeries.setData(candleData);

    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: "volume" },
      priceScaleId: "",
    });
    volumeSeries.priceScale().applyOptions({
      scaleMargins: { top: 0.78, bottom: 0 },
    });
    volumeSeries.setData(volumeData);

    const smaSeries = indicators.find((i) => i.type === "SMA");
    const emaSeries = indicators.find((i) => i.type === "EMA");
    const bbSeries = indicators.find((i) => i.type === "BOLLINGER");

    if (activeOverlays.includes("SMA")) {
      const points = buildSortedLineSeries(smaSeries, "value");
      if (points.length > 0) {
        const line = chart.addSeries(LineSeries, {
          color: "#38bdf8",
          lineWidth: 2,
          title: "SMA",
        });
        line.setData(points);
      }
    }

    if (activeOverlays.includes("EMA")) {
      const points = buildSortedLineSeries(emaSeries, "value");
      if (points.length > 0) {
        const line = chart.addSeries(LineSeries, {
          color: "#a78bfa",
          lineWidth: 2,
          title: "EMA",
        });
        line.setData(points);
      }
    }

    if (activeOverlays.includes("BOLLINGER")) {
      const upper = buildSortedLineSeries(bbSeries, "upper");
      const middle = buildSortedLineSeries(bbSeries, "middle");
      const lower = buildSortedLineSeries(bbSeries, "lower");
      if (upper.length > 0) {
        chart
          .addSeries(LineSeries, {
            color: "rgba(251, 191, 36, 0.85)",
            lineWidth: 1,
            title: "BB Üst",
          })
          .setData(upper);
      }
      if (middle.length > 0) {
        chart
          .addSeries(LineSeries, {
            color: "rgba(251, 191, 36, 0.55)",
            lineWidth: 1,
            title: "BB Orta",
          })
          .setData(middle);
      }
      if (lower.length > 0) {
        chart
          .addSeries(LineSeries, {
            color: "rgba(251, 191, 36, 0.85)",
            lineWidth: 1,
            title: "BB Alt",
          })
          .setData(lower);
      }
    }

    chart.timeScale().fitContent();

    chart.subscribeCrosshairMove((param) => {
      if (!param.time || typeof param.time !== "number") {
        setHoveredBar(null);
        return;
      }
      const snapshot = barMapByTime.get(param.time) ?? null;
      setHoveredBar(snapshot);
    });

    let resizeObserver: ResizeObserver | undefined;
    if (typeof ResizeObserver !== "undefined") {
      resizeObserver = new ResizeObserver((entries) => {
        const rect = entries[0]?.contentRect;
        if (rect && rect.width > 0) {
          chart.applyOptions({ width: Math.floor(rect.width) });
        }
      });
      resizeObserver.observe(container);
    }

    return () => {
      resizeObserver?.disconnect();
      chart.remove();
    };
  }, [theme, candleData, volumeData, indicators, activeOverlays, barMapByTime]);

  if (candleData.length === 0) {
    return (
      <div className="flex h-[320px] items-center justify-center rounded-md border border-dashed border-border bg-surface-muted/30 p-6 text-center text-sm text-muted">
        Seçilen tarih aralığı ve bar periyodu için {symbol} OHLCV fiyat barı bulunamadı.
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {displayBar ? (
        <div
          aria-live="polite"
          className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-md border border-border bg-surface-muted/50 px-3 py-1.5 text-xs tabular-nums"
        >
          <span className="font-semibold text-foreground">{symbol}</span>
          <span className="text-muted">
            {formatDateTime(displayBar.timestampIso, timezone)}
          </span>
          <span>
            Açılış:{" "}
            <strong className="font-medium text-foreground">
              {formatCurrency(displayBar.open, currency)}
            </strong>
          </span>
          <span>
            Yüksek:{" "}
            <strong className="font-medium text-foreground">
              {formatCurrency(displayBar.high, currency)}
            </strong>
          </span>
          <span>
            Düşük:{" "}
            <strong className="font-medium text-foreground">
              {formatCurrency(displayBar.low, currency)}
            </strong>
          </span>
          <span>
            Kapanış:{" "}
            <strong className="font-medium text-foreground">
              {formatCurrency(displayBar.close, currency)}
            </strong>
          </span>
          <span>
            Hacim:{" "}
            <strong className="font-medium text-foreground">
              {formatCompactNumber(displayBar.volume)}
            </strong>
          </span>
        </div>
      ) : null}

      <div
        ref={containerRef}
        role="img"
        aria-label={`${symbol} etkileşimli mum ve hacim grafiği`}
        className="w-full overflow-hidden rounded-md border border-border"
      />

      <p className="text-[11px] text-muted">
        Grafik altyapısı:{" "}
        <a
          href="https://www.tradingview.com"
          target="_blank"
          rel="noopener noreferrer"
          className="underline hover:text-foreground"
        >
          TradingView Lightweight Charts
        </a>{" "}
        (Apache 2.0). OHLCV değerlerini incelemek için grafiğin üzerine gelin veya kaydırın.
      </p>
    </div>
  );
}
