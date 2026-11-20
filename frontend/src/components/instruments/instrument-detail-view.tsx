"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, BellPlus, BookmarkPlus, RefreshCw } from "lucide-react";
import {
  CandlestickChart,
  type ChartOverlayKey,
} from "@/components/instruments/candlestick-chart";
import { AiExplanationCard } from "@/components/instruments/ai-explanation-card";
import { IndicatorPanel } from "@/components/instruments/indicator-panel";
import { ScoreBreakdown } from "@/components/instruments/score-breakdown";
import { TechnicalLevelsCard } from "@/components/instruments/technical-levels-card";
import { useAuth } from "@/components/providers/auth-provider";
import {
  DataProvenanceBar,
  SignedPercentBadge,
} from "@/components/shared/data-status-badges";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button, buttonClasses } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Select } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiClientError } from "@/lib/api/client";
import { apiServices } from "@/lib/api/services";
import type {
  HistoricalBarsResponse,
  IndicatorType,
  InstrumentAnalysisResponse,
  InstrumentDetailResponse,
  InstrumentSetupResponse,
  TechnicalIndicatorsResponse,
  WatchlistResponse,
} from "@/lib/api/types";
import {
  formatCompactNumber,
  formatCurrency,
  formatDateTime,
} from "@/lib/format";
import {
  translateInstrumentType,
  translateProviderMode,
  translateSector,
} from "@/lib/labels";

const DEFAULT_INDICATORS: IndicatorType[] = [
  "SMA",
  "EMA",
  "RSI",
  "MACD",
  "BOLLINGER",
  "VOLUME_AVG",
];

const OVERLAY_OPTIONS: { key: ChartOverlayKey; label: string }[] = [
  { key: "SMA", label: "SMA Çizgisi" },
  { key: "EMA", label: "EMA Çizgisi" },
  { key: "BOLLINGER", label: "Bollinger Bantları" },
];

