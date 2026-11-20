"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  Bell,
  BellRing,
  Check,
  CheckCheck,
  Pause,
  Play,
  Plus,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { useAuth } from "@/components/providers/auth-provider";
import {
  DataProvenanceBar,
  DecisionSupportDisclaimer,
  FreshnessBadge,
  ProviderModeBadge,
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
  AlertConditionType,
  AlertEvaluationRunResponse,
  AlertRuleListResponse,
  AlertRuleResponse,
  IndicatorType,
  NotificationPageResponse,
} from "@/lib/api/types";
import { formatDateTime, formatNumber, toNumeric } from "@/lib/format";
import {
  translateAlertStatus,
  translateIndicator,
  translateIndicatorField,
  translateTriggerState,
} from "@/lib/labels";

const INDICATOR_FIELD_OPTIONS: Record<IndicatorType, string[]> = {
  SMA: ["value"],
  EMA: ["value"],
  RSI: ["value"],
  ATR: ["value"],
  MACD: ["macd", "signal", "histogram"],
  BOLLINGER: ["upper", "middle", "lower", "bandwidth"],
  STOCHASTIC: ["k", "d"],
  VOLUME_AVG: ["average", "ratio"],
};

export function AlertsView({ initialSymbol }: { initialSymbol?: string }) {
  const { effectiveTimezone } = useAuth();

  const [rulesData, setRulesData] = useState<AlertRuleListResponse | null>(null);
  const [notificationsData, setNotificationsData] =
    useState<NotificationPageResponse | null>(null);
  const [unreadOnly, setUnreadOnly] = useState(false);

  const [loading, setLoading] = useState(true);
  const [evaluating, setEvaluating] = useState(false);
  const [creating, setCreating] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [lastEvalSummary, setLastEvalSummary] =
    useState<AlertEvaluationRunResponse | null>(null);

  const [symbol, setSymbol] = useState(initialSymbol || "THYAO");
  const [conditionType, setConditionType] =
    useState<AlertConditionType>("PRICE_ABOVE");
  const [indicatorType, setIndicatorType] = useState<IndicatorType>("RSI");
  const [indicatorField, setIndicatorField] = useState("value");
  const [interval, setInterval] = useState<"1d" | "1h" | "15m">("1d");
  const [thresholdValue, setThresholdValue] = useState("");
  const [cooldownMinutes, setCooldownMinutes] = useState("60");
  const [note, setNote] = useState("");

  useEffect(() => {
    if (initialSymbol) {
      setSymbol(initialSymbol.toUpperCase());
    }
  }, [initialSymbol]);

  const loadAll = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const [rulesRes, notifRes] = await Promise.all([
          apiServices.alerts.list(signal),
          apiServices.notifications.list({ unreadOnly, page: 0, size: 20 }, signal),
        ]);
        setRulesData(rulesRes);
        setNotificationsData(notifRes);
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") {
          return;
        }
        setErrorMessage(
          isApiClientError(err) ? err.message : "Alarmlar ve bildirimler yüklenemedi."
        );
      } finally {
        setLoading(false);
      }
    },
    [unreadOnly]
  );

  useEffect(() => {
    const controller = new AbortController();
    loadAll(controller.signal);
    return () => controller.abort();
  }, [loadAll]);

  const isIndicator =
    conditionType === "INDICATOR_ABOVE" || conditionType === "INDICATOR_BELOW";
  const allowedFields = INDICATOR_FIELD_OPTIONS[indicatorType];

  const handleIndicatorChange = (next: IndicatorType) => {
    setIndicatorType(next);
    const fields = INDICATOR_FIELD_OPTIONS[next];
    if (!fields.includes(indicatorField)) {
      setIndicatorField(fields[0]);
    }
  };

  const handleCreateAlert = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMessage(null);
    setActionMessage(null);

    const parsedThreshold = toNumeric(thresholdValue);
    const parsedCooldown = toNumeric(cooldownMinutes) ?? 60;
    const normalizedSymbol = symbol.trim().toUpperCase();

    if (!normalizedSymbol || parsedThreshold === null) {
      setErrorMessage("Hisse sembolü ve sayısal eşik değeri zorunludur.");
      return;
    }

    setCreating(true);
    try {
      const created = await apiServices.alerts.create({
        symbol: normalizedSymbol,
        conditionType,
        indicatorType: isIndicator ? indicatorType : undefined,
        indicatorField: isIndicator ? indicatorField : undefined,
        interval,
        thresholdValue: parsedThreshold,
        cooldownMinutes: Math.max(1, Math.round(parsedCooldown)),
        note: note.trim() || undefined,
      });
      setThresholdValue("");
      setNote("");
      setActionMessage(`${created.symbol} için alarm kuralı oluşturuldu.`);
      await loadAll();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "Alarm kuralı oluşturulamadı."
      );
    } finally {
      setCreating(false);
    }
  };

  const handleTogglePause = async (rule: AlertRuleResponse) => {
    setErrorMessage(null);
    setActionMessage(null);
    const nextStatus = rule.status === "ACTIVE" ? "PAUSED" : "ACTIVE";
    try {
      await apiServices.alerts.update(rule.id, { status: nextStatus });
      setActionMessage(
        `${rule.symbol} alarm durumu ${translateAlertStatus(nextStatus)} olarak güncellendi.`
      );
      await loadAll();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "Alarm kuralı güncellenemedi."
      );
    }
  };

  const handleDeleteRule = async (rule: AlertRuleResponse) => {
    setErrorMessage(null);
    setActionMessage(null);
    try {
      await apiServices.alerts.remove(rule.id);
      setActionMessage(`${rule.symbol} alarm kuralı silindi.`);
      await loadAll();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "Alarm kuralı silinemedi."
      );
    }
  };

  const handleEvaluateNow = async () => {
    setErrorMessage(null);
    setActionMessage(null);
    setEvaluating(true);
    try {
      const run = await apiServices.alerts.evaluateNow();
      setLastEvalSummary(run);
      await loadAll();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "Alarm değerlendirme koşusu başarısız oldu."
      );
    } finally {
      setEvaluating(false);
    }
  };

  const handleMarkRead = async (id: string) => {
    setErrorMessage(null);
    try {
      await apiServices.notifications.markRead(id);
      await loadAll();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "Bildirim okundu olarak işaretlenemedi."
      );
    }
  };

  const handleMarkAllRead = async () => {
    setErrorMessage(null);
    try {
      const res = await apiServices.notifications.markAllRead();
      setActionMessage(`${res.updatedCount} bildirim okundu olarak işaretlendi.`);
      await loadAll();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "Bildirimler okundu olarak işaretlenemedi."
      );
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight sm:text-2xl">
            Fiyat ve İndikatör Alarmları
          </h1>
          <p className="text-sm text-muted">
            Bekleme süresi (cooldown) koruması ve tam veri tazeliği bildirimi ile fiyat veya indikatör eşik alarmları tanımlayın.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => loadAll()}
            disabled={loading}
          >
            <RefreshCw
              className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"}
              aria-hidden="true"
            />
            <span>Yenile</span>
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={handleEvaluateNow}
            isLoading={evaluating}
          >
            <BellRing className="h-4 w-4" aria-hidden="true" />
            <span>Aktif Alarmları Şimdi Değerlendir</span>
          </Button>
        </div>
      </div>

      {errorMessage ? (
        <Alert variant="error" title="Alarm Hatası">
          {errorMessage}
        </Alert>
      ) : null}

      {actionMessage ? (
        <Alert variant="positive" title="İşlem Başarılı">
          {actionMessage}
        </Alert>
      ) : null}

      {lastEvalSummary ? (
        <Alert variant="info" title="Değerlendirme Özeti">
          {lastEvalSummary.evaluatedCount} aktif kural değerlendirildi:{" "}
          <strong>{lastEvalSummary.triggeredCount}</strong> tetiklendi,{" "}
          <strong>{lastEvalSummary.suppressedCount}</strong> kural bekleme süresi/idempotency nedeniyle bastırıldı ({" "}
          {formatDateTime(lastEvalSummary.evaluatedAt, effectiveTimezone)} · Kaynak:{" "}
          {lastEvalSummary.dataSource.displayName}).
        </Alert>
      ) : null}

      {rulesData ? (
        <DataProvenanceBar
          dataSource={rulesData.dataSource}
          freshness={rulesData.freshness}
          timezone={effectiveTimezone}
          extraLabel={`${rulesData.alerts.length} kural`}
        />
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Bell className="h-4 w-4 text-accent" aria-hidden="true" />
            <span>Alarm Kuralı Oluştur</span>
          </CardTitle>
          <CardDescription>
            Bildirimler değerlendirme anındaki veri sağlayıcısını, simülasyon/gecikme durumunu ve veri tazeliğini açıkça belirtir.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleCreateAlert} className="grid gap-3 sm:grid-cols-4">
            <div className="space-y-1">
              <Label htmlFor="al-symbol">Hisse Sembolü</Label>
              <SymbolSelect id="al-symbol" value={symbol} onChange={setSymbol} />
            </div>

            <div className="space-y-1">
              <Label htmlFor="al-condition">Koşul Türü</Label>
              <Select
                id="al-condition"
                value={conditionType}
                onChange={(e) =>
                  setConditionType(e.target.value as AlertConditionType)
                }
              >
                <option value="PRICE_ABOVE">Fiyat Eşit veya Üstünde</option>
                <option value="PRICE_BELOW">Fiyat Eşit veya Altında</option>
                <option value="INDICATOR_ABOVE">İndikatör Eşit veya Üstünde</option>
                <option value="INDICATOR_BELOW">İndikatör Eşit veya Altında</option>
              </Select>
            </div>

            <div className="space-y-1">
              <Label htmlFor="al-threshold">Eşik Değeri</Label>
              <Input
                id="al-threshold"
                type="number"
                step="0.0001"
                value={thresholdValue}
                onChange={(e) => setThresholdValue(e.target.value)}
                placeholder={isIndicator ? "ör. 70" : "ör. 320.00"}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="al-cooldown">Bekleme Süresi (dakika)</Label>
              <Input
                id="al-cooldown"
                type="number"
                min={1}
                max={43200}
                value={cooldownMinutes}
                onChange={(e) => setCooldownMinutes(e.target.value)}
              />
            </div>

            {isIndicator ? (
              <>
                <div className="space-y-1">
                  <Label htmlFor="al-indicator">İndikatör</Label>
                  <Select
                    id="al-indicator"
                    value={indicatorType}
                    onChange={(e) =>
                      handleIndicatorChange(e.target.value as IndicatorType)
                    }
                  >
                    <option value="RSI">RSI</option>
                    <option value="SMA">SMA</option>
                    <option value="EMA">EMA</option>
                    <option value="MACD">MACD</option>
                    <option value="BOLLINGER">Bollinger Bantları</option>
                    <option value="STOCHASTIC">Stokastik</option>
                    <option value="ATR">ATR</option>
                    <option value="VOLUME_AVG">Hacim Ort.</option>
                  </Select>
                </div>

                <div className="space-y-1">
                  <Label htmlFor="al-indicator-field">İndikatör Alanı</Label>
                  <Select
                    id="al-indicator-field"
                    value={indicatorField}
                    onChange={(e) => setIndicatorField(e.target.value)}
                  >
                    {allowedFields.map((field) => (
                      <option key={field} value={field}>
                        {translateIndicatorField(field)}
                      </option>
                    ))}
                  </Select>
                </div>

                <div className="space-y-1">
                  <Label htmlFor="al-interval">Bar Aralığı</Label>
                  <Select
                    id="al-interval"
                    value={interval}
                    onChange={(e) =>
                      setInterval(e.target.value as "1d" | "1h" | "15m")
                    }
                  >
                    <option value="1d">Günlük (1d)</option>
                    <option value="1h">Saatlik (1h)</option>
                    <option value="15m">15 Dakika (15m)</option>
                  </Select>
                </div>
              </>
            ) : null}

            <div className={isIndicator ? "space-y-1" : "space-y-1 sm:col-span-3"}>
              <Label htmlFor="al-note">Not (isteğe bağlı)</Label>
              <Input
                id="al-note"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="ör. Aşırı alım kontrolü"
                maxLength={255}
              />
            </div>

            <div className="flex items-end justify-end">
              <Button type="submit" isLoading={creating} className="w-full sm:w-auto">
                <Plus className="h-4 w-4" aria-hidden="true" />
                <span>Alarm Oluştur</span>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {loading && !rulesData ? (
        <Skeleton className="h-64 w-full" />
      ) : (
        <div className="grid gap-6 xl:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Alarm Kuralları ({rulesData?.alerts.length ?? 0})</CardTitle>
              <CardDescription>
                Tetiklenen kurallar bekleme süresi dolana veya eşik koşulu sıfırlanana kadar bastırılır.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {rulesData && rulesData.alerts.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Sembol ve Koşul</TableHead>
                      <TableHead>Durum</TableHead>
                      <TableHead className="text-right">Son Gözlenen</TableHead>
                      <TableHead className="text-right">İşlemler</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rulesData.alerts.map((rule) => (
                      <TableRow key={rule.id}>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <Link
                              href={`/instruments/${encodeURIComponent(rule.symbol)}`}
                              className="font-semibold text-accent hover:underline"
                            >
                              {rule.symbol}
                            </Link>
                            <span className="text-xs font-mono text-foreground">
                              {rule.conditionType === "INDICATOR_ABOVE" ||
                              rule.conditionType === "INDICATOR_BELOW"
                                ? `${rule.indicatorType ? translateIndicator(rule.indicatorType) : ""}.${rule.indicatorField ? translateIndicatorField(rule.indicatorField) : ""} (${rule.interval})`
                                : "FİYAT"}{" "}
                              {rule.conditionType.endsWith("ABOVE") ? ">=" : "<="}{" "}
                              {formatNumber(rule.thresholdValue, 2)}
                            </span>
                          </div>
                          <p className="text-xs text-muted">
                            Bekleme: {rule.cooldownMinutes} dk
                            {rule.note ? ` · ${rule.note}` : ""}
                          </p>
                        </TableCell>
                        <TableCell>
                          <div className="flex flex-wrap gap-1">
                            <Badge
                              variant={rule.status === "ACTIVE" ? "positive" : "neutral"}
                            >
                              {translateAlertStatus(rule.status)}
                            </Badge>
                            <Badge
                              variant={
                                rule.triggerState === "TRIGGERED" ? "warning" : "info"
                              }
                            >
                              {translateTriggerState(rule.triggerState)}
                            </Badge>
                          </div>
                        </TableCell>
                        <TableCell className="text-right text-xs tabular-nums">
                          <div className="font-medium">
                            {formatNumber(rule.lastObservedValue, 2)}
                          </div>
                          <div className="text-muted">
                            {formatDateTime(rule.lastEvaluatedAt, effectiveTimezone)}
                          </div>
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="inline-flex items-center gap-1">
                            <Button
                              variant="ghost"
                              size="icon"
                              aria-label={
                                rule.status === "ACTIVE"
                                  ? `${rule.symbol} alarmını duraklat`
                                  : `${rule.symbol} alarmını devam ettir`
                              }
                              onClick={() => handleTogglePause(rule)}
                            >
                              {rule.status === "ACTIVE" ? (
                                <Pause className="h-3.5 w-3.5" aria-hidden="true" />
                              ) : (
                                <Play className="h-3.5 w-3.5 text-positive" aria-hidden="true" />
                              )}
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              aria-label={`${rule.symbol} alarmını sil`}
                              onClick={() => handleDeleteRule(rule)}
                            >
                              <Trash2 className="h-3.5 w-3.5 text-negative" aria-hidden="true" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <div className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted">
                  Henüz yapılandırılmış alarm kuralı yok. Yukarıdan bir fiyat veya indikatör eşiği oluşturun.
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <CardTitle className="flex items-center gap-2">
                    <span>Bildirimler</span>
                    <Badge
                      variant={
                        (notificationsData?.unreadCount ?? 0) > 0
                          ? "warning"
                          : "neutral"
                      }
                    >
                      {notificationsData?.unreadCount ?? 0} okunmamış
                    </Badge>
                  </CardTitle>
                  <CardDescription>
                    Tetiklenme anındaki veri kaynağını ve tazeliğini gösteren idempotent alarm olayları.
                  </CardDescription>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <label className="flex items-center gap-1.5 text-xs cursor-pointer">
                    <input
                      type="checkbox"
                      checked={unreadOnly}
                      onChange={(e) => setUnreadOnly(e.target.checked)}
                      className="h-3.5 w-3.5 rounded accent-accent"
                    />
                    <span>Yalnızca okunmamışlar</span>
                  </label>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleMarkAllRead}
                    disabled={(notificationsData?.unreadCount ?? 0) === 0}
                  >
                    <CheckCheck className="h-3.5 w-3.5" aria-hidden="true" />
                    <span>Tümünü Okundu İşaretle</span>
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              {notificationsData && notificationsData.items.length > 0 ? (
                notificationsData.items.map((n) => (
                  <div
                    key={n.id}
                    className={`rounded-md border p-3 space-y-2 ${
                      n.read
                        ? "border-border bg-surface-muted/20"
                        : "border-accent/50 bg-accent/5"
                    }`}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <Link
                          href={`/instruments/${encodeURIComponent(n.symbol)}`}
                          className="font-semibold text-sm text-accent hover:underline"
                        >
                          {n.symbol}
                        </Link>
                        <ProviderModeBadge
                          mode={n.providerMode}
                          simulated={n.simulated}
                        />
                        <FreshnessBadge
                          freshness={{
                            status: n.freshnessStatus,
                            stale: n.staleData,
                            simulated: n.simulated,
                            latestDataTimestamp: n.dataTimestamp,
                            lastSyncedAt: null,
                            lastSyncStatus: null,
                            ageMinutes: null,
                            note: n.providerDisplayName,
                          }}
                        />
                      </div>

                      {!n.read ? (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleMarkRead(n.id)}
                        >
                          <Check className="h-3.5 w-3.5" aria-hidden="true" />
                          <span>Okundu İşaretle</span>
                        </Button>
                      ) : (
                        <Badge variant="neutral">Okundu</Badge>
                      )}
                    </div>

                    <p className="text-xs leading-relaxed text-foreground/90">
                      {n.message}
                    </p>

                    <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-muted tabular-nums">
                      <span>
                        Veri zamanı: {formatDateTime(n.dataTimestamp, effectiveTimezone)}
                      </span>
                      <span>
                        Tetiklenme: {formatDateTime(n.createdAt, effectiveTimezone)}
                      </span>
                      <span>Sağlayıcı: {n.providerDisplayName}</span>
                    </div>
                  </div>
                ))
              ) : (
                <div className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted">
                  {unreadOnly
                    ? "Okunmamış alarm bildirimi bulunmuyor."
                    : "Henüz tetiklenen bildirim yok."}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      <DecisionSupportDisclaimer />
    </div>
  );
}
