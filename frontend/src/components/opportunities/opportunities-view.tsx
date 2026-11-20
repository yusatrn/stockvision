"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowRight,
  BellPlus,
  Database,
  Flame,
  Layers,
  RefreshCw,
  RotateCcw,
  Search,
  Target,
} from "lucide-react";
import { TechnicalSetupBody } from "@/components/instruments/technical-levels-card";
import { useAuth } from "@/components/providers/auth-provider";
import {
  DataProvenanceBar,
  DecisionSupportDisclaimer,
  ScorePill,
  SetupCategoryBadge,
  SignedPercentBadge,
  TrendBadge,
} from "@/components/shared/data-status-badges";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button, buttonClasses } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
} from "@/components/ui/card";
import { Input, Select } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiClientError } from "@/lib/api/client";
import { apiServices } from "@/lib/api/services";
import type {
  BatchSyncResponse,
  OpportunitiesPageResponse,
  OpportunitiesQueryParams,
  OpportunitiesSortField,
  SetupCategory,
  UniverseScope,
  UniverseSyncStatusResponse,
} from "@/lib/api/types";
import {
  DEFAULT_EXCHANGE,
  formatCurrency,
  formatDateTime,
  formatNumber,
} from "@/lib/format";
import { translateSector, translateSetupCategory } from "@/lib/labels";

const CATEGORY_FILTER_OPTIONS: { value: "" | SetupCategory; label: string }[] = [
  { value: "", label: "Tümü" },
  { value: "HIGH_MOMENTUM_BREAKOUT", label: "Yüksek Momentum / Kırılım" },
  { value: "PULLBACK_WATCH", label: "Destek / Geri Çekilme" },
  { value: "OVEREXTENDED_RISK", label: "Aşırı Alım Riski" },
  { value: "CONSOLIDATION", label: "Yatay / Sıkışma" },
  { value: "WEAK_BEARISH", label: "Zayıf / Düşüş" },
];

