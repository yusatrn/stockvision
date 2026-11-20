"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  Briefcase,
  FolderPlus,
  Plus,
  RefreshCw,
  Trash2,
  TrendingUp,
} from "lucide-react";
import { useAuth } from "@/components/providers/auth-provider";
import {
  DataProvenanceBar,
  DecisionSupportDisclaimer,
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
import { Input, Label, Select } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { SymbolSelect } from "@/components/shared/symbol-select";
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
  FeeTaxPolicy,
  PortfolioPerformanceResponse,
  PortfolioSummaryResponse,
  PortfolioTransactionResponse,
  TransactionSide,
} from "@/lib/api/types";
import {
  formatCurrency,
  formatDateTime,
  formatNumber,
  toNumeric,
} from "@/lib/format";
import {
  translateFeePolicy,
  translateTransactionSide,
  translateValuationStatus,
} from "@/lib/labels";

function renderSignedMoney(amount: number | null, currency: string) {
  if (amount === null) {
    return <span className="text-muted">Kullanılamıyor</span>;
  }
  const formatted = formatCurrency(Math.abs(amount), currency);
  const prefix = amount > 0 ? "+" : amount < 0 ? "-" : "";
  const colorClass =
    amount > 0 ? "text-positive" : amount < 0 ? "text-negative" : "text-foreground";
  return (
    <span className={`font-semibold tabular-nums ${colorClass}`}>
      {prefix}
      {formatted}
    </span>
  );
}

