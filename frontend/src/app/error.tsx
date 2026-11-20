"use client";

import React from "react";
import Link from "next/link";
import { AlertTriangle, RotateCcw } from "lucide-react";
import { Button, buttonClasses } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export default function GlobalErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-6">
      <Card className="w-full max-w-lg">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-negative">
            <AlertTriangle className="h-5 w-5" aria-hidden="true" />
            <span>Beklenmeyen Bir Hata Oluştu</span>
          </CardTitle>
          <CardDescription>
            Arayüzde beklenmeyen bir hata oluştu. Hiçbir finansal işlem gerçekleştirilmedi.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4 text-sm text-muted">
          <p>
            Lütfen görünümü yeniden yüklemeyi deneyin veya ana kontrol paneline dönün.
            {error?.digest ? ` Referans Kimliği: ${error.digest}` : ""}
          </p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" size="sm" onClick={() => reset()}>
              <RotateCcw className="h-4 w-4" aria-hidden="true" />
              <span>Tekrar Dene</span>
            </Button>
            <Link
              href="/"
              className={buttonClasses({ variant: "outline", size: "sm" })}
            >
              Kontrol Paneline Dön
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
