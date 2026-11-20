"use client";

import React, { useEffect, useMemo, useState } from "react";
import { Input, Select } from "@/components/ui/input";
import { apiServices } from "@/lib/api/services";
import { DEFAULT_EXCHANGE } from "@/lib/format";

interface SymbolOption {
  symbol: string;
  name: string;
}

let catalogCache: Promise<SymbolOption[]> | null = null;

async function loadCatalog(exchange: string): Promise<SymbolOption[]> {
  const options: SymbolOption[] = [];
  let page = 0;
  let totalPages = 1;
  while (page < totalPages) {
    const res = await apiServices.instruments.list({ exchange, page, size: 100 });
    totalPages = res.totalPages;
    for (const item of res.items) {
      options.push({ symbol: item.symbol, name: item.name });
    }
    page += 1;
  }
  return options;
}

function getCatalog(exchange: string): Promise<SymbolOption[]> {
  if (!catalogCache) {
    catalogCache = loadCatalog(exchange).catch((err) => {
      catalogCache = null;
      throw err;
    });
  }
  return catalogCache;
}

export interface SymbolSelectProps {
  id: string;
  value: string;
  onChange: (symbol: string) => void;
  /** Symbols pinned to the top (e.g. current portfolio holdings). */
  pinnedSymbols?: string[];
  pinnedLabel?: string;
  exchange?: string;
  disabled?: boolean;
}

/**
 * Searchable symbol picker backed by the full exchange instrument catalog so users select
 * a listed symbol instead of typing it manually.
 */
export function SymbolSelect({
  id,
  value,
  onChange,
  pinnedSymbols = [],
  pinnedLabel = "Portföydeki Hisseler",
  exchange = DEFAULT_EXCHANGE,
  disabled,
}: SymbolSelectProps) {
  const [catalog, setCatalog] = useState<SymbolOption[]>([]);
  const [filter, setFilter] = useState("");
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!apiServices.instruments?.list) {
      return;
    }
    getCatalog(exchange)
      .then((list) => {
        if (!cancelled) {
          setCatalog(list);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setFailed(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [exchange]);

  const normalizedFilter = filter.trim().toLocaleLowerCase("tr-TR");
  const pinnedSet = useMemo(() => new Set(pinnedSymbols), [pinnedSymbols]);

  const filtered = useMemo(() => {
    const matches = (o: SymbolOption) =>
      !normalizedFilter ||
      o.symbol.toLocaleLowerCase("tr-TR").includes(normalizedFilter) ||
      o.name.toLocaleLowerCase("tr-TR").includes(normalizedFilter);
    return catalog.filter(matches);
  }, [catalog, normalizedFilter]);

  const pinnedOptions = filtered.filter((o) => pinnedSet.has(o.symbol));
  const otherOptions = filtered.filter((o) => !pinnedSet.has(o.symbol));
  const valueListed = catalog.some((o) => o.symbol === value);

  return (
    <div className="space-y-1.5">
      <Input
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        placeholder="Listede ara (sembol / şirket)"
        aria-label="Sembol listesinde ara"
        className="h-8 text-xs"
        disabled={disabled || catalog.length === 0}
      />
      <Select
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
      >
        {!valueListed && value ? <option value={value}>{value}</option> : null}
        {pinnedOptions.length > 0 ? (
          <optgroup label={pinnedLabel}>
            {pinnedOptions.map((o) => (
              <option key={`p-${o.symbol}`} value={o.symbol}>
                {o.symbol} — {o.name}
              </option>
            ))}
          </optgroup>
        ) : null}
        <optgroup label={`${exchange} Hisseleri (${otherOptions.length})`}>
          {otherOptions.map((o) => (
            <option key={o.symbol} value={o.symbol}>
              {o.symbol} — {o.name}
            </option>
          ))}
        </optgroup>
      </Select>
      {failed ? (
        <p className="text-[11px] text-warning">
          Hisse listesi yüklenemedi; sayfayı yenileyip tekrar deneyin.
        </p>
      ) : null}
    </div>
  );
}