export function PortfolioView() {
  const { effectiveTimezone } = useAuth();

  const [portfolios, setPortfolios] = useState<PortfolioSummaryResponse[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [performance, setPerformance] = useState<PortfolioPerformanceResponse | null>(
    null
  );
  const [transactions, setTransactions] = useState<PortfolioTransactionResponse[]>(
    []
  );

  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const [newName, setNewName] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newPolicy, setNewPolicy] = useState<FeeTaxPolicy>("EXCLUDED_MVP");
  const [creatingPortfolio, setCreatingPortfolio] = useState(false);

  const [txSymbol, setTxSymbol] = useState("THYAO");
  const [txSide, setTxSide] = useState<TransactionSide>("BUY");
  const [txQuantity, setTxQuantity] = useState("10");
  const [txUnitPrice, setTxUnitPrice] = useState("100");
  const [txFeeAmount, setTxFeeAmount] = useState("0");
  const [txTaxAmount, setTxTaxAmount] = useState("0");
  const [txExecutedAt, setTxExecutedAt] = useState("");
  const [txNotes, setTxNotes] = useState("");
  const [submittingTx, setSubmittingTx] = useState(false);

  const loadPortfolios = useCallback(async (signal?: AbortSignal) => {
    setLoadingList(true);
    setErrorMessage(null);
    try {
      const list = await apiServices.portfolios.list(signal);
      setPortfolios(list);
      setSelectedId((prev) => {
        if (prev && list.some((p) => p.id === prev)) {
          return prev;
        }
        return list[0]?.id ?? null;
      });
    } catch (err) {
      if (err instanceof DOMException && err.name === "AbortError") {
        return;
      }
      setErrorMessage(
        isApiClientError(err) ? err.message : "Sanal portföyler yüklenemedi."
      );
    } finally {
      setLoadingList(false);
    }
  }, []);

  const loadSelectedPortfolio = useCallback(
    async (portfolioId: string, signal?: AbortSignal) => {
      setLoadingDetail(true);
      setErrorMessage(null);
      try {
        const [perf, txList] = await Promise.all([
          apiServices.portfolios.getPerformance(portfolioId, signal),
          apiServices.portfolios.listTransactions(portfolioId, signal),
        ]);
        setPerformance(perf);
        setTransactions(txList);
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") {
          return;
        }
        setErrorMessage(
          isApiClientError(err)
            ? err.message
            : "Portföy performansı hesaplanamadı."
        );
      } finally {
        setLoadingDetail(false);
      }
    },
    []
  );

  useEffect(() => {
    const controller = new AbortController();
    loadPortfolios(controller.signal);
    return () => controller.abort();
  }, [loadPortfolios]);

  useEffect(() => {
    if (!selectedId) {
      setPerformance(null);
      setTransactions([]);
      return;
    }
    const controller = new AbortController();
    loadSelectedPortfolio(selectedId, controller.signal);
    return () => controller.abort();
  }, [selectedId, loadSelectedPortfolio]);

  const activeSummary =
    portfolios.find((p) => p.id === selectedId) ?? portfolios[0] ?? null;

  const handleCreatePortfolio = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMessage(null);
    setActionMessage(null);
    if (!newName.trim()) {
      setErrorMessage("Portföy adı zorunludur.");
      return;
    }
    setCreatingPortfolio(true);
    try {
      const created = await apiServices.portfolios.create({
        name: newName.trim(),
        description: newDescription.trim() || undefined,
        baseCurrency: "TRY",
        feeTaxPolicy: newPolicy,
      });
      setNewName("");
      setNewDescription("");
      setSelectedId(created.id);
      setActionMessage(`"${created.name}" sanal portföyü oluşturuldu.`);
      await loadPortfolios();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "Portföy oluşturulamadı."
      );
    } finally {
      setCreatingPortfolio(false);
    }
  };

  const handleDeletePortfolio = async (id: string, name: string) => {
    setErrorMessage(null);
    setActionMessage(null);
    try {
      await apiServices.portfolios.remove(id);
      setActionMessage(`"${name}" portföyü silindi.`);
      await loadPortfolios();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "Portföy silinemedi."
      );
    }
  };

  const handleAddTransaction = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedId || !activeSummary) {
      return;
    }
    setErrorMessage(null);
    setActionMessage(null);

    const qty = toNumeric(txQuantity);
    const price = toNumeric(txUnitPrice);
    const fee =
      activeSummary.feeTaxPolicy === "EXPLICIT_PER_TXN"
        ? toNumeric(txFeeAmount) ?? 0
        : 0;
    const tax =
      activeSummary.feeTaxPolicy === "EXPLICIT_PER_TXN"
        ? toNumeric(txTaxAmount) ?? 0
        : 0;

    if (qty === null || qty <= 0 || price === null || price <= 0) {
      setErrorMessage("Miktar ve birim fiyat pozitif sayı olmalıdır.");
      return;
    }

    let executedIso: string | undefined;
    if (txExecutedAt.trim()) {
      const parsed = new Date(txExecutedAt);
      if (Number.isNaN(parsed.getTime())) {
        setErrorMessage("Geçersiz işlem tarih/saati.");
        return;
      }
      executedIso = parsed.toISOString();
    }

    setSubmittingTx(true);
    try {
      const created = await apiServices.portfolios.addTransaction(selectedId, {
        symbol: txSymbol.trim().toUpperCase(),
        side: txSide,
        quantity: qty,
        unitPrice: price,
        feeAmount: fee,
        taxAmount: tax,
        executedAt: executedIso,
        notes: txNotes.trim() || undefined,
      });
      setTxNotes("");
      setActionMessage(
        `${translateTransactionSide(created.side)} işlemi kaydedildi: ${formatNumber(created.quantity, 4)} ${created.symbol} @ ${formatCurrency(created.unitPrice, created.currency)}.`
      );
      await loadSelectedPortfolio(selectedId);
      await loadPortfolios();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "İşlem kaydedilemedi."
      );
    } finally {
      setSubmittingTx(false);
    }
  };

  const handleDeleteTransaction = async (transactionId: string) => {
    if (!selectedId) {
      return;
    }
    setErrorMessage(null);
    setActionMessage(null);
    try {
      await apiServices.portfolios.removeTransaction(selectedId, transactionId);
      setActionMessage("İşlem silindi ve portföy defteri yeniden hesaplandı.");
      await loadSelectedPortfolio(selectedId);
      await loadPortfolios();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "İşlem silinemedi."
      );
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight sm:text-2xl">
            Sanal Portföyler ve İşlem Defteri
          </h1>
          <p className="text-sm text-muted">
            Ortalama maliyet tabanını, açık pozisyonları, gerçekleşmemiş ve gerçekleşmiş kâr/zararı yalnızca BigDecimal ALIŞ/SATIŞ defter kayıtlarından türetin.
          </p>
        </div>
        {selectedId ? (
          <Button
            variant="outline"
            size="sm"
            onClick={() => loadSelectedPortfolio(selectedId)}
            disabled={loadingDetail}
          >
            <RefreshCw
              className={loadingDetail ? "h-4 w-4 animate-spin" : "h-4 w-4"}
              aria-hidden="true"
            />
            <span>Yeniden Hesapla</span>
          </Button>
        ) : null}
      </div>

      {errorMessage ? (
        <Alert variant="error" title="Portföy Hatası">
          {errorMessage}
        </Alert>
      ) : null}

      {actionMessage ? (
        <Alert variant="positive" title="Defter Güncellendi">
          {actionMessage}
        </Alert>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <FolderPlus className="h-4 w-4 text-accent" aria-hidden="true" />
            <span>Sanal Portföy Oluştur</span>
          </CardTitle>
          <CardDescription>
            Komisyon ve stopaj vergilerinin MVP kapsamında hariç mi tutulacağını (`EXCLUDED_MVP`) yoksa işlem başına açıkça mı girileceğini (`EXPLICIT_PER_TXN`) seçin.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleCreatePortfolio} className="grid gap-3 sm:grid-cols-4">
            <div className="space-y-1">
              <Label htmlFor="pf-name">Portföy Adı</Label>
              <Input
                id="pf-name"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                placeholder="ör. BIST Uzun Vade"
                maxLength={80}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="pf-desc">Açıklama (isteğe bağlı)</Label>
              <Input
                id="pf-desc"
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
                placeholder="ör. Çekirdek BIST simülasyonu"
                maxLength={255}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="pf-policy">Komisyon / Vergi Politikası</Label>
              <Select
                id="pf-policy"
                value={newPolicy}
                onChange={(e) => setNewPolicy(e.target.value as FeeTaxPolicy)}
              >
                <option value="EXCLUDED_MVP">MVP Dışı (0 Komisyon/Vergi)</option>
                <option value="EXPLICIT_PER_TXN">İşlem Başına Açık Giriş</option>
              </Select>
            </div>
            <div className="flex items-end">
              <Button type="submit" isLoading={creatingPortfolio} className="w-full">
                <Plus className="h-4 w-4" aria-hidden="true" />
                <span>Portföy Oluştur</span>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {loadingList && portfolios.length === 0 ? (
        <Skeleton className="h-64 w-full" />
      ) : portfolios.length === 0 ? (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Briefcase className="h-4 w-4 text-accent" aria-hidden="true" />
              <span>Henüz Sanal Portföy Yok</span>
            </CardTitle>
            <CardDescription>
              ALIŞ/SATIŞ işlemleri kaydetmek ve türetilmiş performansı görmek için yukarıdan ilk sanal portföyünüzü oluşturun.
            </CardDescription>
          </CardHeader>
        </Card>
      ) : activeSummary ? (
        <div className="space-y-6">
          <div
            role="tablist"
            aria-label="Portföyleriniz"
            className="flex flex-wrap items-center gap-2"
          >
            {portfolios.map((pf) => {
              const selected = pf.id === activeSummary.id;
              return (
                <button
                  key={pf.id}
                  type="button"
                  role="tab"
                  aria-selected={selected}
                  onClick={() => setSelectedId(pf.id)}
                  className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm font-medium transition-colors ${
                    selected
                      ? "border-accent bg-accent/15 text-accent"
                      : "border-border bg-surface text-muted hover:text-foreground"
                  }`}
                >
                  <span>{pf.name}</span>
                  <Badge variant="neutral">{pf.openPositionsCount} açık</Badge>
                </button>
              );
            })}
          </div>

          {performance ? (
            <>
              <DataProvenanceBar
                dataSource={performance.dataSource}
                freshness={performance.freshness}
                timezone={effectiveTimezone}
                calculatedAt={performance.calculatedAt}
                extraLabel={`Değerleme: ${translateValuationStatus(performance.valuationStatus)} · Politika: ${translateFeePolicy(performance.feeTaxPolicy)}`}
              />

              <Card>
                <CardHeader>
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <CardTitle className="flex items-center gap-2">
                        <TrendingUp className="h-4 w-4 text-accent" aria-hidden="true" />
                        <span>{performance.name} Performans Özeti</span>
                      </CardTitle>
                      <CardDescription>
                        {performance.feeTaxPolicyNote}
                      </CardDescription>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge
                        variant={
                          performance.valuationStatus === "READY"
                            ? "positive"
                            : performance.valuationStatus === "EMPTY"
                            ? "neutral"
                            : "warning"
                        }
                      >
                        {translateValuationStatus(performance.valuationStatus)}
                      </Badge>
                      <Button
                        variant="danger"
                        size="sm"
                        onClick={() =>
                          handleDeletePortfolio(performance.portfolioId, performance.name)
                        }
                      >
                        <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                        <span>Portföyü Sil</span>
                      </Button>
                    </div>
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
                    <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                      <p className="text-xs text-muted">Açık Maliyet Tabanı</p>
                      <p className="mt-1 text-base font-bold tabular-nums">
                        {formatCurrency(
                          performance.totalCostBasis,
                          performance.baseCurrency
                        )}
                      </p>
                    </div>

                    <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                      <p className="text-xs text-muted">Açık Piyasa Değeri</p>
                      <p className="mt-1 text-base font-bold tabular-nums">
                        {performance.totalMarketValue !== null
                          ? formatCurrency(
                              performance.totalMarketValue,
                              performance.baseCurrency
                            )
                          : "Kullanılamıyor"}
                      </p>
                    </div>

                    <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                      <p className="text-xs text-muted">Gerçekleşmemiş K/Z (Açık)</p>
                      <div className="mt-1 flex flex-wrap items-center gap-1.5 text-base">
                        {renderSignedMoney(
                          performance.totalUnrealizedPnl,
                          performance.baseCurrency
                        )}
                        {performance.totalUnrealizedPnlPercent !== null ? (
                          <SignedPercentBadge
                            value={performance.totalUnrealizedPnlPercent}
                          />
                        ) : null}
                      </div>
                    </div>

                    <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                      <p className="text-xs text-muted">Gerçekleşmiş K/Z (Kapalı İşlemler)</p>
                      <p className="mt-1 text-base">
                        {renderSignedMoney(
                          performance.totalRealizedPnl,
                          performance.baseCurrency
                        )}
                      </p>
                    </div>

                    <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                      <p className="text-xs text-muted">Net K/Z ve Komisyon / Vergi</p>
                      <div className="mt-1 text-base">
                        {renderSignedMoney(
                          performance.totalNetPnl,
                          performance.baseCurrency
                        )}
                      </div>
                      <p className="mt-0.5 text-[11px] text-muted tabular-nums">
                        Komisyon: {formatCurrency(performance.totalFees, performance.baseCurrency)} · Vergi:{" "}
                        {formatCurrency(performance.totalTaxes, performance.baseCurrency)}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>
                    Türetilmiş Açık Pozisyonlar ({performance.openPositionsCount})
                  </CardTitle>
                  <CardDescription>
                    Kronolojik işlem defterinden hesaplanan hareketli ağırlıklı ortalama maliyet.
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  {performance.holdings.length > 0 ? (
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Sembol</TableHead>
                          <TableHead className="text-right">Açık Adet</TableHead>
                          <TableHead className="text-right">Ort. Maliyet</TableHead>
                          <TableHead className="text-right">Son Fiyat</TableHead>
                          <TableHead className="text-right">Maliyet Tabanı</TableHead>
                          <TableHead className="text-right">Piyasa Değeri</TableHead>
                          <TableHead className="text-right">Gerçekleşmemiş K/Z</TableHead>
                          <TableHead className="text-right">Gerçekleşmiş K/Z</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {performance.holdings.map((h) => (
                          <TableRow key={h.instrumentId}>
                            <TableCell>
                              <Link
                                href={`/instruments/${encodeURIComponent(h.symbol)}`}
                                className="font-semibold text-accent hover:underline"
                              >
                                {h.symbol}
                              </Link>
                              <p className="text-xs text-muted line-clamp-1">{h.name}</p>
                            </TableCell>
                            <TableCell className="text-right tabular-nums">
                              {formatNumber(h.openQuantity, 4)}
                            </TableCell>
                            <TableCell className="text-right tabular-nums">
                              {formatCurrency(h.averageCost, h.currency)}
                            </TableCell>
                            <TableCell className="text-right tabular-nums">
                              {h.latestPrice !== null
                                ? formatCurrency(h.latestPrice, h.currency)
                                : "Kullanılamıyor"}
                            </TableCell>
                            <TableCell className="text-right tabular-nums">
                              {formatCurrency(h.costBasis, h.currency)}
                            </TableCell>
                            <TableCell className="text-right font-medium tabular-nums">
                              {h.marketValue !== null
                                ? formatCurrency(h.marketValue, h.currency)
                                : "—"}
                            </TableCell>
                            <TableCell className="text-right">
                              <div className="inline-flex items-center gap-1.5">
                                {renderSignedMoney(h.unrealizedPnl, h.currency)}
                                {h.unrealizedPnlPercent !== null ? (
                                  <SignedPercentBadge value={h.unrealizedPnlPercent} />
                                ) : null}
                              </div>
                            </TableCell>
                            <TableCell className="text-right">
                              {renderSignedMoney(h.realizedPnl, h.currency)}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  ) : (
                    <div className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted">
                      {performance.transactionCount > 0
                        ? "Tüm pozisyonlar şu anda kapalı. Yukarıdaki toplam gerçekleşmiş K/Z kapalı işlemleri yansıtır."
                        : "Bu portföyde henüz işlem kaydı yok."}
                    </div>
                  )}
                </CardContent>
              </Card>
            </>
          ) : null}

          <Card>
            <CardHeader>
              <CardTitle>ALIŞ / SATIŞ İşlemi Kaydet</CardTitle>
              <CardDescription>
                İşlemler kronolojik olarak doğrulanır; eldeki açık adetten fazla satış (açığa satış) engellenir.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleAddTransaction} className="grid gap-3 sm:grid-cols-4">
                <div className="space-y-1">
                  <Label htmlFor="tx-symbol">Sembol</Label>
                  <SymbolSelect
                    id="tx-symbol"
                    value={txSymbol}
                    onChange={setTxSymbol}
                    pinnedSymbols={
                      performance?.holdings
                        .filter((h) => h.openQuantity > 0)
                        .map((h) => h.symbol) ?? []
                    }
                  />
                </div>

                <div className="space-y-1">
                  <Label htmlFor="tx-side">İşlem Yönü</Label>
                  <Select
                    id="tx-side"
                    value={txSide}
                    onChange={(e) => setTxSide(e.target.value as TransactionSide)}
                  >
                    <option value="BUY">ALIŞ (BUY)</option>
                    <option value="SELL">SATIŞ (SELL)</option>
                  </Select>
                </div>

                <div className="space-y-1">
                  <Label htmlFor="tx-qty">Adet / Miktar</Label>
                  <Input
                    id="tx-qty"
                    type="number"
                    step="0.000001"
                    min="0.000001"
                    value={txQuantity}
                    onChange={(e) => setTxQuantity(e.target.value)}
                  />
                </div>

                <div className="space-y-1">
                  <Label htmlFor="tx-price">Birim Fiyat ({activeSummary.baseCurrency})</Label>
                  <Input
                    id="tx-price"
                    type="number"
                    step="0.0001"
                    min="0.0001"
                    value={txUnitPrice}
                    onChange={(e) => setTxUnitPrice(e.target.value)}
                  />
                </div>

                <div className="space-y-1">
                  <Label htmlFor="tx-fee">
                    Komisyon ({activeSummary.feeTaxPolicy === "EXCLUDED_MVP" ? "Hariç" : activeSummary.baseCurrency})
                  </Label>
                  <Input
                    id="tx-fee"
                    type="number"
                    step="0.01"
                    min="0"
                    disabled={activeSummary.feeTaxPolicy === "EXCLUDED_MVP"}
                    value={activeSummary.feeTaxPolicy === "EXCLUDED_MVP" ? "0" : txFeeAmount}
                    onChange={(e) => setTxFeeAmount(e.target.value)}
                  />
                </div>

                <div className="space-y-1">
                  <Label htmlFor="tx-tax">
                    Vergi ({activeSummary.feeTaxPolicy === "EXCLUDED_MVP" ? "Hariç" : activeSummary.baseCurrency})
                  </Label>
                  <Input
                    id="tx-tax"
                    type="number"
                    step="0.01"
                    min="0"
                    disabled={activeSummary.feeTaxPolicy === "EXCLUDED_MVP"}
                    value={activeSummary.feeTaxPolicy === "EXCLUDED_MVP" ? "0" : txTaxAmount}
                    onChange={(e) => setTxTaxAmount(e.target.value)}
                  />
                </div>

                <div className="space-y-1">
                  <Label htmlFor="tx-executed-at">İşlem Tarihi (isteğe bağlı)</Label>
                  <Input
                    id="tx-executed-at"
                    type="datetime-local"
                    value={txExecutedAt}
                    onChange={(e) => setTxExecutedAt(e.target.value)}
                  />
                </div>

                <div className="space-y-1">
                  <Label htmlFor="tx-notes">Not (isteğe bağlı)</Label>
                  <Input
                    id="tx-notes"
                    value={txNotes}
                    onChange={(e) => setTxNotes(e.target.value)}
                    placeholder="İşlem gerekçesi"
                  />
                </div>

                <div className="sm:col-span-4 flex justify-end">
                  <Button type="submit" isLoading={submittingTx}>
                    <Plus className="h-4 w-4" aria-hidden="true" />
                    <span>İşlem Ekle</span>
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>İşlem Defteri ({transactions.length})</CardTitle>
              <CardDescription>
                BigDecimal miktar/fiyatlar ve UTC zaman damgaları ile tutulan kronolojik defter.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {transactions.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Tarih / Saat</TableHead>
                      <TableHead>Sembol</TableHead>
                      <TableHead>Yön</TableHead>
                      <TableHead className="text-right">Adet</TableHead>
                      <TableHead className="text-right">Birim Fiyat</TableHead>
                      <TableHead className="text-right">Brüt Tutar</TableHead>
                      <TableHead className="text-right">Komisyon / Vergi</TableHead>
                      <TableHead>Not</TableHead>
                      <TableHead className="text-right">İşlem</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {transactions.map((tx) => (
                      <TableRow key={tx.id}>
                        <TableCell className="whitespace-nowrap text-xs text-muted tabular-nums">
                          {formatDateTime(tx.executedAt, effectiveTimezone)}
                        </TableCell>
                        <TableCell className="font-semibold">
                          <Link
                            href={`/instruments/${encodeURIComponent(tx.symbol)}`}
                            className="text-accent hover:underline"
                          >
                            {tx.symbol}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <Badge variant={tx.side === "BUY" ? "positive" : "negative"}>
                            {translateTransactionSide(tx.side)}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatNumber(tx.quantity, 4)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatCurrency(tx.unitPrice, tx.currency)}
                        </TableCell>
                        <TableCell className="text-right font-medium tabular-nums">
                          {formatCurrency(tx.grossAmount, tx.currency)}
                        </TableCell>
                        <TableCell className="text-right text-xs text-muted tabular-nums">
                          {formatCurrency(tx.feeAmount + tx.taxAmount, tx.currency)}
                        </TableCell>
                        <TableCell className="text-xs text-muted">
                          {tx.notes || "—"}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`${tx.symbol} ${translateTransactionSide(tx.side)} işlemini sil`}
                            onClick={() => handleDeleteTransaction(tx.id)}
                          >
                            <Trash2 className="h-3.5 w-3.5 text-negative" aria-hidden="true" />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <div className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted">
                  Henüz işlem defteri kaydı yok. Yukarıdan ilk ALIŞ işleminizi ekleyin.
                </div>
              )}
            </CardContent>
          </Card>

          <DecisionSupportDisclaimer disclaimer={performance?.disclaimer} />
        </div>
      ) : null}
    </div>
  );
}
