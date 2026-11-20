"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Activity,
  BarChart3,
  Bell,
  Briefcase,
  Eye,
  Filter,
  FlaskConical,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  Search,
  Settings,
  Sun,
  Target,
  X,
} from "lucide-react";
import { useAuth } from "@/components/providers/auth-provider";
import { useTheme } from "@/components/providers/theme-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiServices } from "@/lib/api/services";
import type { HealthResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import {
  DecisionSupportDisclaimer,
  ProviderModeBadge,
} from "@/components/shared/data-status-badges";

interface NavItem {
  label: string;
  href: string;
  icon: React.ElementType;
  matchPrefix?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { label: "Kontrol Paneli", href: "/", icon: LayoutDashboard, matchPrefix: false },
  { label: "Adaylar ve Seviyeler", href: "/opportunities", icon: Target, matchPrefix: true },
  { label: "Piyasalar", href: "/markets", icon: BarChart3, matchPrefix: true },
  { label: "Tarayıcı", href: "/screener", icon: Filter, matchPrefix: true },
  { label: "İzleme Listesi", href: "/watchlist", icon: Eye, matchPrefix: true },
  { label: "Portföy", href: "/portfolio", icon: Briefcase, matchPrefix: true },
  { label: "Alarmlar", href: "/alerts", icon: Bell, matchPrefix: true },
  { label: "Ayarlar", href: "/settings", icon: Settings, matchPrefix: true },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { theme, toggleTheme } = useTheme();
  const { user, effectiveTimezone, logout } = useAuth();

  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [symbolInput, setSymbolInput] = useState("");
  const [health, setHealth] = useState<HealthResponse | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    apiServices.health
      .getHealth(controller.signal)
      .then(setHealth)
      .catch(() => {
        // Non-blocking status check.
      });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    setMobileNavOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!mobileNavOpen) {
      return;
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMobileNavOpen(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [mobileNavOpen]);

  const handleQuickSearch = (event: React.FormEvent) => {
    event.preventDefault();
    const cleaned = symbolInput.trim().toUpperCase();
    if (!cleaned) {
      return;
    }
    setSymbolInput("");
    router.push(`/instruments/${encodeURIComponent(cleaned)}`);
  };

  const handleLogout = () => {
    logout();
    router.push("/login");
  };

  const renderNavLinks = () => (
    <ul className="space-y-1">
      {NAV_ITEMS.map((item) => {
        const active = item.matchPrefix
          ? pathname === item.href || pathname.startsWith(`${item.href}/`)
          : pathname === item.href;
        const Icon = item.icon;
        return (
          <li key={item.href}>
            <Link
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                active
                  ? "bg-accent/15 text-accent"
                  : "text-muted hover:bg-surface-muted hover:text-foreground"
              )}
            >
              <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{item.label}</span>
            </Link>
          </li>
        );
      })}
    </ul>
  );

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:rounded-md focus:bg-accent focus:px-3 focus:py-2 focus:text-xs focus:font-semibold focus:text-accent-foreground"
      >
        Ana içeriğe atla
      </a>

      <header className="sticky top-0 z-30 border-b border-border bg-surface/95 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-[1440px] items-center justify-between gap-3 px-4 lg:px-6">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="icon"
              className="lg:hidden"
              onClick={() => setMobileNavOpen((prev) => !prev)}
              aria-label={mobileNavOpen ? "Gezinme menüsünü kapat" : "Gezinme menüsünü aç"}
              aria-expanded={mobileNavOpen}
              aria-controls="mobile-navigation-drawer"
            >
              {mobileNavOpen ? (
                <X className="h-5 w-5" aria-hidden="true" />
              ) : (
                <Menu className="h-5 w-5" aria-hidden="true" />
              )}
            </Button>

            <Link href="/" className="flex items-center gap-2 font-semibold tracking-tight">
              <span className="flex h-8 w-8 items-center justify-center rounded-md bg-accent/15 text-accent">
                <Activity className="h-4 w-4" aria-hidden="true" />
              </span>
              <span className="text-sm sm:text-base">StockVision AI</span>
            </Link>

            {health ? (
              <div className="hidden md:flex items-center gap-2">
                <ProviderModeBadge mode={health.dataProviderMode} />
              </div>
            ) : (
              <Badge variant="neutral" className="hidden md:inline-flex">
                <FlaskConical className="h-3 w-3" aria-hidden="true" />
                <span>Piyasa Terminali</span>
              </Badge>
            )}
          </div>

          <form
            onSubmit={handleQuickSearch}
            role="search"
            aria-label="Hızlı hisse sembolü arama"
            className="hidden sm:flex flex-1 max-w-xs items-center gap-1.5"
          >
            <div className="relative flex-1">
              <Search
                className="pointer-events-none absolute left-2.5 top-2.5 h-4 w-4 text-muted"
                aria-hidden="true"
              />
              <Input
                value={symbolInput}
                onChange={(e) => setSymbolInput(e.target.value)}
                placeholder="Sembol aç (ör. THYAO)"
                aria-label="Hisse sembolü aç"
                className="h-8 pl-8 text-xs uppercase placeholder:normal-case"
              />
            </div>
            <Button type="submit" size="sm" variant="secondary">
              Git
            </Button>
          </form>

          <div className="flex items-center gap-2">
            <span className="hidden xl:inline-flex text-xs text-muted">
              Saat Dilimi:{" "}
              <strong className="ml-1 font-medium text-foreground">{effectiveTimezone}</strong>
            </span>

            <Button
              variant="outline"
              size="icon"
              onClick={toggleTheme}
              aria-label={
                theme === "dark" ? "Açık temaya geç" : "Koyu temaya geç"
              }
              title={
                theme === "dark" ? "Açık temaya geç" : "Koyu temaya geç"
              }
            >
              {theme === "dark" ? (
                <Sun className="h-4 w-4" aria-hidden="true" />
              ) : (
                <Moon className="h-4 w-4" aria-hidden="true" />
              )}
            </Button>

            {user ? (
              <div className="flex items-center gap-2">
                <Link
                  href="/settings"
                  className="hidden sm:inline-flex rounded-md border border-border bg-surface-muted px-2.5 py-1 text-xs font-medium text-foreground hover:bg-border/60"
                >
                  {user.displayName || user.email}
                </Link>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleLogout}
                  aria-label="Çıkış Yap"
                >
                  <LogOut className="h-4 w-4" aria-hidden="true" />
                  <span className="hidden md:inline">Çıkış Yap</span>
                </Button>
              </div>
            ) : null}
          </div>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-[1440px] flex-1">
        <aside
          aria-label="Ana gezinme"
          className="hidden lg:block w-56 shrink-0 border-r border-border bg-surface/50 p-4"
        >
          <nav>{renderNavLinks()}</nav>
          <div className="mt-6 rounded-md border border-border bg-surface-muted/50 p-3 text-xs text-muted space-y-1.5">
            <p className="font-semibold text-foreground">BIST Odaklı Kapsam</p>
            <p className="leading-relaxed">
              Nicel teknik göstergeler ve açıklanabilir kural tabanlı skorlama. Yatırım danışmanlığı veya otomatik emir iletimi içermez.
            </p>
          </div>
        </aside>

        {mobileNavOpen ? (
          <div
            id="mobile-navigation-drawer"
            className="fixed inset-0 top-14 z-40 flex lg:hidden"
          >
            <div
              className="fixed inset-0 top-14 bg-black/50"
              onClick={() => setMobileNavOpen(false)}
              aria-hidden="true"
            />
            <nav
              aria-label="Mobil gezinme"
              className="relative z-10 flex w-64 flex-col gap-4 border-r border-border bg-surface p-4 shadow-xl"
            >
              <form onSubmit={handleQuickSearch} className="flex items-center gap-1.5">
                <Input
                  value={symbolInput}
                  onChange={(e) => setSymbolInput(e.target.value)}
                  placeholder="Sembol (THYAO)"
                  aria-label="Mobil hızlı sembol arama"
                  className="h-8 text-xs uppercase placeholder:normal-case"
                />
                <Button type="submit" size="sm" variant="secondary">
                  Git
                </Button>
              </form>
              {renderNavLinks()}
            </nav>
          </div>
        ) : null}

        <main
          id="main-content"
          className="flex-1 min-w-0 px-4 py-5 sm:px-6 lg:px-8 space-y-6"
        >
          {children}
        </main>
      </div>

      <footer className="border-t border-border bg-surface/70">
        <div className="mx-auto max-w-[1440px] space-y-3 px-4 py-4 lg:px-6">
          <DecisionSupportDisclaimer disclaimer={health?.disclaimer} />
          <div className="flex flex-col gap-1 text-xs text-muted sm:flex-row sm:items-center sm:justify-between">
            <span>StockVision AI · Yatırım Karar Destek Platformu</span>
            <span>
              Etkileşimli grafikler{" "}
              <a
                href="https://www.tradingview.com"
                target="_blank"
                rel="noopener noreferrer"
                className="underline hover:text-foreground"
              >
                TradingView Lightweight Charts™
              </a>{" "}
              (Apache Lisansı 2.0 ·{" "}
              <Link href="/settings#licenses" className="underline hover:text-foreground">
                Açık Kaynak Bildirimleri
              </Link>
              ) altyapısıyla sunulmaktadır
            </span>
          </div>
        </div>
      </footer>
    </div>
  );
}
