"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Filter, RefreshCw, RotateCcw } from "lucide-react";
import { useAuth } from "@/components/providers/auth-provider";
import {
  DataProvenanceBar,
  DecisionSupportDisclaimer,
  RegimeBadge,
  ScorePill,
  TrendBadge,
} from "@/components/shared/data-status-badges";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input, Label, Select } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { isApiClientError } from "@/lib/api/client";
import { apiServices } from "@/lib/api/services";
import type {
  ScreenerPageResponse,
  ScreenerQueryParams,
  UniverseScope,
} from "@/lib/api/types";
import {
  DEFAULT_EXCHANGE,
  formatCurrency,
  formatDateTime,
  formatNumber,
} from "@/lib/format";
import { translateSector } from "@/lib/labels";

interface FilterDraft {
  exchange: string;
  interval: string;
  scope: UniverseScope;
  query: string;
  minScore: string;
  maxScore: string;
  rsiMin: string;
  rsiMax: string;
  trend: string;
  includeInsufficientData: boolean;
  sortBy: "score" | "symbol" | "rsi";
  sortDir: "asc" | "desc";
}

const DEFAULT_DRAFT: FilterDraft = {
  exchange: DEFAULT_EXCHANGE,
  interval: "1d",
  scope: "BIST30",
  query: "",
  minScore: "",
  maxScore: "",
  rsiMin: "",
  rsiMax: "",
  trend: "",
  includeInsufficientData: true,
  sortBy: "score",
  sortDir: "desc",
};

function parseOptionalNumber(raw: string): number | undefined {
  const trimmed = raw.trim();
  if (!trimmed) {
    return undefined;
  }
  const n = Number(trimmed);
  return Number.isFinite(n) ? n : undefined;
}

