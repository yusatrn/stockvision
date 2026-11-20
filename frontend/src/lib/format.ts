export type NumericInput = number | string | null | undefined;

export const DEFAULT_TIMEZONE =
  process.env.NEXT_PUBLIC_DEFAULT_TIMEZONE || "Europe/Istanbul";

export const DEFAULT_EXCHANGE =
  process.env.NEXT_PUBLIC_DEFAULT_EXCHANGE || "BIST";

/**
 * Safely parses numeric API fields (including Java BigDecimal serialized as number or string).
 */
export function toNumeric(value: NumericInput): number | null {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const parsed = typeof value === "number" ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

/**
 * Formats monetary amounts with explicit decimal rounding (default: 2 fraction digits).
 */
export function formatCurrency(
  value: NumericInput,
  currency = "TRY",
  fractionDigits = 2
): string {
  const num = toNumeric(value);
  if (num === null) {
    return "—";
  }
  try {
    return new Intl.NumberFormat("tr-TR", {
      style: "currency",
      currency: currency || "TRY",
      currencyDisplay: "code",
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    }).format(num);
  } catch {
    return `${formatNumber(num, fractionDigits)} ${currency}`;
  }
}

/**
 * Formats decimal metrics/indicators with explicit fixed rounding precision.
 */
export function formatNumber(
  value: NumericInput,
  fractionDigits = 2
): string {
  const num = toNumeric(value);
  if (num === null) {
    return "—";
  }
  return new Intl.NumberFormat("tr-TR", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(num);
}

/**
 * Formats signed percentage changes (e.g. +2,45%, -1,10%, 0,00%).
 */
export function formatSignedPercent(
  value: NumericInput,
  fractionDigits = 2
): string {
  const num = toNumeric(value);
  if (num === null) {
    return "—";
  }
  const absFormatted = formatNumber(Math.abs(num), fractionDigits);
  if (num > 0) {
    return `+${absFormatted}%`;
  }
  if (num < 0) {
    return `-${absFormatted}%`;
  }
  return `${formatNumber(0, fractionDigits)}%`;
}

/**
 * Formats large integer metrics such as trading volume in compact notation.
 */
export function formatCompactNumber(value: NumericInput): string {
  const num = toNumeric(value);
  if (num === null) {
    return "—";
  }
  return new Intl.NumberFormat("tr-TR", {
    notation: "compact",
    maximumFractionDigits: 2,
  }).format(num);
}

/**
 * Formats UTC ISO timestamps into the user's selected IANA timezone with explicit timezone label.
 */
export function formatDateTime(
  isoUtc: string | null | undefined,
  timezone: string = DEFAULT_TIMEZONE
): string {
  if (!isoUtc) {
    return "—";
  }
  const date = new Date(isoUtc);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }

  const formatWithZone = (zone: string) =>
    new Intl.DateTimeFormat("tr-TR", {
      timeZone: zone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
      timeZoneName: "short",
    }).format(date);

  try {
    return formatWithZone(timezone || DEFAULT_TIMEZONE);
  } catch {
    try {
      return formatWithZone("UTC");
    } catch {
      return isoUtc;
    }
  }
}

/**
 * Formats UTC date-only labels (e.g., bar dates on technical cards) in the chosen timezone.
 */
export function formatShortDate(
  isoUtc: string | null | undefined,
  timezone: string = DEFAULT_TIMEZONE
): string {
  if (!isoUtc) {
    return "—";
  }
  const date = new Date(isoUtc);
  if (Number.isNaN(date.getTime())) {
    return "—";
  }
  try {
    return new Intl.DateTimeFormat("tr-TR", {
      timeZone: timezone || DEFAULT_TIMEZONE,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(date);
  } catch {
    return isoUtc.slice(0, 10);
  }
}

/**
 * Formats relative freshness age in minutes into human-readable Turkish labels.
 */
export function formatRelativeAgeMinutes(
  ageMinutes: number | null | undefined
): string {
  if (ageMinutes === null || ageMinutes === undefined || !Number.isFinite(ageMinutes)) {
    return "Bilinmiyor";
  }
  const mins = Math.max(0, Math.round(ageMinutes));
  if (mins < 1) {
    return "< 1 dk önce";
  }
  if (mins < 60) {
    return `${mins} dk önce`;
  }
  const hours = Math.floor(mins / 60);
  const remMins = mins % 60;
  if (hours < 24) {
    return remMins > 0 ? `${hours} sa ${remMins} dk önce` : `${hours} sa önce`;
  }
  const days = Math.floor(hours / 24);
  const remHours = hours % 24;
  return remHours > 0 ? `${days} gün ${remHours} sa önce` : `${days} gün önce`;
}
