"use client";

import React from "react";
import Link from "next/link";
import {
  AlertTriangle,
  BellPlus,
  CheckCircle2,
  Layers,
  ShieldAlert,
  TrendingUp,
} from "lucide-react";
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
import { buttonClasses } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import type {
  BistMarginReferenceResponse,
  InstrumentSetupResponse,
  PriceZoneResponse,
  RiskRewardMetricsResponse,
  ScoreStatus,
  SetupCategory,
} from "@/lib/api/types";
import { formatCurrency, formatNumber, toNumeric } from "@/lib/format";

export interface TechnicalZonesProps {
  currency: string;
  status: ScoreStatus;
  category: SetupCategory;
  technicalReferencePrice: number | null;
  breakoutTriggerLevel: number | null;
  distanceToBreakoutPercent: number | null;
  breakoutConfirmed: boolean;
  supportWatchZone: PriceZoneResponse | null;
  resistanceExitZone: PriceZoneResponse | null;
  invalidationRiskLevel: number | null;
  invalidationRationale: string | null;
  riskReward: RiskRewardMetricsResponse | null;
  bistMargin: BistMarginReferenceResponse | null;
  rationaleItems: string[];
  riskWarnings: string[];
  usedIndicators: string[];
  insufficientDataReasons: string[];
}

function positionPercent(value: number, min: number, max: number): number {
  if (max <= min) {
    return 50;
  }
  const pct = ((value - min) / (max - min)) * 100;
  return Math.max(2, Math.min(98, pct));
}

