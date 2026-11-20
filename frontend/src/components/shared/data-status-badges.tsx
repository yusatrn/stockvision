"use client";

import React from "react";
import {
  AlertTriangle,
  ArrowDownRight,
  ArrowUpRight,
  CheckCircle2,
  Clock,
  Database,
  Flame,
  FlaskConical,
  Minus,
  Radio,
  ShieldAlert,
  Target,
  TrendingDown,
  TrendingUp,
  XCircle,
} from "lucide-react";
import { Badge, type BadgeVariant } from "@/components/ui/badge";
import type {
  DataProviderMode,
  DataSourceMetadataResponse,
  FreshnessMetadataResponse,
  FreshnessStatus,
  ScoreRegime,
  ScoreStatus,
  SetupCategory,
  TrendDirection,
} from "@/lib/api/types";
import {
  formatDateTime,
  formatNumber,
  formatRelativeAgeMinutes,
  formatSignedPercent,
  toNumeric,
} from "@/lib/format";
import { translateSetupCategory } from "@/lib/labels";
import { cn } from "@/lib/utils";

export function ProviderModeBadge({
  mode,
  simulated,
  delayMinutes = 0,
}: {
  mode: DataProviderMode;
  simulated?: boolean;
  delayMinutes?: number;
}) {
  if (simulated || mode === "SIMULATED") {
    return (
      <Badge variant="warning" title="Simüle/mock piyasa verisi sağlayıcısı tarafından üretilmiştir">
        <FlaskConical className="h-3 w-3" aria-hidden="true" />
        <span>Simüle Veri</span>
      </Badge>
    );
  }
  if (mode === "DELAYED") {
    return (
      <Badge variant="info" title="Gecikmeli gerçek piyasa verisi akışı">
        <Clock className="h-3 w-3" aria-hidden="true" />
        <span>{delayMinutes > 0 ? `Gecikmeli (${delayMinutes} dk)` : "Gecikmeli Akış"}</span>
      </Badge>
    );
  }
  if (mode === "REALTIME") {
    return (
      <Badge variant="positive">
        <Radio className="h-3 w-3" aria-hidden="true" />
        <span>Gerçek Zamanlı</span>
      </Badge>
    );
  }
  return (
    <Badge variant="neutral">
      <Database className="h-3 w-3" aria-hidden="true" />
      <span>Yapılandırılmamış</span>
    </Badge>
  );
}

const FRESHNESS_MAP: Record<
  FreshnessStatus,
  { variant: BadgeVariant; label: string; Icon: React.ElementType }
> = {
  FRESH: { variant: "positive", label: "Güncel", Icon: CheckCircle2 },
  STALE: { variant: "warning", label: "Bayat Veri", Icon: AlertTriangle },
  SIMULATED_ONLY: { variant: "warning", label: "Simüle Akış", Icon: FlaskConical },
  NO_DATA: { variant: "negative", label: "Veri Yok", Icon: XCircle },
};

export function FreshnessBadge({
  freshness,
}: {
  freshness: FreshnessMetadataResponse;
}) {
  const cfg = FRESHNESS_MAP[freshness.status] ?? {
    variant: "neutral" as BadgeVariant,
    label: freshness.status,
    Icon: Clock,
  };
  const Icon = cfg.Icon;
  const age = formatRelativeAgeMinutes(freshness.ageMinutes);

  return (
    <Badge variant={cfg.variant} title={freshness.note}>
      <Icon className="h-3 w-3" aria-hidden="true" />
      <span>
        {cfg.label}
        {freshness.ageMinutes !== null ? ` · ${age}` : ""}
      </span>
    </Badge>
  );
}

export function SignedPercentBadge({
  value,
  fractionDigits = 2,
  className,
}: {
  value: number | string | null | undefined;
  fractionDigits?: number;
  className?: string;
}) {
  const num = toNumeric(value);
  if (num === null) {
    return (
      <span className={cn("inline-flex items-center gap-1 text-xs text-muted", className)}>
        <Minus className="h-3 w-3" aria-hidden="true" />
        <span>—</span>
      </span>
    );
  }

  const isUp = num > 0;
  const isDown = num < 0;
  const Icon = isUp ? ArrowUpRight : isDown ? ArrowDownRight : Minus;
  const variant: BadgeVariant = isUp ? "positive" : isDown ? "negative" : "neutral";
  const directionText = isUp ? "Yükseliş" : isDown ? "Düşüş" : "Değişimsiz";

  return (
    <Badge
      variant={variant}
      aria-label={`${directionText} ${formatSignedPercent(num, fractionDigits)}`}
      className={cn("tabular-nums", className)}
    >
      <Icon className="h-3 w-3" aria-hidden="true" />
      <span>{formatSignedPercent(num, fractionDigits)}</span>
    </Badge>
  );
}

