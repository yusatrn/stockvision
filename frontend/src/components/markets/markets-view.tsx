"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight, BarChart3, RefreshCw, Search } from "lucide-react";
import { useAuth } from "@/components/providers/auth-provider";
import {
  DataProvenanceBar,
  SignedPercentBadge,
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
import { Input, Select } from "@/components/ui/input";
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
  ExchangeResponse,
  InstrumentPageResponse,
  MarketSummaryResponse,
} from "@/lib/api/types";
import {
  DEFAULT_EXCHANGE,
  formatCompactNumber,
  formatCurrency,
} from "@/lib/format";
import { translateInstrumentType, translateSector } from "@/lib/labels";

export function MarketsView() {
  const { effectiveTimezone } = useAuth();
  const [exchanges, setExchanges] = useState<ExchangeResponse[]>([]);
  const [selectedExchange, setSelectedExchange] = useState(DEFAULT_EXCHANGE);
  const [searchInput, setSearchInput] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [page, setPage] = useState(0);

  const [summary, setSummary] = useState<MarketSummaryResponse | null>(null);
  const [instruments, setInstruments] = useState<InstrumentPageResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadExchanges = useCallback(async (signal?: AbortSignal) => {
    try {
      const list = await apiServices.markets.listMarkets(signal);
      setExchanges(list);
      if (list.length > 0 && !list.some((ex) => ex.code === DEFAULT_EXCHANGE)) {
        setSelectedExchange(list[0].code);
      }
    } catch {
      // Summary call handles primary error reporting cleanly.
    }
  }, []);

  const loadMarketData = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const [summaryRes, instRes] = await Promise.all([
          apiServices.markets.getSummary(selectedExchange, signal),
          apiServices.instruments.list(
            {
              exchange: selectedExchange,
              query: appliedQuery || undefined,
              page,
              size: 25,
            },
            signal
          ),
        ]);
        setSummary(summaryRes);
        setInstruments(instRes);
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") {
          return;
        }
        setErrorMessage(
          isApiClientError(err)
            ? `${err.message} (${err.code})`
            : "Piyasa dizini yüklenemedi."
        );
      } finally {
        setLoading(false);
      }
    },
    [selectedExchange, appliedQuery, page]
  );

  useEffect(() => {
    const controller = new AbortController();
    loadExchanges(controller.signal);
    return () => controller.abort();
  }, [loadExchanges]);

  useEffect(() => {
    const controller = new AbortController();
    loadMarketData(controller.signal);
    return () => controller.abort();
  }, [loadMarketData]);

  const handleSearchSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setPage(0);
    setAppliedQuery(searchInput.trim());
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight sm:text-2xl">
            Piyasalar ve Enstrümanlar
          </h1>
          <p className="text-sm text-muted">
            Desteklenen borsaları, piyasa hareketlerini ve aranabilir aktif enstrümanları inceleyin.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Select
            aria-label="Borsa filtrele"
            value={selectedExchange}
            onChange={(e) => {
              setSelectedExchange(e.target.value);
              setPage(0);
            }}
            className="w-44"
          >
            {exchanges.length > 0 ? (
              exchanges.map((ex) => (
                <option key={ex.code} value={ex.code}>
                  {ex.code} — {ex.name}
                </option>
              ))
            ) : (
              <option value={DEFAULT_EXCHANGE}>{DEFAULT_EXCHANGE}</option>
            )}
          </Select>

          <Button
            variant="outline"
            size="sm"
            onClick={() => loadMarketData()}
            disabled={loading}
          >
            <RefreshCw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} aria-hidden="true" />
            <span>Yenile</span>
          </Button>
        </div>
      </div>

      {errorMessage ? (
        <Alert variant="error" title="Piyasalar Yüklenemedi">
          {errorMessage}
        </Alert>
      ) : null}

      {summary ? (
        <DataProvenanceBar
          dataSource={summary.dataSource}
          freshness={summary.freshness}
          timezone={effectiveTimezone}
          extraLabel={`${summary.exchange.code} Seans ${summary.exchange.sessionOpenLocal}–${summary.exchange.sessionCloseLocal}`}
        />
      ) : null}

      {loading && !summary ? (
        <div className="space-y-4">
          <Skeleton className="h-40" />
          <Skeleton className="h-80" />
        </div>
      ) : null}

      {summary ? (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BarChart3 className="h-4 w-4 text-accent" aria-hidden="true" />
              <span>{summary.exchange.code} Seans Hareketleri</span>
            </CardTitle>
            <CardDescription>{summary.summaryNote}</CardDescription>
          </CardHeader>
          <CardContent>
            {summary.movers.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Sembol</TableHead>
                    <TableHead>Şirket Adı</TableHead>
                    <TableHead className="text-right">Fiyat</TableHead>
                    <TableHead className="text-right">Değişim</TableHead>
                    <TableHead className="text-right">Hacim</TableHead>
                    <TableHead className="text-right">İşlem</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {summary.movers.map((mover) => (
                    <TableRow key={mover.symbol}>
                      <TableCell className="font-semibold text-accent">
                        <Link
                          href={`/instruments/${encodeURIComponent(mover.symbol)}`}
                          className="hover:underline"
                        >
                          {mover.symbol}
                        </Link>
                      </TableCell>
                      <TableCell className="text-muted">{mover.name}</TableCell>
                      <TableCell className="text-right tabular-nums font-medium">
                        {formatCurrency(mover.price, mover.currency)}
                      </TableCell>
                      <TableCell className="text-right">
                        <SignedPercentBadge value={mover.changePercent} />
                      </TableCell>
                      <TableCell className="text-right tabular-nums text-muted">
                        {formatCompactNumber(mover.volume)}
                      </TableCell>
                      <TableCell className="text-right">
                        <Link
                          href={`/instruments/${encodeURIComponent(mover.symbol)}`}
                          className="inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline"
                        >
                          <span>Analiz Et</span>
                          <ArrowRight className="h-3 w-3" aria-hidden="true" />
                        </Link>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <p className="text-sm text-muted">
                {selectedExchange} için öne çıkan fiyat verisi bulunamadı.
              </p>
            )}
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader className="sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle>Enstrüman Dizini</CardTitle>
            <CardDescription>
              Toplam {summary?.activeInstrumentCount ?? instruments?.totalElements ?? 0} aktif{" "}
              {selectedExchange} enstrümanı arasında sembol kodu veya şirket adına göre arama yapın.
            </CardDescription>
          </div>
          <form onSubmit={handleSearchSubmit} className="mt-2 flex items-center gap-2 sm:mt-0">
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-muted"
                aria-hidden="true"
              />
              <Input
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Sembol veya şirket ara..."
                aria-label="Enstrümanlarda ara"
                className="w-56 pl-8"
              />
            </div>
            <Button type="submit" size="sm" variant="secondary">
              Filtrele
            </Button>
            {appliedQuery ? (
              <Button
                type="button"
                size="sm"
                variant="ghost"
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
        </CardHeader>
        <CardContent className="space-y-4">
          {instruments && instruments.items.length > 0 ? (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Sembol</TableHead>
                    <TableHead>Ad</TableHead>
                    <TableHead>Sektör</TableHead>
                    <TableHead>Tür</TableHead>
                    <TableHead>Para Birimi</TableHead>
                    <TableHead className="text-right">Durum</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {instruments.items.map((inst) => (
                    <TableRow key={inst.id}>
                      <TableCell>
                        <Link
                          href={`/instruments/${encodeURIComponent(inst.symbol)}`}
                          className="font-semibold text-accent hover:underline"
                        >
                          {inst.symbol}
                        </Link>
                      </TableCell>
                      <TableCell>{inst.name}</TableCell>
                      <TableCell className="text-muted">
                        {translateSector(inst.sector)}
                      </TableCell>
                      <TableCell>
                        <Badge variant="neutral">
                          {translateInstrumentType(inst.type)}
                        </Badge>
                      </TableCell>
                      <TableCell>{inst.currency}</TableCell>
                      <TableCell className="text-right">
                        <Badge variant={inst.active ? "positive" : "neutral"}>
                          {inst.active ? "Aktif" : "Pasif"}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              <div className="flex items-center justify-between text-xs text-muted">
                <span>
                  Sayfa {instruments.page + 1} / {Math.max(1, instruments.totalPages)} ·{" "}
                  {instruments.totalElements} enstrüman
                </span>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page <= 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Önceki
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page + 1 >= instruments.totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Sonraki
                  </Button>
                </div>
              </div>
            </>
          ) : !loading ? (
            <div className="rounded-md border border-dashed border-border p-8 text-center text-sm text-muted">
              {appliedQuery ? `"${appliedQuery}"` : selectedExchange} ile eşleşen enstrüman bulunamadı.
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
