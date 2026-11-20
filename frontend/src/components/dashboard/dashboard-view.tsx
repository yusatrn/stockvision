"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  BarChart3,
  Clock,
  Eye,
  Filter,
  RefreshCw,
  Target,
} from "lucide-react";
import { useAuth } from "@/components/providers/auth-provider";
import {
  DataProvenanceBar,
  FreshnessBadge,
  ProviderModeBadge,
  RegimeBadge,
  ScorePill,
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
  CardTitle,
} from "@/components/ui/card";
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
  MarketSummaryResponse,
  ScreenerPageResponse,
  WatchlistCollectionResponse,
} from "@/lib/api/types";
import {
  DEFAULT_EXCHANGE,
  formatCompactNumber,
  formatCurrency,
  formatDateTime,
  formatNumber,
  formatRelativeAgeMinutes,
} from "@/lib/format";
import { translateSyncStatus } from "@/lib/labels";

export function DashboardView() {
  const { effectiveTimezone } = useAuth();
  const [summary, setSummary] = useState<MarketSummaryResponse | null>(null);
  const [screener, setScreener] = useState<ScreenerPageResponse | null>(null);
  const [watchlists, setWatchlists] = useState<WatchlistCollectionResponse | null>(
    null
  );
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | undefined>(undefined);

  const loadDashboard = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setErrorMessage(null);
    setTraceId(undefined);

    try {
      const [marketData, screenerData, watchlistData] = await Promise.all([
        apiServices.markets.getSummary(DEFAULT_EXCHANGE, signal),
        apiServices.screener.screen(
          {
            exchange: DEFAULT_EXCHANGE,
            interval: "1d",
            includeInsufficientData: true,
            sortBy: "score",
            sortDir: "desc",
            page: 0,
            size: 6,
          },
          signal
        ),
        apiServices.watchlists.list(signal).catch(() => null),
      ]);
      setSummary(marketData);
      setScreener(screenerData);
      setWatchlists(watchlistData);
    } catch (err) {
      if (err instanceof DOMException && err.name === "AbortError") {
        return;
      }
      if (isApiClientError(err)) {
        setErrorMessage(`${err.message} (${err.code})`);
        setTraceId(err.traceId);
      } else {
        setErrorMessage("Kontrol paneli piyasa ve analiz verileri yüklenemedi.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    loadDashboard(controller.signal);
    return () => controller.abort();
  }, [loadDashboard]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight sm:text-2xl">
            Piyasa ve Analiz Kontrol Paneli
          </h1>
          <p className="text-sm text-muted">
            Borsa İstanbul öncelikli piyasa özeti, veri tazeliği telemetrisi ve son deterministik teknik skor koşuları.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => loadDashboard()}
            disabled={loading}
          >
            <RefreshCw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} aria-hidden="true" />
            <span>Yenile</span>
          </Button>
          <Link
            href="/opportunities"
            className={buttonClasses({ variant: "secondary", size: "sm" })}
          >
            <Target className="h-4 w-4" aria-hidden="true" />
            <span>Adaylar ve Seviyeler</span>
          </Link>
          <Link
            href="/screener"
            className={buttonClasses({ variant: "primary", size: "sm" })}
          >
            <Filter className="h-4 w-4" aria-hidden="true" />
            <span>Tarayıcıyı Aç</span>
          </Link>
        </div>
      </div>

      {errorMessage ? (
        <Alert variant="error" title="Kontrol Paneli Verileri Yüklenemedi">
          <p>{errorMessage}</p>
          {traceId ? <p className="mt-1 text-xs text-muted">İzleme Kimliği (Trace ID): {traceId}</p> : null}
          <div className="mt-3">
            <Button variant="outline" size="sm" onClick={() => loadDashboard()}>
              Yeniden Dene
            </Button>
          </div>
        </Alert>
      ) : null}

      {loading && !summary ? (
        <div className="space-y-4" aria-busy="true" aria-label="Kontrol paneli yükleniyor">
          <Skeleton className="h-14 w-full" />
          <div className="grid gap-4 lg:grid-cols-2">
            <Skeleton className="h-44" />
            <Skeleton className="h-44" />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <Skeleton className="h-72" />
            <Skeleton className="h-72" />
          </div>
        </div>
      ) : null}

      {summary ? (
        <DataProvenanceBar
          dataSource={summary.dataSource}
          freshness={summary.freshness}
          timezone={effectiveTimezone}
          extraLabel={`${summary.exchange.code} (${summary.exchange.currency})`}
        />
      ) : null}

      {summary ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between gap-2">
                <CardTitle className="flex items-center gap-2">
                  <BarChart3 className="h-4 w-4 text-accent" aria-hidden="true" />
                  <span>Piyasa Özeti · {summary.exchange.code}</span>
                </CardTitle>
                <ProviderModeBadge
                  mode={summary.dataSource.mode}
                  simulated={summary.simulated}
                  delayMinutes={summary.dataSource.delayMinutes}
                />
              </div>
              <CardDescription>{summary.exchange.name}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                  <p className="text-xs text-muted">Aktif Semboller</p>
                  <p className="mt-1 text-lg font-bold tabular-nums">
                    {summary.activeInstrumentCount}
                  </p>
                </div>
                <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                  <p className="text-xs text-muted">Seans (Yerel)</p>
                  <p className="mt-1 text-sm font-semibold tabular-nums">
                    {summary.exchange.sessionOpenLocal} – {summary.exchange.sessionCloseLocal}
                  </p>
                  <p className="text-[11px] text-muted">{summary.exchange.timezone}</p>
                </div>
                <div className="rounded-md border border-border bg-surface-muted/40 p-3 col-span-2 sm:col-span-1">
                  <p className="text-xs text-muted">Takas Para Birimi</p>
                  <p className="mt-1 text-lg font-bold">{summary.exchange.currency}</p>
                </div>
              </div>
              <p className="text-xs leading-relaxed text-muted">{summary.summaryNote}</p>
            </CardContent>
            <CardFooter>
              <span>Borsa Ülkesi: {summary.exchange.countryCode}</span>
              <Link
                href="/markets"
                className="inline-flex items-center gap-1 font-medium text-accent hover:underline"
              >
                <span>Piyasalara Göz At</span>
                <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
              </Link>
            </CardFooter>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex items-center justify-between gap-2">
                <CardTitle className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-accent" aria-hidden="true" />
                  <span>Veri Tazeliği ve Akış Durumu</span>
                </CardTitle>
                <FreshnessBadge freshness={summary.freshness} />
              </div>
              <CardDescription>
                UTC zaman damgaları {effectiveTimezone} saat dilimine çevrildi
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3 text-xs">
              <dl className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
                  <dt className="text-muted">Etkin Sağlayıcı</dt>
                  <dd className="mt-0.5 font-semibold text-foreground">
                    {summary.dataSource.displayName} ({summary.dataSource.providerId})
                  </dd>
                </div>
                <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
                  <dt className="text-muted">Son Veri Zamanı</dt>
                  <dd className="mt-0.5 font-semibold text-foreground tabular-nums">
                    {formatDateTime(summary.freshness.latestDataTimestamp, effectiveTimezone)}
                  </dd>
                </div>
                <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
                  <dt className="text-muted">Son Senkronizasyon ve Durum</dt>
                  <dd className="mt-0.5 font-semibold text-foreground">
                    {translateSyncStatus(summary.freshness.lastSyncStatus)} ·{" "}
                    {formatDateTime(summary.freshness.lastSyncedAt, effectiveTimezone)}
                  </dd>
                </div>
                <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
                  <dt className="text-muted">Veri Yaşı ve Gecikme</dt>
                  <dd className="mt-0.5 font-semibold text-foreground">
                    {formatRelativeAgeMinutes(summary.freshness.ageMinutes)} ·{" "}
                    {summary.dataSource.delayMinutes > 0
                      ? `${summary.dataSource.delayMinutes} dk gecikme`
                      : "0 dk gecikme"}
                  </dd>
                </div>
              </dl>
              <p className="leading-relaxed text-muted">{summary.freshness.note}</p>
            </CardContent>
            <CardFooter>
              <span>Akış Saat Dilimi: {summary.dataSource.sourceTimezone}</span>
              <Link href="/settings" className="font-medium text-accent hover:underline">
                Saat Dilimini Değiştir
              </Link>
            </CardFooter>
          </Card>
        </div>
      ) : null}

      <div className="grid gap-4 xl:grid-cols-2">
        {(() => {
          const savedWatchlist =
            watchlists?.watchlists.find((wl) => wl.items.length > 0) ?? null;

          return (
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between gap-2">
                  <CardTitle className="flex items-center gap-2">
                    <Eye className="h-4 w-4 text-accent" aria-hidden="true" />
                    <span>
                      {savedWatchlist
                        ? `İzleme Listesi · ${savedWatchlist.name}`
                        : "İzleme Listesi ve Öne Çıkan Hisseler"}
                    </span>
                  </CardTitle>
                  <Badge variant={savedWatchlist ? "accent" : "info"}>
                    {savedWatchlist ? "Kayıtlı Liste" : "Piyasa Hareketleri"}
                  </Badge>
                </div>
                <CardDescription>
                  {savedWatchlist
                    ? `Kayıtlı izleme listenizden ${savedWatchlist.items.length} takip edilen enstrüman.`
                    : "Henüz kayıtlı izleme listenizde hisse bulunmuyor. Piyasa özeti API'sinden öne çıkan hisseler gösteriliyor."}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {savedWatchlist ? (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Sembol</TableHead>
                        <TableHead className="text-right">Son Fiyat</TableHead>
                        <TableHead className="text-right">Değişim</TableHead>
                        <TableHead className="text-right">Hacim</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {savedWatchlist.items.slice(0, 6).map((item) => (
                        <TableRow key={item.id}>
                          <TableCell>
                            <Link
                              href={`/instruments/${encodeURIComponent(item.symbol)}`}
                              className="font-semibold text-accent hover:underline"
                            >
                              {item.symbol}
                            </Link>
                            <p className="text-xs text-muted line-clamp-1">
                              {item.name}
                            </p>
                          </TableCell>
                          <TableCell className="text-right font-medium tabular-nums">
                            {item.latestQuote
                              ? formatCurrency(
                                  item.latestQuote.price,
                                  item.latestQuote.currency
                                )
                              : "Kullanılamıyor"}
                          </TableCell>
                          <TableCell className="text-right">
                            {item.latestQuote ? (
                              <SignedPercentBadge
                                value={item.latestQuote.changePercent}
                              />
                            ) : (
                              <span className="text-xs text-muted">—</span>
                            )}
                          </TableCell>
                          <TableCell className="text-right tabular-nums text-muted">
                            {item.latestQuote
                              ? formatCompactNumber(item.latestQuote.volume)
                              : "—"}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : summary && summary.movers.length > 0 ? (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Sembol</TableHead>
                        <TableHead className="text-right">Son Fiyat</TableHead>
                        <TableHead className="text-right">Değişim</TableHead>
                        <TableHead className="text-right">Hacim</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {summary.movers.slice(0, 6).map((mover) => (
                        <TableRow key={mover.symbol}>
                          <TableCell>
                            <Link
                              href={`/instruments/${encodeURIComponent(mover.symbol)}`}
                              className="font-semibold text-accent hover:underline"
                            >
                              {mover.symbol}
                            </Link>
                            <p className="text-xs text-muted line-clamp-1">
                              {mover.name}
                            </p>
                          </TableCell>
                          <TableCell className="text-right font-medium tabular-nums">
                            {formatCurrency(mover.price, mover.currency)}
                          </TableCell>
                          <TableCell className="text-right">
                            <SignedPercentBadge value={mover.changePercent} />
                          </TableCell>
                          <TableCell className="text-right tabular-nums text-muted">
                            {formatCompactNumber(mover.volume)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : !loading ? (
                  <div className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted">
                    Şu anda {DEFAULT_EXCHANGE} için piyasa hareketi verisi bulunamadı.
                  </div>
                ) : null}
              </CardContent>
              <CardFooter>
                <span>Kaynak: {summary?.dataSource.displayName ?? "—"}</span>
                <Link
                  href="/watchlist"
                  className="inline-flex items-center gap-1 font-medium text-accent hover:underline"
                >
                  <span>
                    {savedWatchlist ? "Listeleri Yönet" : "İzleme Listesi Oluştur"}
                  </span>
                  <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                </Link>
              </CardFooter>
            </Card>
          );
        })()}

        <Card>
          <CardHeader>
            <div className="flex items-center justify-between gap-2">
              <CardTitle className="flex items-center gap-2">
                <Activity className="h-4 w-4 text-accent" aria-hidden="true" />
                <span>Son Teknik Analiz Skorları</span>
              </CardTitle>
              {screener ? (
                <Badge variant="neutral">{screener.algorithmVersion}</Badge>
              ) : null}
            </div>
            <CardDescription>
              Günlük (`1d`) barlar üzerinden {DEFAULT_EXCHANGE} için kural tabanlı teknik skorlar (0–100).
            </CardDescription>
          </CardHeader>
          <CardContent>
            {screener && screener.items.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Enstrüman</TableHead>
                    <TableHead className="text-right">Skor</TableHead>
                    <TableHead>Rejim / Trend</TableHead>
                    <TableHead className="text-right">RSI</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {screener.items.map((item) => (
                    <TableRow key={item.scoreRunId || item.symbol}>
                      <TableCell>
                        <div className="flex items-center gap-1.5">
                          <Link
                            href={`/instruments/${encodeURIComponent(item.symbol)}`}
                            className="font-semibold text-accent hover:underline"
                          >
                            {item.symbol}
                          </Link>
                          {item.staleData ? (
                            <Badge variant="warning" title="Altta yatan fiyat barları bayat olarak işaretli">
                              <AlertTriangle className="h-3 w-3" aria-hidden="true" />
                              <span>Bayat</span>
                            </Badge>
                          ) : null}
                        </div>
                        <p className="text-xs text-muted line-clamp-1">{item.headline}</p>
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
                      <TableCell className="text-right tabular-nums">
                        {formatNumber(item.latestRsi, 2)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : !loading ? (
              <div className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted">
                Henüz son analiz sonucu bulunamadı. Hisseleri tek tek inceleyin veya tarayıcı filtrelerini güncelleyin.
              </div>
            ) : null}
          </CardContent>
          <CardFooter>
            <span>
              Toplam {screener?.totalElements ?? 0} enstrümandan {screener?.items.length ?? 0} tanesi gösteriliyor
            </span>
            <Link
              href="/screener"
              className="inline-flex items-center gap-1 font-medium text-accent hover:underline"
            >
              <span>Tam Tarayıcıyı Aç</span>
              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Link>
          </CardFooter>
        </Card>
      </div>
    </div>
  );
}