export function PriceLadderBar({
  currency,
  invalidationRiskLevel,
  supportWatchZone,
  referencePrice,
  breakoutTriggerLevel,
  resistanceExitZone,
}: {
  currency: string;
  invalidationRiskLevel: number;
  supportWatchZone: PriceZoneResponse;
  referencePrice: number;
  breakoutTriggerLevel: number | null;
  resistanceExitZone: PriceZoneResponse;
}) {
  const candidates = [
    invalidationRiskLevel,
    supportWatchZone.low,
    supportWatchZone.high,
    referencePrice,
    resistanceExitZone.low,
    resistanceExitZone.high,
  ];
  if (breakoutTriggerLevel !== null) {
    candidates.push(breakoutTriggerLevel);
  }
  const minVal = Math.min(...candidates);
  const maxVal = Math.max(...candidates);

  const stopPct = positionPercent(invalidationRiskLevel, minVal, maxVal);
  const supLowPct = positionPercent(supportWatchZone.low, minVal, maxVal);
  const supHighPct = positionPercent(supportWatchZone.high, minVal, maxVal);
  const refPct = positionPercent(referencePrice, minVal, maxVal);
  const resLowPct = positionPercent(resistanceExitZone.low, minVal, maxVal);
  const resHighPct = positionPercent(resistanceExitZone.high, minVal, maxVal);

  return (
    <div className="space-y-2 rounded-md border border-border bg-surface-muted/30 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-muted">
        <span className="font-medium text-foreground">
          Teknik Seviye Merdiveni (Stop → Destek → Fiyat → Direnç R1–R2)
        </span>
        <span className="tabular-nums">
          Son Ref.: <strong className="text-foreground">{formatCurrency(referencePrice, currency)}</strong>
        </span>
      </div>

      <div
        className="relative h-5 w-full rounded-full bg-surface-muted border border-border overflow-hidden"
        role="img"
        aria-label={`Teknik seviye merdiveni: Stop ${formatCurrency(invalidationRiskLevel, currency)}, Destek ${formatCurrency(supportWatchZone.low, currency)}-${formatCurrency(supportWatchZone.high, currency)}, Mevcut ${formatCurrency(referencePrice, currency)}, Direnç ${formatCurrency(resistanceExitZone.low, currency)}-${formatCurrency(resistanceExitZone.high, currency)}`}
      >
        <div
          className="absolute top-0 bottom-0 bg-info/30"
          style={{
            left: `${Math.min(supLowPct, supHighPct)}%`,
            width: `${Math.max(3, Math.abs(supHighPct - supLowPct))}%`,
          }}
          title={`Destek/İzleme Bandı: ${formatCurrency(supportWatchZone.low, currency)} – ${formatCurrency(supportWatchZone.high, currency)}`}
        />
        <div
          className="absolute top-0 bottom-0 bg-positive/30"
          style={{
            left: `${Math.min(resLowPct, resHighPct)}%`,
            width: `${Math.max(3, Math.abs(resHighPct - resLowPct))}%`,
          }}
          title={`Direnç/Realizasyon Bandı: ${formatCurrency(resistanceExitZone.low, currency)} – ${formatCurrency(resistanceExitZone.high, currency)}`}
        />
        <div
          className="absolute top-0 bottom-0 w-1 bg-negative"
          style={{ left: `${stopPct}%` }}
          title={`Risk Sınırı (Stop Ref.): ${formatCurrency(invalidationRiskLevel, currency)}`}
        />
        <div
          className="absolute top-0 bottom-0 w-1.5 -ml-0.5 bg-foreground"
          style={{ left: `${refPct}%` }}
          title={`Referans Fiyat: ${formatCurrency(referencePrice, currency)}`}
        />
      </div>

      <div className="grid grid-cols-2 gap-2 text-[11px] sm:grid-cols-4">
        <div className="tabular-nums">
          <span className="text-negative font-medium">Stop Ref.: </span>
          <span className="text-foreground font-semibold">
            {formatCurrency(invalidationRiskLevel, currency)}
          </span>
        </div>
        <div className="tabular-nums">
          <span className="text-info font-medium">Destek Bandı: </span>
          <span className="text-foreground font-semibold">
            {formatCurrency(supportWatchZone.low, currency)} – {formatCurrency(supportWatchZone.high, currency)}
          </span>
        </div>
        <div className="tabular-nums">
          <span className="text-accent font-medium">Kırılım Eşiği: </span>
          <span className="text-foreground font-semibold">
            {breakoutTriggerLevel !== null ? formatCurrency(breakoutTriggerLevel, currency) : "—"}
          </span>
        </div>
        <div className="tabular-nums">
          <span className="text-positive font-medium">R1 / R2 Bölgesi: </span>
          <span className="text-foreground font-semibold">
            {formatCurrency(resistanceExitZone.low, currency)} – {formatCurrency(resistanceExitZone.high, currency)}
          </span>
        </div>
      </div>
    </div>
  );
}

export function BistMarginStrip({
  currency,
  bistMargin,
}: {
  currency: string;
  bistMargin: BistMarginReferenceResponse;
}) {
  return (
    <div className="rounded-md border border-border bg-surface-muted/35 p-3 space-y-1.5 text-xs">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="font-semibold text-foreground">
          BIST ±%10 Günlük Teorik Fiyat Marjı Referansı
        </span>
        <span className="text-[11px] text-muted">
          Önceki Kapanış: {formatCurrency(bistMargin.previousClose, currency)}
        </span>
      </div>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 tabular-nums">
        <div>
          <span className="text-muted block text-[11px]">Teorik Taban (-%10)</span>
          <span className="font-semibold text-negative">
            {formatCurrency(bistMargin.theoreticalFloorPrice, currency)}
          </span>
        </div>
        <div>
          <span className="text-muted block text-[11px]">Referans Fiyat & Değişim</span>
          <div className="flex items-center gap-1.5">
            <span className="font-semibold text-foreground">
              {formatCurrency(bistMargin.referencePrice, currency)}
            </span>
            <SignedPercentBadge value={bistMargin.dailyChangePercent} />
          </div>
        </div>
        <div>
          <span className="text-muted block text-[11px]">Teorik Tavan (+%10)</span>
          <span className="font-semibold text-positive">
            {formatCurrency(bistMargin.theoreticalCeilingPrice, currency)}
          </span>
        </div>
        <div>
          <span className="text-muted block text-[11px]">Teorik Tavana Mesafe</span>
          <span className="font-semibold text-foreground">
            %{formatNumber(bistMargin.distanceToCeilingPercent, 2)}
          </span>
        </div>
      </div>
      <p className="text-[11px] text-muted">{bistMargin.note}</p>
    </div>
  );
}

