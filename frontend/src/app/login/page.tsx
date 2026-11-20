"use client";

import React, { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Activity, LogIn, Moon, Sun } from "lucide-react";
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
import { Input, Label } from "@/components/ui/input";
import { isApiClientError } from "@/lib/api/client";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login, sessionNotice, clearSessionNotice } = useAuth();
  const { theme, toggleTheme } = useTheme();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [errorDetails, setErrorDetails] = useState<string[]>([]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    clearSessionNotice();
    setErrorMessage(null);
    setErrorDetails([]);

    if (!email.trim() || !password) {
      setErrorMessage("Lütfen hem e-posta adresinizi hem parolanızı girin.");
      return;
    }

    setSubmitting(true);
    try {
      await login(email.trim(), password);
      const next = searchParams.get("next");
      const target = next && next.startsWith("/") ? next : "/";
      router.replace(target);
    } catch (err) {
      if (isApiClientError(err)) {
        setErrorMessage(err.message);
        if (err.fieldErrors && err.fieldErrors.length > 0) {
          setErrorDetails(err.fieldErrors.map((f) => `${f.field}: ${f.message}`));
        }
      } else {
        setErrorMessage("Beklenmeyen bir hata nedeniyle giriş yapılamadı.");
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
          <CardTitle className="text-lg">StockVision AI Girişi</CardTitle>
          <CardDescription>
            Borsa İstanbul öncelikli piyasa özetlerine, teknik analiz grafiklerine ve açıklanabilir skorlara erişin.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {sessionNotice ? (
            <Alert variant="warning" title="Oturum Bildirimi">
              {sessionNotice}
            </Alert>
          ) : null}

          {errorMessage ? (
            <Alert variant="error" title="Giriş Başarısız">
              <p>{errorMessage}</p>
              {errorDetails.length > 0 ? (
                <ul className="mt-1 list-disc pl-4">
                  {errorDetails.map((detail) => (
                    <li key={detail}>{detail}</li>
                  ))}
                </ul>
              ) : null}
            </Alert>
          ) : null}

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-1.5">
              <Label htmlFor="login-email">E-posta Adresi</Label>
              <Input
                id="login-email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="analist@ornek.com"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="login-password">Parola</Label>
              <Input
                id="login-password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Parolanızı girin"
              />
            </div>

            <Button type="submit" className="w-full" isLoading={submitting}>
              <LogIn className="h-4 w-4" aria-hidden="true" />
              <span>Giriş Yap</span>
            </Button>
          </form>

          <p className="text-center text-xs text-muted">
            Hesabınız yok mu?{" "}
            <Link href="/register" className="font-medium text-accent hover:underline">
              Yeni hesap oluşturun
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

export default function LoginPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-background" />}>
      <LoginForm />
    </Suspense>
  );
}