const REGIME_LABELS: Record<string, { label: string; variant: BadgeVariant }> = {
  STRONG_BULLISH_TECHNICAL: { label: "Güçlü Pozitif Teknik", variant: "positive" },
  MODERATE_BULLISH_TECHNICAL: { label: "Ilımlı Pozitif Teknik", variant: "positive" },
  NEUTRAL_TECHNICAL: { label: "Nötr Teknik", variant: "neutral" },
  MODERATE_BEARISH_TECHNICAL: { label: "Ilımlı Negatif Teknik", variant: "negative" },
  STRONG_BEARISH_TECHNICAL: { label: "Güçlü Negatif Teknik", variant: "negative" },
  STRONG_BULLISH: { label: "Güçlü Pozitif Teknik", variant: "positive" },
  BULLISH: { label: "Pozitif Teknik", variant: "positive" },
  NEUTRAL: { label: "Nötr Teknik", variant: "neutral" },
  BEARISH: { label: "Negatif Teknik", variant: "negative" },
  STRONG_BEARISH: { label: "Güçlü Negatif Teknik", variant: "negative" },
  INSUFFICIENT_DATA: { label: "Yetersiz Veri (INSUFFICIENT_DATA)", variant: "warning" },
};

export function RegimeBadge({
  regime,
  status,
}: {
  regime: ScoreRegime;
  status?: ScoreStatus;
}) {
  if (status === "INSUFFICIENT_DATA" || regime === "INSUFFICIENT_DATA") {
    return (
      <Badge variant="warning">
        <AlertTriangle className="h-3 w-3" aria-hidden="true" />
        <span>Yetersiz Veri (INSUFFICIENT_DATA)</span>
      </Badge>
    );
  }
  const cfg = REGIME_LABELS[regime] ?? {
    label: regime.replaceAll("_", " "),
    variant: "neutral" as BadgeVariant,
  };
  const Icon =
    cfg.variant === "positive"
      ? TrendingUp
      : cfg.variant === "negative"
      ? TrendingDown
      : Minus;

  return (
    <Badge variant={cfg.variant}>
      <Icon className="h-3 w-3" aria-hidden="true" />
      <span>{cfg.label}</span>
    </Badge>
  );
}

export function TrendBadge({ trend }: { trend: TrendDirection }) {
  if (trend === "INSUFFICIENT_DATA" || trend === "UNKNOWN") {
    return (
      <Badge variant="warning">
        <AlertTriangle className="h-3 w-3" aria-hidden="true" />
        <span>
          {trend === "UNKNOWN" ? "Belirsiz Trend" : "Yetersiz Veri (INSUFFICIENT_DATA)"}
        </span>
      </Badge>
    );
  }
  if (trend === "BULLISH") {
    return (
      <Badge variant="positive">
        <TrendingUp className="h-3 w-3" aria-hidden="true" />
        <span>Yükseliş Trendi</span>
      </Badge>
    );
  }
  if (trend === "BEARISH") {
    return (
      <Badge variant="negative">
        <TrendingDown className="h-3 w-3" aria-hidden="true" />
        <span>Düşüş Trendi</span>
      </Badge>
    );
  }
  return (
    <Badge variant="neutral">
      <Minus className="h-3 w-3" aria-hidden="true" />
      <span>Yatay Trend</span>
    </Badge>
  );
}