export function TechnicalSetupBody({
  data,
  compact = false,
}: {
  data: TechnicalZonesProps;
  compact?: boolean;
}) {
  const ready =
    data.status === "READY" &&
    data.technicalReferencePrice !== null &&
    data.supportWatchZone !== null &&
    data.resistanceExitZone !== null &&
    data.invalidationRiskLevel !== null &&
    data.riskReward !== null;

  if (!ready) {
    return (
      <div className="space-y-3">
        <Alert
          variant="warning"
          title="Yetersiz Veri (INSUFFICIENT_DATA) · Teknik Seviyeler Üretilmedi"
        >
          <ul className="list-disc pl-4 space-y-1">
            {(data.insufficientDataReasons.length > 0
              ? data.insufficientDataReasons
              : ["Geçerli destek, direnç ve ATR hesabı için yeterli bar geçmişi bulunamadı."]
            ).map((reason, idx) => (
              <li key={idx}>{reason}</li>
            ))}
          </ul>
        </Alert>
        {data.bistMargin ? (
          <BistMarginStrip currency={data.currency} bistMargin={data.bistMargin} />
        ) : null}
      </div>
    );
  }

  const refPrice = data.technicalReferencePrice!;
  const supZone = data.supportWatchZone!;
  const resZone = data.resistanceExitZone!;
  const stopLevel = data.invalidationRiskLevel!;
  const rr = data.riskReward!;
  const distBreakoutNum = toNumeric(data.distanceToBreakoutPercent);

  return (
    <div className="space-y-3">
      <PriceLadderBar
        currency={data.currency}
        invalidationRiskLevel={stopLevel}
        supportWatchZone={supZone}
        referencePrice={refPrice}
        breakoutTriggerLevel={data.breakoutTriggerLevel}
        resistanceExitZone={resZone}
      />

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-md border border-info/30 bg-info/5 p-3 space-y-1">
          <p className="text-xs font-semibold text-info">Destek / İzleme Bölgesi</p>
          <p className="text-sm font-bold tabular-nums text-foreground">
            {formatCurrency(supZone.low, data.currency)} – {formatCurrency(supZone.high, data.currency)}
          </p>
          <p className="text-[11px] text-muted leading-snug">{supZone.rationale}</p>
        </div>

        <div className="rounded-md border border-accent/30 bg-accent/5 p-3 space-y-1">
          <div className="flex items-center justify-between gap-1">
            <p className="text-xs font-semibold text-accent">Kırılım İzleme Eşiği</p>
            {data.breakoutConfirmed ? (
              <Badge variant="positive" className="text-[10px]">
                <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
                <span>Kırılım Üstü</span>
              </Badge>
            ) : null}
          </div>
          <p className="text-sm font-bold tabular-nums text-foreground">
            {data.breakoutTriggerLevel !== null
              ? formatCurrency(data.breakoutTriggerLevel, data.currency)
              : "—"}
          </p>
          <p className="text-[11px] text-muted leading-snug tabular-nums">
            {data.breakoutConfirmed
              ? "Fiyat önceki 19 barlık direnç eşiğinin üzerinde."
              : distBreakoutNum !== null
              ? `19 barlık eşiğe kalan mesafe: %${formatNumber(Math.abs(distBreakoutNum), 2)}`
              : "19 barlık yüksek seviye referansı."}
          </p>
        </div>

        <div className="rounded-md border border-positive/30 bg-positive/5 p-3 space-y-1">
          <p className="text-xs font-semibold text-positive">Direnç / Kâr Realizasyonu (R1–R2)</p>
          <p className="text-sm font-bold tabular-nums text-foreground">
            {formatCurrency(resZone.low, data.currency)} – {formatCurrency(resZone.high, data.currency)}
          </p>
          <p className="text-[11px] text-muted leading-snug tabular-nums">
            R1: +%{formatNumber(rr.upsideToR1Percent, 2)} · R2: +%{formatNumber(rr.upsideToR2Percent, 2)}
          </p>
        </div>

        <div className="rounded-md border border-negative/30 bg-negative/5 p-3 space-y-1">
          <p className="text-xs font-semibold text-negative">Risk Sınırı (Stop Ref.) & R/Ö</p>
          <p className="text-sm font-bold tabular-nums text-foreground">
            {formatCurrency(stopLevel, data.currency)}{" "}
            <span className="text-xs font-normal text-muted">
              (-%{formatNumber(rr.riskDistancePercent, 2)})
            </span>
          </p>
          <p className="text-[11px] text-muted leading-snug tabular-nums">
            Risk/Ödül: <strong className="text-foreground">1 : {formatNumber(rr.riskRewardToR1, 2)}</strong> (R1) ·{" "}
            <strong className="text-foreground">1 : {formatNumber(rr.riskRewardToR2, 2)}</strong> (R2)
          </p>
        </div>
      </div>

      {data.bistMargin ? (
        <BistMarginStrip currency={data.currency} bistMargin={data.bistMargin} />
      ) : null}

      {!compact && data.rationaleItems.length > 0 ? (
        <div className="rounded-md border border-border bg-surface p-3 space-y-1.5">
          <p className="text-xs font-semibold text-foreground">Kural Tabanlı Teknik Gerekçe</p>
          <ul className="list-disc pl-4 space-y-1 text-xs text-muted">
            {data.rationaleItems.map((item, idx) => (
              <li key={idx}>{item}</li>
            ))}
          </ul>
        </div>
      ) : null}

      {data.riskWarnings.length > 0 ? (
        <div className="rounded-md border border-warning/35 bg-warning/10 p-3 space-y-1">
          <p className="flex items-center gap-1.5 text-xs font-semibold text-warning">
            <AlertTriangle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            <span>Oynaklık ve Teknik Risk Notları</span>
          </p>
          <ul className="list-disc pl-4 space-y-0.5 text-xs text-foreground/90">
            {data.riskWarnings.map((warning, idx) => (
              <li key={idx}>{warning}</li>
            ))}
          </ul>
        </div>
      ) : null}

      {data.usedIndicators.length > 0 ? (
        <div className="flex flex-wrap items-center gap-1.5 text-[11px] text-muted">
          <span className="font-medium text-foreground">Kullanılan İndikatörler:</span>
          {data.usedIndicators.map((ind) => (
            <Badge key={ind} variant="neutral" className="text-[10px]">
              {ind}
            </Badge>
          ))}
        </div>
      ) : null}
    </div>
  );
}

