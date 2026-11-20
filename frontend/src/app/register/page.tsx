"use client";

import React, { useState } from "react";
import Link from "next/link";
import { Activity, Moon, Sun, UserPlus } from "lucide-react";
import { useAuth } from "@/components/providers/auth-provider";
import { useTheme } from "@/components/providers/theme-provider";
import { DecisionSupportDisclaimer } from "@/components/shared/data-status-badges";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input, Label, Select } from "@/components/ui/input";
import { isApiClientError } from "@/lib/api/client";
import { DEFAULT_TIMEZONE } from "@/lib/format";

const COMMON_TIMEZONES = [
  "Europe/Istanbul",
  "UTC",
  "Europe/London",
  "Europe/Berlin",
  "America/New_York",
];

export default function RegisterPage() {
  const { register } = useAuth();
  const { theme, toggleTheme } = useTheme();

  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [timezone, setTimezone] = useState(DEFAULT_TIMEZONE);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<string[]>([]);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMessage(null);
    setFieldErrors([]);
    setSuccessMessage(null);

    if (!email.trim() || !password) {
      setErrorMessage("E-posta adresi ve parola zorunludur.");
      return;
    }

    setSubmitting(true);
    try {
      const response = await register({
        email: email.trim(),
        password,
        displayName: displayName.trim() || undefined,
        timezone,
      });
      setSuccessMessage(
        response.message ||
          "Kayıt talebiniz alındı. Bilgilerinizle şimdi giriş yapabilirsiniz."
      );
      setPassword("");
    } catch (err) {
      if (isApiClientError(err)) {
        setErrorMessage(err.message);
        if (err.fieldErrors && err.fieldErrors.length > 0) {
          setFieldErrors(err.fieldErrors.map((f) => `${f.field}: ${f.message}`));
        }
      } else {
        setErrorMessage("Beklenmeyen bir hata nedeniyle kayıt oluşturulamadı.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-background px-4 py-10 flex flex-col items-center justify-center">
      <div className="mb-6 flex w-full max-w-md items-center justify-between">
        <div className="flex items-center gap-2 font-semibold">
          <span className="flex h-8 w-8 items-center justify-center rounded-md bg-accent/15 text-accent">
            <Activity className="h-4 w-4" aria-hidden="true" />
          </span>
          <span>StockVision AI</span>
        </div>
        <Button
          variant="outline"
          size="icon"
          onClick={toggleTheme}
          aria-label={theme === "dark" ? "Açık temaya geç" : "Koyu temaya geç"}
        >
          {theme === "dark" ? (
            <Sun className="h-4 w-4" aria-hidden="true" />
          ) : (
            <Moon className="h-4 w-4" aria-hidden="true" />
          )}
        </Button>
      </div>

      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-lg">Hesap Oluştur</CardTitle>
          <CardDescription>
            StockVision AI karar destek analizleri için kaydolun. Parolalar sunucu tarafında güçlü özetlemeyle saklanır.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {successMessage ? (
            <Alert variant="positive" title="Talep Alındı">
              <p>{successMessage}</p>
              <p className="mt-2">
                <Link href="/login" className="font-semibold text-accent underline">
                  Giriş Sayfasına Geç
                </Link>
              </p>
            </Alert>
          ) : null}

          {errorMessage ? (
            <Alert variant="error" title="Kayıt Hatası">
              <p>{errorMessage}</p>
              {fieldErrors.length > 0 ? (
                <ul className="mt-1 list-disc pl-4">
                  {fieldErrors.map((detail) => (
                    <li key={detail}>{detail}</li>
                  ))}
                </ul>
              ) : null}
            </Alert>
          ) : null}

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-1.5">
              <Label htmlFor="reg-email">E-posta Adresi</Label>
              <Input
                id="reg-email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="analist@ornek.com"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="reg-display-name">Görünen Ad (isteğe bağlı)</Label>
              <Input
                id="reg-display-name"
                type="text"
                autoComplete="name"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="Piyasa Analisti"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="reg-password">Parola</Label>
              <Input
                id="reg-password"
                type="password"
                autoComplete="new-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Harf ve rakam içeren en az 10 karakter"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="reg-timezone">Görüntüleme Saat Dilimi</Label>
              <Select
                id="reg-timezone"
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
              >
                {COMMON_TIMEZONES.map((tz) => (
                  <option key={tz} value={tz}>
                    {tz}
                  </option>
                ))}
              </Select>
            </div>

            <Button type="submit" className="w-full" isLoading={submitting}>
              <UserPlus className="h-4 w-4" aria-hidden="true" />
              <span>Hesap Oluştur</span>
            </Button>
          </form>

          <p className="text-center text-xs text-muted">
            Zaten hesabınız var mı?{" "}
            <Link href="/login" className="font-medium text-accent hover:underline">
              Giriş yapın
            </Link>
          </p>
        </CardContent>
      </Card>

      <div className="mt-6 w-full max-w-md">
        <DecisionSupportDisclaimer />
      </div>
    </div>
  );
}
