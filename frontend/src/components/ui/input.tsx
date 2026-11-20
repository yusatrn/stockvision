import * as React from "react";
import { cn } from "@/lib/utils";

export function Label({
  className,
  ...props
}: React.LabelHTMLAttributes<HTMLLabelElement>) {
  return (
    <label
      className={cn("text-xs font-medium text-foreground", className)}
      {...props}
    />
  );
}

export interface InputProps
  extends React.InputHTMLAttributes<HTMLInputElement> {
  hasError?: boolean;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, hasError = false, ...props }, ref) => {
    return (
      <input
        ref={ref}
        aria-invalid={hasError || undefined}
        className={cn(
          "flex h-9 w-full rounded-md border bg-surface px-3 py-1 text-sm text-foreground shadow-sm transition-colors placeholder:text-muted disabled:cursor-not-allowed disabled:opacity-50",
          hasError ? "border-negative" : "border-border",
          className
        )}
        {...props}
      />
    );
  }
);

Input.displayName = "Input";

export interface SelectProps
  extends React.SelectHTMLAttributes<HTMLSelectElement> {
  hasError?: boolean;
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, hasError = false, children, ...props }, ref) => {
    return (
      <select
        ref={ref}
        aria-invalid={hasError || undefined}
        className={cn(
          "flex h-9 w-full rounded-md border bg-surface px-3 py-1 text-sm text-foreground shadow-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50",
          hasError ? "border-negative" : "border-border",
          className
        )}
        {...props}
      >
        {children}
      </select>
    );
  }
);

Select.displayName = "Select";
