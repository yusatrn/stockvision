import * as React from "react";
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Info,
} from "lucide-react";
import { cn } from "@/lib/utils";

export type AlertVariant = "info" | "warning" | "error" | "positive";

const VARIANT_CLASSES: Record<AlertVariant, string> = {
  info: "border-info/30 bg-info/10 text-foreground",
  warning: "border-warning/40 bg-warning/10 text-foreground",
  error: "border-negative/40 bg-negative/10 text-foreground",
  positive: "border-positive/30 bg-positive/10 text-foreground",
};

const ICON_CLASSES: Record<AlertVariant, string> = {
  info: "text-info",
  warning: "text-warning",
  error: "text-negative",
  positive: "text-positive",
};

export interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: AlertVariant;
  title?: string;
}

export function Alert({
  variant = "info",
  title,
  className,
  children,
  ...props
}: AlertProps) {
  const Icon =
    variant === "warning"
      ? AlertTriangle
      : variant === "error"
      ? AlertCircle
      : variant === "positive"
      ? CheckCircle2
      : Info;

  return (
    <div
      role={variant === "error" || variant === "warning" ? "alert" : "status"}
      className={cn(
        "flex items-start gap-3 rounded-lg border px-4 py-3 text-sm",
        VARIANT_CLASSES[variant],
        className
      )}
      {...props}
    >
      <Icon
        className={cn("mt-0.5 h-4 w-4 shrink-0", ICON_CLASSES[variant])}
        aria-hidden="true"
      />
      <div className="flex-1 space-y-1">
        {title ? <p className="font-semibold leading-snug">{title}</p> : null}
        <div className="text-xs leading-relaxed text-foreground/90 sm:text-sm">
          {children}
        </div>
      </div>
    </div>
  );
}
