"use client";

import React, { useState } from "react";
import { CheckCircle2, Moon, RefreshCw, Scale, Sun, User } from "lucide-react";
import { useAuth } from "@/components/providers/auth-provider";
import { useTheme } from "@/components/providers/theme-provider";
import { DecisionSupportDisclaimer } from "@/components/shared/data-status-badges";
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
import { Label, Select } from "@/components/ui/input";
import { isApiClientError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";

const TIMEZONE_CHOICES = [
  "Europe/Istanbul",
  "UTC",
  "Europe/London",
  "Europe/Berlin",
  "America/New_York",
  "Asia/Tokyo",
];

export function SettingsView() {
  const { user, effectiveTimezone, setDisplayTimezone, refreshProfile } = useAuth();
  const { theme, setTheme } = useTheme();
  const [refreshing, setRefreshing] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleRefreshProfile = async () => {
    setRefreshing(true);
    setStatusMessage(null);
    setErrorMessage(null);
    try {
      await refreshProfile();
      setStatusMessage("Profil bilgileri /api/v1/users/me üzerinden senkronize edildi.");
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "Kullanıcı profili yenilenemedi."
      );
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold tracking-tight sm:text-2xl">
          Çalışma Alanı Ayarları ve Lisans Uyumluluğu
        </h1>
        <p className="text-sm text-muted">
          Görüntüleme saat dilimini, arayüz temasını, hesap profilinizi ve üçüncü taraf açık kaynak bildirimlerini yönetin.
        </p>
      </div>

      {statusMessage ? (
        <Alert variant="positive" title="Güncellendi">
          {statusMessage}
        </Alert>
      ) : null}

      {errorMessage ? (
        <Alert variant="error" title="Senkronizasyon Hatası">
          {errorMessage}
        </Alert>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between gap-2">
              <CardTitle className="flex items-center gap-2">
                <User className="h-4 w-4 text-accent" aria-hidden="true" />
                <span>Doğrulanmış Profil</span>
              </CardTitle>
              <Button
                variant="outline"
                size="sm"
                onClick={handleRefreshProfile}
                isLoading={refreshing}
              >
                <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
                <span>Profili Senkronize Et</span>
              </Button>
            </div>
            <CardDescription>
              Doğrudan `/api/v1/users/me` üzerinden alınan hesap bilgileri.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {user ? (
              <dl className="grid grid-cols-1 gap-3 text-xs sm:grid-cols-2">
                <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                  <dt className="text-muted">E-posta</dt>
                  <dd className="mt-0.5 font-semibold break-all">{user.email}</dd>
                </div>
                <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                  <dt className="text-muted">Görünen Ad</dt>
                  <dd className="mt-0.5 font-semibold">{user.displayName || "Belirtilmedi"}</dd>
                </div>
                <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                  <dt className="text-muted">Rol ve Hesap Durumu</dt>
                  <dd className="mt-1 flex items-center gap-1.5">
                    <Badge variant="neutral">
                      {user.role === "ADMIN" ? "YÖNETİCİ" : "KULLANICI"}
                    </Badge>
                    <Badge variant={user.status === "ACTIVE" ? "positive" : "warning"}>
                      <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
                      <span>{user.status === "ACTIVE" ? "AKTİF" : user.status}</span>
                    </Badge>
                  </dd>
                </div>
                <div className="rounded-md border border-border bg-surface-muted/40 p-3">
                  <dt className="text-muted">Profil Saat Dilimi</dt>
                  <dd className="mt-0.5 font-semibold">{user.timezone}</dd>
                </div>
                <div className="rounded-md border border-border bg-surface-muted/40 p-3 sm:col-span-2">
                  <dt className="text-muted">Üyelik Tarihi</dt>
                  <dd className="mt-0.5 font-semibold tabular-nums">
                    {formatDateTime(user.createdAt, effectiveTimezone)}
                  </dd>
                </div>
              </dl>
            ) : (
              <p className="text-sm text-muted">Profil bilgileri kullanılamıyor.</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Görünüm ve Saat Dilimi Tercihleri</CardTitle>
            <CardDescription>
              UTC API zaman damgaları tarayıcıda seçtiğiniz saat dilimine dinamik olarak çevrilir.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="space-y-2">
              <Label>Renk Teması</Label>
              <div className="flex gap-2" role="group" aria-label="Renk teması seçimi">
                <Button
                  variant={theme === "dark" ? "primary" : "outline"}
                  size="sm"
                  onClick={() => setTheme("dark")}
                  aria-pressed={theme === "dark"}
                >
                  <Moon className="h-4 w-4" aria-hidden="true" />
                  <span>Koyu Terminal</span>
                </Button>
                <Button
                  variant={theme === "light" ? "primary" : "outline"}
                  size="sm"
                  onClick={() => setTheme("light")}
                  aria-pressed={theme === "light"}
                >
                  <Sun className="h-4 w-4" aria-hidden="true" />
                  <span>Açık Tema</span>
                </Button>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="settings-timezone">Görüntüleme Saat Dilimi Geçersiz Kılma</Label>
              <div className="flex flex-wrap items-center gap-2">
                <Select
                  id="settings-timezone"
                  value={
                    TIMEZONE_CHOICES.includes(effectiveTimezone)
                      ? effectiveTimezone
                      : TIMEZONE_CHOICES[0]
                  }
                  onChange={(e) => {
                    setDisplayTimezone(e.target.value);
                    setStatusMessage(`Görüntüleme saat dilimi ${e.target.value} olarak güncellendi.`);
                  }}
                  className="max-w-xs"
                >
                  {TIMEZONE_CHOICES.map((tz) => (
                    <option key={tz} value={tz}>
                      {tz}
                    </option>
                  ))}
                </Select>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setDisplayTimezone("");
                    setStatusMessage("Görüntüleme saat dilimi profil varsayılanına sıfırlandı.");
                  }}
                >
                  Varsayılana Sıfırla
                </Button>
              </div>
              <p className="text-xs text-muted">
                Etkin arayüz saat dilimi: <strong className="text-foreground">{effectiveTimezone}</strong>
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card id="licenses">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Scale className="h-4 w-4 text-accent" aria-hidden="true" />
            <span>Açık Kaynak Lisansları ve TradingView Atfı</span>
          </CardTitle>
          <CardDescription>
            Dahil edilen grafik kütüphaneleri için zorunlu Apache 2.0 Lisansı ve NOTICE bildirimleri.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4 text-xs leading-relaxed text-muted">
          <div className="rounded-md border border-border bg-surface-muted/40 p-4 space-y-2">
            <p className="font-semibold text-foreground">
              TradingView Lightweight Charts™ (Apache Lisansı, Sürüm 2.0)
            </p>
            <p>
              StockVision AI içindeki etkileşimli finansal mum ve indikatör grafikleri,{" "}
              <a
                href="https://www.tradingview.com"
                target="_blank"
                rel="noopener noreferrer"
                className="font-medium text-accent underline"
              >
                TradingView, Inc. (https://www.tradingview.com)
              </a>{" "}
              tarafından geliştirilen{" "}
              <code className="rounded bg-surface px-1 py-0.5 text-foreground">
                lightweight-charts
              </code>{" "}
              kütüphanesini kullanır.
            </p>
            <p>
              Apache Lisansı 2.0 kapsamında lisanslanmıştır. TradingView NOTICE gereksinimleri uyarınca grafiklerde atıf logosu etkin tutulur ve grafik görünümleri ile lisans bildirimlerinde görünür bağlantı atfı korunur.
            </p>
          </div>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <div className="rounded-md border border-border p-3">
              <p className="font-semibold text-foreground">Next.js ve React</p>
              <p>MIT Lisansı · Uygulama çatısı ve arayüz çalışma zamanı.</p>
            </div>
            <div className="rounded-md border border-border p-3">
              <p className="font-semibold text-foreground">Tailwind CSS</p>
              <p>MIT Lisansı · Yardımcı sınıf tabanlı stil sistemi.</p>
            </div>
            <div className="rounded-md border border-border p-3">
              <p className="font-semibold text-foreground">Lucide Icons</p>
              <p>ISC Lisansı · Erişilebilir SVG ikon seti.</p>
            </div>
          </div>
        </CardContent>
      </Card>

      <DecisionSupportDisclaimer />
    </div>
  );
}
