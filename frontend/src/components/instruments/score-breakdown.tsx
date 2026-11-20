"use client";

import React from "react";
import { AlertTriangle, CheckCircle2, Info } from "lucide-react";
import {
  DecisionSupportDisclaimer,
  RegimeBadge,
  ScorePill,
  TrendBadge,
} from "@/components/shared/data-status-badges";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import type { InstrumentAnalysisResponse } from "@/lib/api/types";
import { formatDateTime, formatNumber } from "@/lib/format";
import { translateIndicatorField, translateScoreComponent } from "@/lib/labels";

export function ScoreBreakdown({
  analysis,
  timezone,
}: {
  analysis: InstrumentAnalysisResponse;
  timezone: string;
}) {
  const isInsufficient = analysis.status === "INSUFFICIENT_DATA";

  return (
    <div className="space-y-4">
      {isInsufficient ? (
        <Alert variant="warning" title="Analiz Durumu: Yetersiz Veri (INSUFFICIENT_DATA)">
          <p>
            Gerekli bar geçmişi veya indikatör ısınma verileri eksik ya da geçersiz olduğu için bileşik skor üretimi durduruldu.
          </p>
          {analysis.insufficientDataReasons.length > 0 ? (
            <ul className="mt-2 list-disc pl-5 space-y-0.5 text-xs">
              {analysis.insufficientDataReasons.map((reason) => (
                <li key={reason}>{reason}</li>
              ))}
            </ul>
          ) : null}
        </Alert>
      ) : null}

      {analysis.staleData ? (
        <Alert variant="warning" title="Bayat Piyasa Verisi Uyarısı">
          Altta yatan OHLCV fiyat barları bayat olarak işaretlendi. Bu analize güvenmeden önce son bar zamanını ve senkronizasyon durumunu kontrol edin.
        </Alert>
      ) : null}

      <Card>
        <CardHeader>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle>Açıklanabilir Teknik Skor · {analysis.symbol}</CardTitle>
              <CardDescription>
                {analysis.algorithmVersion} · {analysis.indicatorEngineVersion} ·{" "}
                {analysis.barCount} {analysis.interval} barı · Hesaplama:{" "}
                {formatDateTime(analysis.calculatedAt, timezone)}
              </CardDescription>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <ScorePill score={analysis.finalScore} status={analysis.status} />
              <RegimeBadge regime={analysis.regime} status={analysis.status} />
              <TrendBadge trend={analysis.trendDirection} />
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="rounded-md border border-border bg-surface-muted/40 p-4 space-y-2">
            <h3 className="text-sm font-semibold text-foreground">
              {analysis.explanation.headline}
            </h3>
            <p className="text-xs leading-relaxed text-muted">
              {analysis.explanation.methodologySummary}
            </p>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <h3 className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted">
                <CheckCircle2 className="h-3.5 w-3.5 text-positive" aria-hidden="true" />
                <span>Temel Etkenler</span>
              </h3>
              {analysis.explanation.keyDrivers.length > 0 ? (
                <ul className="space-y-1.5 text-xs leading-relaxed text-foreground/90">
                  {analysis.explanation.keyDrivers.map((driver) => (
                    <li
                      key={driver}
                      className="rounded border border-border/70 bg-surface-muted/30 px-2.5 py-1.5"
                    >
                      {driver}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-xs text-muted">Bildirilen aktif etken bulunmuyor.</p>
              )}
            </div>

            <div className="space-y-2">
              <h3 className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted">
                <Info className="h-3.5 w-3.5 text-warning" aria-hidden="true" />
                <span>Risk ve Veri Notları</span>
              </h3>
              {analysis.explanation.riskAndDataNotes.length > 0 ? (
                <ul className="space-y-1.5 text-xs leading-relaxed text-foreground/90">
                  {analysis.explanation.riskAndDataNotes.map((note) => (
                    <li
                      key={note}
                      className="rounded border border-border/70 bg-surface-muted/30 px-2.5 py-1.5"
                    >
                      {note}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-xs text-muted">Ek risk uyarısı yok.</p>
              )}
            </div>
          </div>

          <div className="space-y-3">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">
              Ağırlıklı Bileşen Kırılımı (Hazır Ağırlıklar Toplamı = %100)
            </h3>
            <div className="grid gap-3 md:grid-cols-2">
              {analysis.components.map((component) => {
                const scoreVal = component.normalizedScore;
                const progressWidth =
                  scoreVal !== null ? Math.min(100, Math.max(0, scoreVal)) : 0;
                const metricEntries = Object.entries(component.metrics || {});

                return (
                  <div
                    key={component.type}
                    className="rounded-md border border-border bg-surface-muted/30 p-3.5 space-y-2.5"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold tracking-wide">
                          {translateScoreComponent(component.type)}
                        </span>
                        <Badge variant="neutral">Ağırlık %{component.weightPercent}</Badge>
                      </div>
                      {component.status === "INSUFFICIENT_DATA" ? (
                        <Badge variant="warning">
                          <AlertTriangle className="h-3 w-3" aria-hidden="true" />
                          <span>Yetersiz Veri (INSUFFICIENT_DATA)</span>
                        </Badge>
                      ) : (
                        <span className="text-xs font-semibold tabular-nums text-foreground">
                          {formatNumber(component.normalizedScore, 1)} / 100
                        </span>
                      )}
                    </div>

                    <div
                      aria-hidden="true"
                      className="h-1.5 w-full overflow-hidden rounded-full bg-border"
                    >
                      <div
                        className="h-full rounded-full bg-accent transition-all"
                        style={{ width: `${progressWidth}%` }}
                      />
                    </div>

                    <p className="text-xs leading-relaxed text-muted">
                      {component.explanation}
                    </p>

                    <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-muted">
                      <span>
                        Katkı:{" "}
                        <strong className="text-foreground tabular-nums">
                          {component.weightedContribution !== null
                            ? formatNumber(component.weightedContribution, 2)
                            : "—"}
                        </strong>
                      </span>
                      {metricEntries.length > 0 ? (
                        <div className="flex flex-wrap gap-1.5">
                          {metricEntries.map(([k, v]) => (
                            <span
                              key={k}
                              className="rounded bg-surface px-1.5 py-0.5 font-mono text-[10px] border border-border"
                            >
                              {translateIndicatorField(k)}: {formatNumber(v, 2)}
                            </span>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <DecisionSupportDisclaimer disclaimer={analysis.explanation.disclaimer} />
        </CardContent>
      </Card>
    </div>
  );
}
