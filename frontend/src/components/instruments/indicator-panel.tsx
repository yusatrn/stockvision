"use client";

import React from "react";
import { AlertTriangle, CheckCircle2, SlidersHorizontal } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import type { IndicatorType, TechnicalIndicatorsResponse } from "@/lib/api/types";
import { formatNumber } from "@/lib/format";
import { translateIndicator, translateIndicatorField } from "@/lib/labels";

export const ALL_SUPPORTED_INDICATORS: {
  type: IndicatorType;
  label: string;
  overlaySupported: boolean;
}[] = [
  { type: "SMA", label: "SMA (Basit Hareketli Ort.)", overlaySupported: true },
  { type: "EMA", label: "EMA (Üssel Hareketli Ort.)", overlaySupported: true },
  { type: "BOLLINGER", label: "Bollinger Bantları", overlaySupported: true },
  { type: "RSI", label: "RSI (Göreceli Güç Endeksi)", overlaySupported: false },
  { type: "MACD", label: "MACD", overlaySupported: false },
  { type: "STOCHASTIC", label: "Stokastik Osilatör", overlaySupported: false },
  { type: "ATR", label: "ATR (Ortalama Gerçek Aralık)", overlaySupported: false },
  { type: "VOLUME_AVG", label: "Hacim Ort. ve Oranı", overlaySupported: false },
];

export function IndicatorPanel({
  technicalData,
  selectedIndicators,
  onToggleIndicator,
}: {
  technicalData: TechnicalIndicatorsResponse;
  selectedIndicators: IndicatorType[];
  onToggleIndicator: (indicator: IndicatorType) => void;
}) {
  const overallStatusLabel =
    technicalData.status === "READY"
      ? "HAZIR"
      : technicalData.status === "PARTIAL"
      ? "KISMİ"
      : "Yetersiz Veri (INSUFFICIENT_DATA)";

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <SlidersHorizontal className="h-4 w-4 text-accent" aria-hidden="true" />
              <span>Teknik İndikatörler · {technicalData.engineVersion}</span>
            </CardTitle>
            <CardDescription>
              Aktif indikatörleri seçin ({technicalData.barCount} adet {technicalData.interval} barı analiz edildi · Durum: {overallStatusLabel})
            </CardDescription>
          </div>
          <Badge
            variant={
              technicalData.status === "READY"
                ? "positive"
                : technicalData.status === "PARTIAL"
                ? "warning"
                : "negative"
            }
          >
            {overallStatusLabel}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div
          role="group"
          aria-label="Teknik indikatörleri aç veya kapat"
          className="flex flex-wrap gap-2"
        >
          {ALL_SUPPORTED_INDICATORS.map((item) => {
            const active = selectedIndicators.includes(item.type);
            return (
              <button
                key={item.type}
                type="button"
                aria-pressed={active}
                onClick={() => onToggleIndicator(item.type)}
                className={`rounded-md border px-2.5 py-1.5 text-xs font-medium transition-colors ${
                  active
                    ? "border-accent bg-accent/15 text-accent"
                    : "border-border bg-surface-muted/40 text-muted hover:text-foreground"
                }`}
              >
                {translateIndicator(item.type)}
              </button>
            );
          })}
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {technicalData.indicators.map((series) => {
            const paramEntries = Object.entries(series.parameters || {});
            const valueEntries = Object.entries(series.latestValues || {});

            return (
              <div
                key={series.type}
                className="rounded-md border border-border bg-surface-muted/30 p-3 space-y-2"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs font-bold">{translateIndicator(series.type)}</span>
                  {series.status === "READY" ? (
                    <Badge variant="positive">
                      <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
                      <span>HAZIR</span>
                    </Badge>
                  ) : (
                    <Badge variant="warning">
                      <AlertTriangle className="h-3 w-3" aria-hidden="true" />
                      <span>Yetersiz Veri (INSUFFICIENT_DATA)</span>
                    </Badge>
                  )}
                </div>

                <p className="text-[11px] text-muted tabular-nums">
                  Bar: {series.availableBars} / {series.requiredBars} gerekli (ısınma:{" "}
                  {series.warmUpBars})
                </p>

                {paramEntries.length > 0 ? (
                  <p className="text-[11px] font-mono text-muted">
                    {paramEntries.map(([k, v]) => `${k}=${String(v)}`).join(", ")}
                  </p>
                ) : null}

                {valueEntries.length > 0 ? (
                  <dl className="space-y-1 border-t border-border/60 pt-2 text-xs tabular-nums">
                    {valueEntries.map(([key, val]) => (
                      <div key={key} className="flex items-center justify-between gap-2">
                        <dt className="text-muted">{translateIndicatorField(key)}</dt>
                        <dd className="font-semibold text-foreground">
                          {formatNumber(val, 2)}
                        </dd>
                      </div>
                    ))}
                  </dl>
                ) : (
                  <p className="border-t border-border/60 pt-2 text-xs text-muted">
                    Son değerlerin hesaplanması için geçmiş bar sayısı yetersiz.
                  </p>
                )}
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}