export function TechnicalLevelsCard({
  setup,
  timezone,
}: {
  setup: InstrumentSetupResponse;
  timezone: string;
}) {
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Layers className="h-4 w-4 text-accent" aria-hidden="true" />
              <span>Teknik Seviyeler, Destek/Direnç Bölgeleri ve Risk Sınırı</span>
            </CardTitle>
            <CardDescription>{setup.headline}</CardDescription>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <SetupCategoryBadge category={setup.category} />
            <TrendBadge trend={setup.trendDirection} />
            <ScorePill score={setup.momentumBreakoutScore} status={setup.status} />
          </div>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        <DataProvenanceBar
          dataSource={setup.dataSource}
          freshness={setup.freshness}
          timezone={timezone}
          calculatedAt={setup.calculatedAt}
          extraLabel={`Motor: ${setup.setupEngineVersion} · ${setup.barCount} bar`}
        />

        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 text-xs">
          <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
            <span className="text-muted block">Momentum Skoru</span>
            <span className="mt-0.5 text-sm font-bold tabular-nums text-foreground">
              {setup.momentumBreakoutScore !== null
                ? `${formatNumber(setup.momentumBreakoutScore, 2)} / 100`
                : "INSUFFICIENT_DATA"}
            </span>
          </div>
          <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
            <span className="text-muted block">Bileşik Teknik Skor</span>
            <span className="mt-0.5 text-sm font-bold tabular-nums text-foreground">
              {setup.technicalScore !== null
                ? `${formatNumber(setup.technicalScore, 2)} / 100`
                : "INSUFFICIENT_DATA"}
            </span>
          </div>
          <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
            <span className="text-muted block">Hacim Oranı / RSI(14)</span>
            <span className="mt-0.5 text-sm font-bold tabular-nums text-foreground">
              {setup.volumeRatio !== null ? `${formatNumber(setup.volumeRatio, 2)}x` : "—"} ·{" "}
              RSI {setup.latestRsi !== null ? formatNumber(setup.latestRsi, 1) : "—"}
            </span>
          </div>
          <div className="rounded-md border border-border bg-surface-muted/40 p-2.5">
            <span className="text-muted block">ATR(14) Volatilite</span>
            <span className="mt-0.5 text-sm font-bold tabular-nums text-foreground">
              {setup.atr14 !== null
                ? `${formatCurrency(setup.atr14, setup.currency)} (%${formatNumber(setup.atrPercent, 2)})`
                : "—"}
            </span>
          </div>
        </div>

        <TechnicalSetupBody
          data={{
            currency: setup.currency,
            status: setup.status,
            category: setup.category,
            technicalReferencePrice: setup.technicalReferencePrice,
            breakoutTriggerLevel: setup.breakoutTriggerLevel,
            distanceToBreakoutPercent: setup.distanceToBreakoutPercent,
            breakoutConfirmed: setup.breakoutConfirmed,
            supportWatchZone: setup.supportWatchZone,
            resistanceExitZone: setup.resistanceExitZone,
            invalidationRiskLevel: setup.invalidationRiskLevel,
            invalidationRationale: setup.invalidationRationale,
            riskReward: setup.riskReward,
            bistMargin: setup.bistMargin,
            rationaleItems: setup.rationaleItems,
            riskWarnings: setup.riskWarnings,
            usedIndicators: setup.usedIndicators,
            insufficientDataReasons: setup.insufficientDataReasons,
          }}
        />

        <div className="flex items-start gap-2 rounded-md border border-border bg-surface-muted/35 p-2.5 text-[11px] text-muted">
          <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0 text-accent" aria-hidden="true" />
          <span>{setup.disclaimer}</span>
        </div>
      </CardContent>

      <CardFooter>
        <span>
          Ölçek: {setup.interval.toUpperCase()} ({setup.adjusted ? "Düzeltilmiş" : "Ham"})
        </span>
        <div className="flex flex-wrap items-center gap-3">
          <Link
            href={`/alerts?symbol=${encodeURIComponent(setup.symbol)}`}
            className={buttonClasses({ variant: "outline", size: "sm" })}
          >
            <BellPlus className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Seviye Alarmı Kur</span>
          </Link>
          <Link
            href="/opportunities"
            className="inline-flex items-center gap-1 text-xs font-medium text-accent hover:underline"
          >
            <TrendingUp className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Tüm Adayları Gör</span>
          </Link>
        </div>
      </CardFooter>
    </Card>
  );
}

export function OpportunitiesDisclaimerBanner({ disclaimer }: { disclaimer?: string }) {
  return <DecisionSupportDisclaimer disclaimer={disclaimer} />;
}