export function ScreenerView() {
  const { effectiveTimezone } = useAuth();
  const [draft, setDraft] = useState<FilterDraft>(DEFAULT_DRAFT);
  const [applied, setApplied] = useState<ScreenerQueryParams>({
    exchange: DEFAULT_EXCHANGE,
    interval: "1d",
    scope: "BIST30",
    includeInsufficientData: true,
    sortBy: "score",
    sortDir: "desc",
    page: 0,
    size: 20,
  });

  const [data, setData] = useState<ScreenerPageResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const fetchScreener = useCallback(
    async (params: ScreenerQueryParams, signal?: AbortSignal) => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const res = await apiServices.screener.screen(params, signal);
        setData(res);
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") {
          return;
        }
        setErrorMessage(
          isApiClientError(err)
            ? `${err.message} (${err.code})`
            : "Teknik tarayıcı sorgusu çalıştırılamadı."
        );
      } finally {
        setLoading(false);
      }
    },
    []
  );

  useEffect(() => {
    const controller = new AbortController();
    fetchScreener(applied, controller.signal);
    return () => controller.abort();
  }, [applied, fetchScreener]);

  const handleApply = (event: React.FormEvent) => {
    event.preventDefault();
    setValidationError(null);

    const minScore = parseOptionalNumber(draft.minScore);
    const maxScore = parseOptionalNumber(draft.maxScore);
    const rsiMin = parseOptionalNumber(draft.rsiMin);
    const rsiMax = parseOptionalNumber(draft.rsiMax);

    if (minScore !== undefined && maxScore !== undefined && minScore > maxScore) {
      setValidationError("Minimum skor, maksimum skordan büyük olamaz.");
      return;
    }
    if (rsiMin !== undefined && rsiMax !== undefined && rsiMin > rsiMax) {
      setValidationError("Minimum RSI, maksimum RSI değerinden büyük olamaz.");
      return;
    }

    setApplied({
      exchange: draft.exchange || DEFAULT_EXCHANGE,
      interval: draft.interval || "1d",
      scope: draft.scope,
      query: draft.query.trim() || undefined,
      minScore,
      maxScore,
      rsiMin,
      rsiMax,
      trend: draft.trend || undefined,
      includeInsufficientData: draft.includeInsufficientData,
      sortBy: draft.sortBy,
      sortDir: draft.sortDir,
      page: 0,
      size: 20,
    });
  };

  const handleReset = () => {
    setValidationError(null);
    setDraft(DEFAULT_DRAFT);
    setApplied({
      exchange: DEFAULT_EXCHANGE,
      interval: "1d",
      scope: "BIST30",
      includeInsufficientData: true,
      sortBy: "score",
      sortDir: "desc",
      page: 0,
      size: 20,
    });
  };

  const currentPage = data?.page ?? applied.page ?? 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight sm:text-2xl">
            Açıklanabilir Teknik Tarayıcı
          </h1>
          <p className="text-sm text-muted">
            Aktif enstrümanları bileşik teknik skora (0–100), RSI sınırlarına ve trend yönüne göre filtreleyin.
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => fetchScreener(applied)}
          disabled={loading}
        >
          <RefreshCw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} aria-hidden="true" />
          <span>Sorguyu Yenile</span>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Filter className="h-4 w-4 text-accent" aria-hidden="true" />
            <span>Tarayıcı Ölçütleri</span>
          </CardTitle>
          <CardDescription>
            Deterministik kural tabanlı filtreleme. Gerekli veri geçmişi eksik olan hisseler açıkça Yetersiz Veri (INSUFFICIENT_DATA) olarak işaretlenir.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleApply} className="space-y-4">
            {validationError ? (
              <Alert variant="error" title="Geçersiz Filtre Sınırları">
                {validationError}
              </Alert>
            ) : null}

            <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4 xl:grid-cols-5">
              <div className="space-y-1">
                <Label htmlFor="scr-exchange">Borsa</Label>
                <Input
                  id="scr-exchange"
                  value={draft.exchange}
                  onChange={(e) =>
                    setDraft((d) => ({ ...d, exchange: e.target.value.toUpperCase() }))
                  }
                  placeholder="BIST"
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="scr-interval">Bar Aralığı</Label>
                <Select
                  id="scr-interval"
                  value={draft.interval}
                  onChange={(e) => setDraft((d) => ({ ...d, interval: e.target.value }))}
                >
                  <option value="1d">Günlük (1d)</option>
                  <option value="1h">Saatlik (1h)</option>
                  <option value="15m">15 Dakika (15m)</option>
                </Select>
              </div>

              <div className="space-y-1">
                <Label htmlFor="scr-scope">Hisse Kapsamı</Label>
                <Select
                  id="scr-scope"
                  value={draft.scope}
                  onChange={(e) =>
                    setDraft((d) => ({ ...d, scope: e.target.value as UniverseScope }))
                  }
                >
                  <option value="BIST30">İlk 30 (BIST30)</option>
                  <option value="BIST100">İlk 100 (BIST100)</option>
                  <option value="SYNCED">Senkronize Hisseler</option>
                  <option value="ALL">Tüm BIST (~625)</option>
                </Select>
              </div>

              <div className="space-y-1">
                <Label htmlFor="scr-query">Sembol / Şirket</Label>
                <Input
                  id="scr-query"
                  value={draft.query}
                  onChange={(e) => setDraft((d) => ({ ...d, query: e.target.value }))}
                  placeholder="ör. PGSUS veya Banka"
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="scr-min-score">Min. Skor (0–100)</Label>
                <Input
                  id="scr-min-score"
                  type="number"
                  min={0}
                  max={100}
                  value={draft.minScore}
                  onChange={(e) => setDraft((d) => ({ ...d, minScore: e.target.value }))}
                  placeholder="ör. 55"
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="scr-max-score">Maks. Skor (0–100)</Label>
                <Input
                  id="scr-max-score"
                  type="number"
                  min={0}
                  max={100}
                  value={draft.maxScore}
                  onChange={(e) => setDraft((d) => ({ ...d, maxScore: e.target.value }))}
                  placeholder="ör. 90"
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="scr-trend">Trend Yönü</Label>
                <Select
                  id="scr-trend"
                  value={draft.trend}
                  onChange={(e) => setDraft((d) => ({ ...d, trend: e.target.value }))}
                >
                  <option value="">Tüm Trendler</option>
                  <option value="BULLISH">Yükseliş</option>
                  <option value="NEUTRAL">Yatay / Nötr</option>
                  <option value="BEARISH">Düşüş</option>
                </Select>
              </div>

              <div className="space-y-1">
                <Label htmlFor="scr-rsi-min">RSI Min. (0–100)</Label>
                <Input
                  id="scr-rsi-min"
                  type="number"
                  min={0}
                  max={100}
                  value={draft.rsiMin}
                  onChange={(e) => setDraft((d) => ({ ...d, rsiMin: e.target.value }))}
                  placeholder="ör. 30"
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="scr-rsi-max">RSI Maks. (0–100)</Label>
                <Input
                  id="scr-rsi-max"
                  type="number"
                  min={0}
                  max={100}
                  value={draft.rsiMax}
                  onChange={(e) => setDraft((d) => ({ ...d, rsiMax: e.target.value }))}
                  placeholder="ör. 70"
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="scr-sort-by">Sıralama Ölçütü</Label>
                <Select
                  id="scr-sort-by"
                  value={draft.sortBy}
                  onChange={(e) =>
                    setDraft((d) => ({
                      ...d,
                      sortBy: e.target.value as FilterDraft["sortBy"],
                    }))
                  }
                >
                  <option value="score">Bileşik Skor</option>
                  <option value="rsi">Son RSI</option>
                  <option value="symbol">Sembol Kodu</option>
                </Select>
              </div>

              <div className="space-y-1">
                <Label htmlFor="scr-sort-dir">Sıralama Yönü</Label>
                <Select
                  id="scr-sort-dir"
                  value={draft.sortDir}
                  onChange={(e) =>
                    setDraft((d) => ({
                      ...d,
                      sortDir: e.target.value as FilterDraft["sortDir"],
                    }))
                  }
                >
                  <option value="desc">Azalan (Yüksekten Düşüğe)</option>
                  <option value="asc">Artan (Düşükten Yükseğe)</option>
                </Select>
              </div>

              <div className="flex items-end pb-1">
                <label className="flex items-center gap-2 text-xs font-medium text-foreground cursor-pointer">
                  <input
                    type="checkbox"
                    checked={draft.includeInsufficientData}
                    onChange={(e) =>
                      setDraft((d) => ({
                        ...d,
                        includeInsufficientData: e.target.checked,
                      }))
                    }
                    className="h-4 w-4 rounded border-border accent-accent"
                  />
                  <span>INSUFFICIENT_DATA Kayıtlarını Göster</span>
                </label>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2 pt-1">
              <Button type="submit" size="sm" isLoading={loading}>
                <Filter className="h-4 w-4" aria-hidden="true" />
                <span>Filtreleri Uygula</span>
              </Button>
              <Button type="button" size="sm" variant="outline" onClick={handleReset}>
                <RotateCcw className="h-4 w-4" aria-hidden="true" />
                <span>Sıfırla</span>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {errorMessage ? (
        <Alert variant="error" title="Tarayıcı İsteği Başarısız">
          {errorMessage}
        </Alert>
      ) : null}

      {data ? (
        <DataProvenanceBar
          dataSource={data.dataSource}
          freshness={data.freshness}
          timezone={effectiveTimezone}
          extraLabel={`Motor: ${data.algorithmVersion} · Aralık: ${data.interval}`}
        />
      ) : null}

      {loading && !data ? (
        <Skeleton className="h-80 w-full" />
      ) : null}

      {data ? (
        <Card>
          <CardHeader className="sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle>Tarama Sonuçları ({data.totalElements})</CardTitle>
              <CardDescription>
                Sayfa {data.page + 1} / {Math.max(1, data.totalPages)} · Sıralama:{" "}
                {applied.sortBy === "rsi"
                  ? "Son RSI"
                  : applied.sortBy === "symbol"
                  ? "Sembol"
                  : "Skor"}{" "}
                ({applied.sortDir === "asc" ? "Artan" : "Azalan"})
              </CardDescription>
            </div>
            <Badge variant="neutral">{data.algorithmVersion}</Badge>
          </CardHeader>
          <CardContent className="space-y-4">
            {data.items.length > 0 ? (
              <>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Enstrüman</TableHead>
                      <TableHead className="text-right">Son Kapanış</TableHead>
                      <TableHead className="text-right">RSI (14)</TableHead>
                      <TableHead className="text-right">Skor</TableHead>
                      <TableHead>Rejim ve Trend</TableHead>
                      <TableHead>Özet ve İşaretler</TableHead>
                      <TableHead>Hesaplama</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.items.map((item) => (
                      <TableRow key={item.scoreRunId || item.symbol}>
                        <TableCell>
                          <Link
                            href={`/instruments/${encodeURIComponent(item.symbol)}`}
                            className="font-semibold text-accent hover:underline"
                          >
                            {item.symbol}
                          </Link>
                          <p className="text-xs text-muted line-clamp-1">
                            {item.name}
                            {item.sector ? ` · ${translateSector(item.sector)}` : ""}
                          </p>
                        </TableCell>
                        <TableCell className="text-right font-medium tabular-nums">
                          {formatCurrency(item.latestClose, item.currency)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatNumber(item.latestRsi, 2)}
                        </TableCell>
                        <TableCell className="text-right">
                          <ScorePill score={item.finalScore} status={item.status} />
                        </TableCell>
                        <TableCell>
                          <div className="flex flex-wrap gap-1">
                            <RegimeBadge regime={item.regime} status={item.status} />
                            <TrendBadge trend={item.trendDirection} />
                          </div>
                        </TableCell>
                        <TableCell className="max-w-xs">
                          <p className="text-xs text-foreground/90">{item.headline}</p>
                          <div className="mt-1 flex flex-wrap gap-1">
                            {item.simulated ? (
                              <Badge variant="warning">Simüle</Badge>
                            ) : null}
                            {item.staleData ? (
                              <Badge variant="warning">
                                <AlertTriangle className="h-3 w-3" aria-hidden="true" />
                                <span>Bayat Veri</span>
                              </Badge>
                            ) : null}
                          </div>
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-xs text-muted tabular-nums">
                          {formatDateTime(item.calculatedAt, effectiveTimezone)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>

                <div className="flex items-center justify-between text-xs text-muted">
                  <span>
                    Sayfa {data.page + 1} / {Math.max(1, data.totalPages)} ·{" "}
                    {data.totalElements} eşleşme
                  </span>
                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={currentPage <= 0}
                      onClick={() =>
                        setApplied((prev) => ({
                          ...prev,
                          page: Math.max(0, (prev.page ?? 0) - 1),
                        }))
                      }
                    >
                      Önceki
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={currentPage + 1 >= data.totalPages}
                      onClick={() =>
                        setApplied((prev) => ({
                          ...prev,
                          page: (prev.page ?? 0) + 1,
                        }))
                      }
                    >
                      Sonraki
                    </Button>
                  </div>
                </div>
              </>
            ) : (
              <div className="rounded-md border border-dashed border-border p-8 text-center space-y-3">
                <p className="text-sm text-muted">
                  Tarayıcı ölçütlerinizle eşleşen enstrüman bulunamadı.
                </p>
                <Button variant="outline" size="sm" onClick={handleReset}>
                  Tüm Filtreleri Sıfırla
                </Button>
              </div>
            )}

            <DecisionSupportDisclaimer disclaimer={data.disclaimer} />
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