export function OpportunitiesView() {
  const { effectiveTimezone } = useAuth();
  const [interval, setInterval] = useState<"1d" | "1h" | "15m">("1d");
  const [scope, setScope] = useState<UniverseScope>("BIST30");
  const [searchInput, setSearchInput] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [category, setCategory] = useState<"" | SetupCategory>("");
  const [sortBy, setSortBy] = useState<OpportunitiesSortField>("momentum");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [includeInsufficientData, setIncludeInsufficientData] = useState(true);
  const [page, setPage] = useState(0);

  const [data, setData] = useState<OpportunitiesPageResponse | null>(null);
  const [syncStatus, setSyncStatus] = useState<UniverseSyncStatusResponse | null>(null);
  const [syncingBatch, setSyncingBatch] = useState(false);
  const [batchMessage, setBatchMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadOpportunities = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const params: OpportunitiesQueryParams = {
          exchange: DEFAULT_EXCHANGE,
          interval,
          scope,
          query: appliedQuery || undefined,
          category: category || undefined,
          includeInsufficientData,
          sortBy,
          sortDir,
          page,
          size: 20,
        };
        const [res, statusRes] = await Promise.all([
          apiServices.opportunities.list(params, signal),
          apiServices.marketData
            ?.getSyncStatus({ exchange: DEFAULT_EXCHANGE, interval }, signal)
            .catch(() => null) ?? Promise.resolve(null),
        ]);
        setData(res);
        if (statusRes) {
          setSyncStatus(statusRes);
        }
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") {
          return;
        }
        setErrorMessage(
          isApiClientError(err)
            ? `${err.message} (${err.code})`
            : "Adaylar ve teknik seviyeler yüklenemedi."
        );
      } finally {
        setLoading(false);
      }
    },
    [interval, scope, appliedQuery, category, includeInsufficientData, sortBy, sortDir, page]
  );

  useEffect(() => {
    const controller = new AbortController();
    loadOpportunities(controller.signal);
    return () => controller.abort();
  }, [loadOpportunities]);

  const handleSelectCategory = (next: "" | SetupCategory) => {
    setCategory(next);
    setPage(0);
  };

  const handleSearchSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setAppliedQuery(searchInput.trim());
    setPage(0);
  };

  const handleSyncBatch = async () => {
    if (!apiServices.marketData?.syncBatch) {
      return;
    }
    setSyncingBatch(true);
    setBatchMessage(null);
    try {
      const batch: BatchSyncResponse = await apiServices.marketData.syncBatch({
        exchange: DEFAULT_EXCHANGE,
        interval,
        batchSize: 25,
      });
      setSyncStatus({
        exchange: batch.exchange,
        interval: batch.interval,
        providerId: batch.providerId,
        totalActiveInstruments: batch.totalActiveInstruments,
        syncedInstruments: batch.syncedInstruments,
        remainingInstruments: batch.remainingInstruments,
      });
      setBatchMessage(
        `${batch.succeededInBatch} yeni hisse verisi çekildi (${batch.syncedInstruments}/${batch.totalActiveInstruments})` +
          (batch.failedInBatch > 0 ? ` · ${batch.failedInBatch} sembol atlandı` : "")
      );
      await loadOpportunities();
    } catch (err) {
      setBatchMessage(
        isApiClientError(err)
          ? `Toplu senkronizasyon başarısız: ${err.message}`
          : "Toplu senkronizasyon tamamlanamadı."
      );
    } finally {
      setSyncingBatch(false);
    }
  };

  const handleResetFilters = () => {
    setScope("BIST30");
    setSearchInput("");
    setAppliedQuery("");
    setCategory("");
    setSortBy("momentum");
    setSortDir("desc");
    setIncludeInsufficientData(true);
    setPage(0);
  };

  const counts = data?.categoryCounts ?? {};
  const totalTracked = Object.values(counts).reduce((acc, val) => acc + (val || 0), 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight sm:text-2xl">
            Momentum Adayları ve Teknik Seviyeler
          </h1>
          <p className="text-sm text-muted">
            {DEFAULT_EXCHANGE} hisseleri için kural tabanlı kırılım/momentum profilleri, destek izleme bantları,
            direnç/realizasyon bölgeleri, risk sınırı ve ±%10 BIST teorik fiyat marjı referansları.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Select
            aria-label="Analiz periyodu"
            value={interval}
            onChange={(e) => {
              setInterval(e.target.value as "1d" | "1h" | "15m");
              setPage(0);
            }}
            className="w-40"
          >
            <option value="1d">Günlük (1d)</option>
            <option value="1h">Saatlik (1h)</option>
            <option value="15m">15 Dakika (15m)</option>
          </Select>

          <Button
            variant="outline"
            size="sm"
            onClick={() => loadOpportunities()}
            disabled={loading}
          >
            <RefreshCw
              className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"}
              aria-hidden="true"
            />
            <span>Yenile</span>
          </Button>
        </div>
      </div>

      <Alert
        variant="info"
        title="Kural Tabanlı Karar Destek Analizi · Tavan Garantisi veya Al/Sat Tavsiyesi Değildir"
      >
        Tavan tahmini veya al/sat tavsiyesi değildir; hacim artışı, direnç kırılımı, ATR ve destek/direnç
        indikatörlerinden türetilen kural tabanlı karar destek analizidir. BIST +%10 tavan ve -%10 taban değerleri
        yalnızca günlük teorik fiyat marjı sınırlarını gösterir.
      </Alert>

      {errorMessage ? (
        <Alert variant="error" title="Aday Analizi Yüklenemedi">
          <p>{errorMessage}</p>
          <div className="mt-2">
            <Button variant="outline" size="sm" onClick={() => loadOpportunities()}>
              Yeniden Dene
            </Button>
          </div>
        </Alert>
      ) : null}

      {data ? (
        <DataProvenanceBar
          dataSource={data.dataSource}
          freshness={data.freshness}
          timezone={effectiveTimezone}
          extraLabel={`Motor: ${data.setupEngineVersion} / ${data.scoringAlgorithmVersion}`}
        />
      ) : null}

      {syncStatus ? (
        <div className="flex flex-col gap-2.5 rounded-lg border border-border bg-surface-muted/40 px-4 py-3 text-xs sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap items-center gap-2">
            <Database className="h-4 w-4 text-accent shrink-0" aria-hidden="true" />
            <span>
              <strong>{syncStatus.exchange} Kapsam Durumu:</strong>{" "}
              <strong className="text-foreground tabular-nums">
                {syncStatus.syncedInstruments} / {syncStatus.totalActiveInstruments}
              </strong>{" "}
              hissenin {syncStatus.interval} verisi senkronize
              {syncStatus.remainingInstruments > 0
                ? ` (${syncStatus.remainingInstruments} hisse bekliyor)`
                : " (tüm katalog güncel)"}
            </span>
            {batchMessage ? (
              <Badge variant="info">{batchMessage}</Badge>
            ) : null}
          </div>

          {syncStatus.remainingInstruments > 0 ? (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={handleSyncBatch}
              isLoading={syncingBatch}
              disabled={syncingBatch}
            >
              <Database className="h-3.5 w-3.5" aria-hidden="true" />
              <span>+25 Hisse Senkronize Et</span>
            </Button>
          ) : null}
        </div>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <button
          type="button"
          onClick={() => handleSelectCategory("HIGH_MOMENTUM_BREAKOUT")}
          className={`text-left rounded-lg border p-4 transition-colors ${
            category === "HIGH_MOMENTUM_BREAKOUT"
              ? "border-positive bg-positive/10"
              : "border-border bg-surface hover:bg-surface-muted/40"
          }`}
        >
          <div className="flex items-center justify-between text-xs text-muted">
            <span>Yüksek Momentum / Kırılım</span>
            <Flame className="h-4 w-4 text-positive" aria-hidden="true" />
          </div>
          <p className="mt-1.5 text-2xl font-bold tabular-nums text-foreground">
            {counts.HIGH_MOMENTUM_BREAKOUT ?? 0}
          </p>
          <p className="mt-0.5 text-[11px] text-muted">
            Hacim destekli direnç eşiği profilleri
          </p>
        </button>

        <button
          type="button"
          onClick={() => handleSelectCategory("PULLBACK_WATCH")}
          className={`text-left rounded-lg border p-4 transition-colors ${
            category === "PULLBACK_WATCH"
              ? "border-info bg-info/10"
              : "border-border bg-surface hover:bg-surface-muted/40"
          }`}
        >
          <div className="flex items-center justify-between text-xs text-muted">
            <span>Destek İzleme Adayları</span>
            <Target className="h-4 w-4 text-info" aria-hidden="true" />
          </div>
          <p className="mt-1.5 text-2xl font-bold tabular-nums text-foreground">
            {counts.PULLBACK_WATCH ?? 0}
          </p>
          <p className="mt-0.5 text-[11px] text-muted">
            Destek/EMA bölgesinde dengeli RSI
          </p>
        </button>

        <button
          type="button"
          onClick={() => handleSelectCategory("OVEREXTENDED_RISK")}
          className={`text-left rounded-lg border p-4 transition-colors ${
            category === "OVEREXTENDED_RISK"
              ? "border-warning bg-warning/10"
              : "border-border bg-surface hover:bg-surface-muted/40"
          }`}
        >
          <div className="flex items-center justify-between text-xs text-muted">
            <span>Aşırı Isınma Riski</span>
            <AlertTriangle className="h-4 w-4 text-warning" aria-hidden="true" />
          </div>
          <p className="mt-1.5 text-2xl font-bold tabular-nums text-foreground">
            {counts.OVEREXTENDED_RISK ?? 0}
          </p>
          <p className="mt-0.5 text-[11px] text-muted">
            RSI &gt; 75 veya üst bant uzaması
          </p>
        </button>

        <button
          type="button"
          onClick={() => handleSelectCategory("")}
          className={`text-left rounded-lg border p-4 transition-colors ${
            category === ""
              ? "border-accent bg-accent/10"
              : "border-border bg-surface hover:bg-surface-muted/40"
          }`}
        >
          <div className="flex items-center justify-between text-xs text-muted">
            <span>Tüm İncelenen Hisseler</span>
            <Layers className="h-4 w-4 text-accent" aria-hidden="true" />
          </div>
          <p className="mt-1.5 text-2xl font-bold tabular-nums text-foreground">
            {totalTracked}
          </p>
          <p className="mt-0.5 text-[11px] text-muted">
            BIST ±%10 marj ve ATR referansları
          </p>
        </button>
      </div>

      <Card>
        <CardContent className="pt-4 space-y-4">
          <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Kategori filtresi">
            {CATEGORY_FILTER_OPTIONS.map((opt) => {
              const active = category === opt.value;
              const optCount =
                opt.value === "" ? totalTracked : counts[opt.value] ?? 0;
              return (
                <button
                  key={opt.label}
                  type="button"
                  aria-pressed={active}
                  onClick={() => handleSelectCategory(opt.value)}
                  className={`rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ${
                    active
                      ? "border-accent bg-accent/15 text-accent"
                      : "border-border bg-surface-muted/40 text-muted hover:text-foreground"
                  }`}
                >
                  {opt.label} ({optCount})
                </button>
              );
            })}
          </div>

          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between border-t border-border pt-3">
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-2">
                <label htmlFor="opp-scope" className="text-xs font-medium text-muted">
                  Kapsam:
                </label>
                <Select
                  id="opp-scope"
                  aria-label="Hisse kapsamı"
                  value={scope}
                  onChange={(e) => {
                    setScope(e.target.value as UniverseScope);
                    setPage(0);
                  }}
                  className="w-44"
                >
                  <option value="BIST30">İlk 30 (BIST30)</option>
                  <option value="BIST100">İlk 100 (BIST100)</option>
                  <option value="SYNCED">Senkronize Hisseler</option>
                  <option value="ALL">Tüm BIST (~625)</option>
                </Select>
              </div>

              <form onSubmit={handleSearchSubmit} className="flex items-center gap-1.5">
                <div className="relative">
                  <Search
                    className="pointer-events-none absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted"
                    aria-hidden="true"
                  />
                  <Input
                    value={searchInput}
                    onChange={(e) => setSearchInput(e.target.value)}
                    placeholder="Tüm BIST'te ara (ör. PGSUS)"
                    aria-label="Tüm BIST içinde ara"
                    className="h-9 w-48 pl-8 text-xs"
                  />
                </div>
                <Button type="submit" variant="secondary" size="sm">
                  Ara
                </Button>
                {appliedQuery ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setSearchInput("");
                      setAppliedQuery("");
                      setPage(0);
                    }}
                  >
                    Temizle
                  </Button>
                ) : null}
              </form>

              <div className="flex items-center gap-2">
                <label htmlFor="opp-sort-by" className="text-xs font-medium text-muted">
                  Sıralama:
                </label>
                <Select
                  id="opp-sort-by"
                  aria-label="Sıralama ölçütü"
                  value={sortBy}
                  onChange={(e) => {
                    const next = e.target.value as OpportunitiesSortField;
                    setSortBy(next);
                    setSortDir(next === "ceilingDistance" || next === "symbol" ? "asc" : "desc");
                    setPage(0);
                  }}
                  className="w-44"
                >
                  <option value="momentum">Momentum Skoru</option>
                  <option value="score">Bileşik Teknik Skor</option>
                  <option value="volumeRatio">Hacim Oranı</option>
                  <option value="ceilingDistance">Tavan Marjına Yakınlık</option>
                  <option value="riskReward">Risk / Ödül (R2)</option>
                  <option value="symbol">Sembol</option>
                </Select>
              </div>

              <div className="flex items-center gap-2">
                <label htmlFor="opp-sort-dir" className="text-xs font-medium text-muted">
                  Yön:
                </label>
                <Select
                  id="opp-sort-dir"
                  aria-label="Sıralama yönü"
                  value={sortDir}
                  onChange={(e) => {
                    setSortDir(e.target.value as "asc" | "desc");
                    setPage(0);
                  }}
                  className="w-28"
                >
                  <option value="desc">Azalan</option>
                  <option value="asc">Artan</option>
                </Select>
              </div>
            </div>

            <label className="flex items-center gap-2 text-xs text-muted cursor-pointer">
              <input
                type="checkbox"
                checked={includeInsufficientData}
                onChange={(e) => {
                  setIncludeInsufficientData(e.target.checked);
                  setPage(0);
                }}
                className="h-4 w-4 rounded accent-accent"
              />
              <span>Yetersiz veri (INSUFFICIENT_DATA) olanları göster</span>
            </label>
          </div>
        </CardContent>
      </Card>

      {loading && !data ? (
        <div className="space-y-4" aria-busy="true" aria-label="Adaylar yükleniyor">
          <Skeleton className="h-56" />
          <Skeleton className="h-56" />
        </div>
      ) : null}

      {data && data.items.length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center space-y-3">
            <p className="text-sm text-muted">
              Seçili kategori veya filtre ölçütleriyle eşleşen teknik kurulum bulunamadı.
            </p>
            <Button variant="outline" size="sm" onClick={handleResetFilters}>
              <RotateCcw className="h-4 w-4" aria-hidden="true" />
              <span>Filtreleri Sıfırla</span>
            </Button>
          </CardContent>
        </Card>
      ) : null}

      {data && data.items.length > 0 ? (
        <div className="space-y-4">
          {data.items.map((item) => (
            <Card key={item.symbol}>
              <CardHeader>
                <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Link
                        href={`/instruments/${encodeURIComponent(item.symbol)}`}
                        className="text-lg font-bold text-accent hover:underline"
                      >
                        {item.symbol}
                      </Link>
                      <span className="text-sm text-muted">· {item.name}</span>
                      {item.sector ? (
                        <Badge variant="neutral">{translateSector(item.sector)}</Badge>
                      ) : null}
                    </div>
                    <CardDescription>{item.headline}</CardDescription>
                  </div>

                  <div className="flex flex-wrap items-center gap-1.5">
                    <SetupCategoryBadge category={item.category} />
                    <TrendBadge trend={item.trendDirection} />
                    <Badge variant="accent" className="tabular-nums">
                      Momentum: {formatNumber(item.momentumBreakoutScore, 1)} / 100
                    </Badge>
                    <ScorePill score={item.technicalScore} status={item.status} />
                  </div>
                </div>
              </CardHeader>

              <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6 text-xs">
                  <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
                    <span className="text-muted block">Son Fiyat</span>
                    <span className="mt-0.5 text-sm font-bold tabular-nums text-foreground">
                      {formatCurrency(
                        item.bistMargin?.referencePrice ?? item.technicalReferencePrice,
                        item.currency
                      )}
                    </span>
                  </div>
                  <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
                    <span className="text-muted block">Günlük Değişim</span>
                    <div className="mt-1">
                      <SignedPercentBadge value={item.bistMargin?.dailyChangePercent} />
                    </div>
                  </div>
                  <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
                    <span className="text-muted block">BIST +%10 Tavan Ref.</span>
                    <span className="mt-0.5 text-sm font-semibold tabular-nums text-positive">
                      {formatCurrency(item.bistMargin?.theoreticalCeilingPrice, item.currency)}
                    </span>
                    <span className="block text-[10px] text-muted tabular-nums">
                      Mesafe: %{formatNumber(item.bistMargin?.distanceToCeilingPercent, 2)}
                    </span>
                  </div>
                  <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
                    <span className="text-muted block">Hacim Oranı</span>
                    <span className="mt-0.5 text-sm font-bold tabular-nums text-foreground">
                      {item.volumeRatio !== null ? `${formatNumber(item.volumeRatio, 2)}x` : "—"}
                    </span>
                    <span className="block text-[10px] text-muted">20 bar ort. göre</span>
                  </div>
                  <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
                    <span className="text-muted block">RSI(14)</span>
                    <span className="mt-0.5 text-sm font-bold tabular-nums text-foreground">
                      {formatNumber(item.latestRsi, 1)}
                    </span>
                  </div>
                  <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
                    <span className="text-muted block">ATR(14) Volatilite</span>
                    <span className="mt-0.5 text-sm font-semibold tabular-nums text-foreground">
                      {item.atr14 !== null ? formatCurrency(item.atr14, item.currency) : "—"}
                    </span>
                    <span className="block text-[10px] text-muted tabular-nums">
                      %{formatNumber(item.atrPercent, 2)}
                    </span>
                  </div>
                </div>

                <TechnicalSetupBody
                  data={{
                    currency: item.currency,
                    status: item.status,
                    category: item.category,
                    technicalReferencePrice: item.technicalReferencePrice,
                    breakoutTriggerLevel: item.breakoutTriggerLevel,
                    distanceToBreakoutPercent: item.distanceToBreakoutPercent,
                    breakoutConfirmed: item.breakoutConfirmed,
                    supportWatchZone: item.supportWatchZone,
                    resistanceExitZone: item.resistanceExitZone,
                    invalidationRiskLevel: item.invalidationRiskLevel,
                    invalidationRationale: item.invalidationRationale,
                    riskReward: item.riskReward,
                    bistMargin: item.bistMargin,
                    rationaleItems: item.rationaleItems,
                    riskWarnings: item.riskWarnings,
                    usedIndicators: item.usedIndicators,
                    insufficientDataReasons: item.insufficientDataReasons,
                  }}
                />
              </CardContent>

              <CardFooter>
                <span>
                  Kategori: {translateSetupCategory(item.category)} · Hesaplama:{" "}
                  {formatDateTime(item.calculatedAt, effectiveTimezone)}
                </span>
                <div className="flex flex-wrap items-center gap-2">
                  <Link
                    href={`/alerts?symbol=${encodeURIComponent(item.symbol)}`}
                    className={buttonClasses({ variant: "outline", size: "sm" })}
                  >
                    <BellPlus className="h-3.5 w-3.5" aria-hidden="true" />
                    <span>Alarm Oluştur</span>
                  </Link>
                  <Link
                    href={`/instruments/${encodeURIComponent(item.symbol)}`}
                    className={buttonClasses({ variant: "secondary", size: "sm" })}
                  >
                    <span>Grafik ve Detay</span>
                    <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                  </Link>
                </div>
              </CardFooter>
            </Card>
          ))}

          {data.totalPages > 1 ? (
            <div className="flex items-center justify-between text-xs text-muted">
              <span>
                Sayfa {data.page + 1} / {data.totalPages} · Toplam {data.totalElements} aday
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page <= 0 || loading}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Önceki
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= data.totalPages || loading}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Sonraki
                </Button>
              </div>
            </div>
          ) : null}
        </div>
      ) : null}

      <DecisionSupportDisclaimer disclaimer={data?.disclaimer} />
    </div>
  );
}