export function InstrumentDetailView({ symbol }: { symbol: string }) {
  const { effectiveTimezone } = useAuth();
  const [interval, setInterval] = useState<"1d" | "1h" | "15m">("1d");
  const [adjusted, setAdjusted] = useState(false);
  const [selectedIndicators, setSelectedIndicators] =
    useState<IndicatorType[]>(DEFAULT_INDICATORS);
  const [activeOverlays, setActiveOverlays] = useState<ChartOverlayKey[]>([
    "SMA",
    "EMA",
  ]);

  const [detail, setDetail] = useState<InstrumentDetailResponse | null>(null);
  const [barsRes, setBarsRes] = useState<HistoricalBarsResponse | null>(null);
  const [techRes, setTechRes] = useState<TechnicalIndicatorsResponse | null>(null);
  const [analysisRes, setAnalysisRes] = useState<InstrumentAnalysisResponse | null>(null);
  const [setupRes, setSetupRes] = useState<InstrumentSetupResponse | null>(null);
  const [watchlists, setWatchlists] = useState<WatchlistResponse[]>([]);
  const [targetWatchlistId, setTargetWatchlistId] = useState<string>("");
  const [addingToWatchlist, setAddingToWatchlist] = useState(false);
  const [quickMessage, setQuickMessage] = useState<{
    variant: "positive" | "error";
    text: string;
  } | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadAll = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const indicatorQuery: IndicatorType[] =
          selectedIndicators.length > 0 ? selectedIndicators : ["SMA"];
        const [d, b, t, a, s, w] = await Promise.all([
          apiServices.instruments.getDetail(symbol, signal),
          apiServices.instruments.getBars(symbol, { interval, adjusted }, signal),
          apiServices.instruments.getIndicators(
            symbol,
            { interval, adjusted, indicators: indicatorQuery },
            signal
          ),
          apiServices.instruments.getAnalysis(symbol, { interval, adjusted }, signal),
          apiServices.instruments
            .getSetup(symbol, { interval, adjusted }, signal)
            .catch(() => null),
          apiServices.watchlists.list(signal).catch(() => null),
        ]);
        setDetail(d);
        setBarsRes(b);
        setTechRes(t);
        setAnalysisRes(a);
        setSetupRes(s);
        if (w) {
          setWatchlists(w.watchlists);
          setTargetWatchlistId((prev) =>
            prev && w.watchlists.some((wl) => wl.id === prev)
              ? prev
              : w.watchlists[0]?.id ?? ""
          );
        }
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") {
          return;
        }
        setErrorMessage(
          isApiClientError(err)
            ? `${err.message} (${err.code})`
            : `${symbol} enstrüman verileri yüklenemedi.`
        );
      } finally {
        setLoading(false);
      }
    },
    [symbol, interval, adjusted, selectedIndicators]
  );

  useEffect(() => {
    const controller = new AbortController();
    loadAll(controller.signal);
    return () => controller.abort();
  }, [loadAll]);

  const handleToggleIndicator = (indicator: IndicatorType) => {
    setSelectedIndicators((prev) => {
      if (prev.includes(indicator)) {
        if (prev.length === 1) {
          return prev;
        }
        return prev.filter((item) => item !== indicator);
      }
      return [...prev, indicator];
    });
  };

  const handleToggleOverlay = (overlay: ChartOverlayKey) => {
    setActiveOverlays((prev) =>
      prev.includes(overlay) ? prev.filter((item) => item !== overlay) : [...prev, overlay]
    );
    if (!selectedIndicators.includes(overlay)) {
      setSelectedIndicators((prev) => [...prev, overlay]);
    }
  };

  const handleAddToWatchlist = async () => {
    if (!targetWatchlistId) {
      return;
    }
    setAddingToWatchlist(true);
    setQuickMessage(null);
    try {
      await apiServices.watchlists.addItem(targetWatchlistId, { symbol });
      const targetName =
        watchlists.find((w) => w.id === targetWatchlistId)?.name ?? "izleme listesi";
      setQuickMessage({
        variant: "positive",
        text: `${symbol}, "${targetName}" listesine eklendi.`,
      });
    } catch (err) {
      setQuickMessage({
        variant: "error",
        text: isApiClientError(err)
          ? err.message
          : `${symbol} izleme listesine eklenemedi.`,
      });
    } finally {
      setAddingToWatchlist(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-1">
          <Link
            href="/markets"
            className="inline-flex items-center gap-1 text-xs font-medium text-muted hover:text-foreground"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Piyasalara Dön</span>
          </Link>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight sm:text-2xl">{symbol}</h1>
            {detail ? (
              <>
                <span className="text-sm text-muted">· {detail.name}</span>
                <Badge variant="neutral">{detail.exchangeCode}</Badge>
                {detail.sector ? (
                  <Badge variant="info">{translateSector(detail.sector)}</Badge>
                ) : null}
              </>
            ) : null}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Select
            aria-label="Grafik bar aralığı"
            value={interval}
            onChange={(e) => setInterval(e.target.value as "1d" | "1h" | "15m")}
            className="w-40"
          >
            <option value="1d">Günlük (1d)</option>
            <option value="1h">Saatlik (1h)</option>
            <option value="15m">15 Dakika (15m)</option>
          </Select>

          <label className="flex items-center gap-1.5 rounded-md border border-border bg-surface px-2.5 h-9 text-xs cursor-pointer">
            <input
              type="checkbox"
              checked={adjusted}
              onChange={(e) => setAdjusted(e.target.checked)}
              className="h-3.5 w-3.5 rounded accent-accent"
            />
            <span>Düzeltilmiş</span>
          </label>

          <Button
            variant="outline"
            size="sm"
            onClick={() => loadAll()}
            disabled={loading}
          >
            <RefreshCw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} aria-hidden="true" />
            <span>Yenile</span>
          </Button>

          {watchlists.length > 0 ? (
            <div className="flex items-center gap-1.5">
              <Select
                aria-label="Hedef izleme listesi"
                value={targetWatchlistId}
                onChange={(e) => setTargetWatchlistId(e.target.value)}
                className="w-36"
              >
                {watchlists.map((wl) => (
                  <option key={wl.id} value={wl.id}>
                    {wl.name}
                  </option>
                ))}
              </Select>
              <Button
                variant="secondary"
                size="sm"
                onClick={handleAddToWatchlist}
                isLoading={addingToWatchlist}
              >
                <BookmarkPlus className="h-4 w-4" aria-hidden="true" />
                <span>İzleme Listesine Ekle</span>
              </Button>
            </div>
          ) : (
            <Link
              href="/watchlist"
              className={buttonClasses({ variant: "outline", size: "sm" })}
            >
              <BookmarkPlus className="h-4 w-4" aria-hidden="true" />
              <span>Yeni İzleme Listesi</span>
            </Link>
          )}

          <Link
            href={`/alerts?symbol=${encodeURIComponent(symbol)}`}
            className={buttonClasses({ variant: "secondary", size: "sm" })}
          >
            <BellPlus className="h-4 w-4" aria-hidden="true" />
            <span>Alarm Oluştur</span>
          </Link>
        </div>
      </div>

      {quickMessage ? (
        <Alert
          variant={quickMessage.variant}
          title={
            quickMessage.variant === "positive"
              ? "İzleme Listesi Güncellendi"
              : "İzleme Listesi Uyarısı"
          }
        >
          {quickMessage.text}
        </Alert>
      ) : null}

      {errorMessage ? (
        <Alert variant="error" title="Enstrüman Yükleme Hatası">
          {errorMessage}
        </Alert>
      ) : null}

      {loading && !detail ? (
        <div className="space-y-4">
          <Skeleton className="h-14" />
          <Skeleton className="h-28" />
          <Skeleton className="h-[420px]" />
        </div>
      ) : null}

      {detail ? (
        <>
          <DataProvenanceBar
            dataSource={detail.dataSource}
            freshness={detail.freshness}
            timezone={effectiveTimezone}
            calculatedAt={analysisRes?.calculatedAt}
            extraLabel={`Borsa Saat Dilimi: ${detail.exchangeTimezone}`}
          />

          <Card>
            <CardContent className="grid gap-4 pt-5 sm:grid-cols-2 lg:grid-cols-4">
              <div>
                <p className="text-xs text-muted">Son Fiyat</p>
                <p className="mt-1 text-2xl font-bold tabular-nums">
                  {formatCurrency(detail.latestQuote.price, detail.latestQuote.currency)}
                </p>
                <p className="mt-1 text-xs text-muted">
                  Zaman: {formatDateTime(detail.latestQuote.quotedAt, effectiveTimezone)}
                </p>
              </div>
              <div>
                <p className="text-xs text-muted">Önceki Kapanışa Göre Değişim</p>
                <div className="mt-1.5 flex items-center gap-2">
                  <SignedPercentBadge value={detail.latestQuote.changePercent} />
                  <span className="text-xs font-medium text-muted tabular-nums">
                    ({formatCurrency(
                      detail.latestQuote.changeAmount,
                      detail.latestQuote.currency
                    )})
                  </span>
                </div>
                <p className="mt-1 text-xs text-muted tabular-nums">
                  Önceki Kapanış:{" "}
                  {formatCurrency(
                    detail.latestQuote.previousClose,
                    detail.latestQuote.currency
                  )}
                </p>
              </div>
              <div>
                <p className="text-xs text-muted">Son Seans Hacmi</p>
                <p className="mt-1 text-xl font-semibold tabular-nums">
                  {formatCompactNumber(detail.latestQuote.volume)}
                </p>
                <p className="mt-1 text-xs text-muted">
                  Tür: {translateInstrumentType(detail.type)} · Para Birimi: {detail.currency}
                </p>
              </div>
              <div>
                <p className="text-xs text-muted">Sağlayıcı ve Gecikme</p>
                <p className="mt-1 text-sm font-semibold">
                  {detail.dataSource.displayName}
                </p>
                <p className="mt-1 text-xs text-muted">
                  Mod: {translateProviderMode(detail.dataSource.mode)} ·{" "}
                  {detail.latestQuote.delayMinutes > 0
                    ? `${detail.latestQuote.delayMinutes} dk gecikme`
                    : "0 dk gecikme"}
                </p>
              </div>
            </CardContent>
          </Card>
        </>
      ) : null}

      {barsRes ? (
        <Card>
          <CardHeader className="sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle>
                Fiyat Hareketi ve Hacim · {barsRes.interval.toUpperCase()} ({barsRes.count} bar)
              </CardTitle>
              <CardDescription>{barsRes.missingDataNote}</CardDescription>
            </div>
            <div
              role="group"
              aria-label="Grafik fiyat katmanları"
              className="mt-2 flex flex-wrap gap-1.5 sm:mt-0"
            >
              {OVERLAY_OPTIONS.map((opt) => {
                const enabled = activeOverlays.includes(opt.key);
                return (
                  <button
                    key={opt.key}
                    type="button"
                    aria-pressed={enabled}
                    onClick={() => handleToggleOverlay(opt.key)}
                    className={`rounded border px-2.5 py-1 text-xs font-medium transition-colors ${
                      enabled
                        ? "border-accent bg-accent/15 text-accent"
                        : "border-border bg-surface-muted/40 text-muted hover:text-foreground"
                    }`}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>
          </CardHeader>
          <CardContent>
            <CandlestickChart
              symbol={symbol}
              currency={detail?.currency ?? "TRY"}
              timezone={effectiveTimezone}
              bars={barsRes.bars}
              indicators={techRes?.indicators ?? []}
              activeOverlays={activeOverlays}
            />
          </CardContent>
        </Card>
      ) : null}

      {setupRes ? (
        <TechnicalLevelsCard setup={setupRes} timezone={effectiveTimezone} />
      ) : null}

      {techRes ? (
        <IndicatorPanel
          technicalData={techRes}
          selectedIndicators={selectedIndicators}
          onToggleIndicator={handleToggleIndicator}
        />
      ) : null}

      {analysisRes ? (
        <>
          <ScoreBreakdown analysis={analysisRes} timezone={effectiveTimezone} />
          <AiExplanationCard
            symbol={symbol}
            interval={interval}
            adjusted={adjusted}
            scoreRunId={analysisRes.scoreRunId}
            timezone={effectiveTimezone}
          />
        </>
      ) : null}
    </div>
  );
}