export function DataProvenanceBar({
  dataSource,
  freshness,
  timezone,
  calculatedAt,
  extraLabel,
}: {
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
  timezone: string;
  calculatedAt?: string | null;
  extraLabel?: string;
}) {
  return (
    <div className="flex flex-col gap-2 rounded-lg border border-border bg-surface-muted/50 px-3.5 py-2.5 text-xs text-muted sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
      <div className="flex flex-wrap items-center gap-2">
        <ProviderModeBadge
          mode={dataSource.mode}
          simulated={dataSource.simulated}
          delayMinutes={dataSource.delayMinutes}
        />
        <FreshnessBadge freshness={freshness} />
        <span className="text-foreground/90 font-medium">
          Kaynak: {dataSource.displayName}
        </span>
        <span aria-hidden="true">·</span>
        <span>Akış Saat Dilimi: {dataSource.sourceTimezone}</span>
        {extraLabel ? (
          <>
            <span aria-hidden="true">·</span>
            <span>{extraLabel}</span>
          </>
        ) : null}
      </div>

      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span>
          Son Bar:{" "}
          <strong className="font-medium text-foreground">
            {formatDateTime(freshness.latestDataTimestamp, timezone)}
          </strong>
        </span>
        {calculatedAt ? (
          <span>
            Hesaplama:{" "}
            <strong className="font-medium text-foreground">
              {formatDateTime(calculatedAt, timezone)}
            </strong>
          </span>
        ) : null}
        {freshness.lastSyncedAt ? (
          <span>
            Senkronizasyon:{" "}
            <strong className="font-medium text-foreground">
              {formatDateTime(freshness.lastSyncedAt, timezone)}
            </strong>
          </span>
        ) : null}
      </div>

      {dataSource.licenseNote || freshness.note ? (
        <p className="w-full text-[11px] leading-relaxed text-muted">
          {freshness.note}
          {freshness.note && dataSource.licenseNote ? " · " : ""}
          {dataSource.licenseNote}
        </p>
      ) : null}
    </div>
  );
}

export function ScorePill({
  score,
  status,
}: {
  score: number | null;
  status: ScoreStatus;
}) {
  if (status === "INSUFFICIENT_DATA" || score === null) {
    return (
      <Badge variant="warning" className="tabular-nums">
        <AlertTriangle className="h-3 w-3" aria-hidden="true" />
        <span>Yetersiz Veri (INSUFFICIENT_DATA)</span>
      </Badge>
    );
  }
  const variant: BadgeVariant =
    score >= 65 ? "positive" : score <= 35 ? "negative" : "info";
  return (
    <Badge variant={variant} className="tabular-nums font-semibold">
      <span>{formatNumber(score, 1)} / 100</span>
    </Badge>
  );
}

export function SetupCategoryBadge({ category }: { category: SetupCategory }) {
  const label = translateSetupCategory(category);
  switch (category) {
    case "HIGH_MOMENTUM_BREAKOUT":
      return (
        <Badge variant="positive">
          <Flame className="h-3 w-3" aria-hidden="true" />
          <span>{label}</span>
        </Badge>
      );
    case "PULLBACK_WATCH":
      return (
        <Badge variant="info">
          <Target className="h-3 w-3" aria-hidden="true" />
          <span>{label}</span>
        </Badge>
      );
    case "OVEREXTENDED_RISK":
      return (
        <Badge variant="warning">
          <AlertTriangle className="h-3 w-3" aria-hidden="true" />
          <span>{label}</span>
        </Badge>
      );
    case "WEAK_BEARISH":
      return (
        <Badge variant="negative">
          <TrendingDown className="h-3 w-3" aria-hidden="true" />
          <span>{label}</span>
        </Badge>
      );
    case "INSUFFICIENT_DATA":
      return (
        <Badge variant="warning">
          <AlertTriangle className="h-3 w-3" aria-hidden="true" />
          <span>{label}</span>
        </Badge>
      );
    default:
      return (
        <Badge variant="neutral">
          <Minus className="h-3 w-3" aria-hidden="true" />
          <span>{label}</span>
        </Badge>
      );
  }
}

export function DecisionSupportDisclaimer({
  disclaimer,
  className,
}: {
  disclaimer?: string;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex items-start gap-2.5 rounded-lg border border-border bg-surface-muted/40 px-3.5 py-2.5 text-xs text-muted",
        className
      )}
    >
      <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
      <p className="leading-relaxed">
        <strong className="font-semibold text-foreground">Karar Destek Uyarısı:</strong>{" "}
        {disclaimer ||
          "StockVision AI yalnızca kural tabanlı teknik analiz ve piyasa karar destek bilgileri sunar. Kişiye özel yatırım danışmanlığı değildir, otomatik emir iletmez ve kâr ya da risksiz getiri iddiası içermez."}
      </p>
    </div>
  );
}
