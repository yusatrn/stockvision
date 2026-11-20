"use client";

import React, { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle,
  Bot,
  CheckCircle2,
  Compass,
  Info,
  RefreshCw,
} from "lucide-react";
import { DecisionSupportDisclaimer } from "@/components/shared/data-status-badges";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiClientError } from "@/lib/api/client";
import { apiServices } from "@/lib/api/services";
import type { AiExplanationResponse } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import {
  translateAiProvider,
  translateAiStatus,
  translateFallbackReason,
  translateProviderMode,
} from "@/lib/labels";

interface AiExplanationCardProps {
  symbol: string;
  interval: string;
  adjusted: boolean;
  scoreRunId: string;
  timezone: string;
}

export function AiExplanationCard({
  symbol,
  interval,
  adjusted,
  scoreRunId,
  timezone,
}: AiExplanationCardProps) {
  const [data, setData] = useState<AiExplanationResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [regenerating, setRegenerating] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchExplanation = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setErrorMessage(null);
      try {
        const res = await apiServices.instruments.getAiExplanation(
          symbol,
          { interval, adjusted, scoreRunId },
          signal
        );
        setData(res);
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") {
          return;
        }
        setErrorMessage(
          isApiClientError(err)
            ? `${err.message} (${err.code})`
            : "Bu analiz koşusu için yapay zekâ açıklaması yüklenemedi."
        );
      } finally {
        setLoading(false);
      }
    },
    [symbol, interval, adjusted, scoreRunId]
  );

  useEffect(() => {
    const controller = new AbortController();
    fetchExplanation(controller.signal);
    return () => controller.abort();
  }, [fetchExplanation]);

  const handleRegenerate = async () => {
    setRegenerating(true);
    setErrorMessage(null);
    try {
      const res = await apiServices.instruments.regenerateAiExplanation(symbol, {
        interval,
        adjusted,
        scoreRunId,
      });
      setData(res);
    } catch (err) {
      setErrorMessage(
        isApiClientError(err)
          ? `${err.message} (${err.code})`
          : "Yapay zekâ açıklaması yeniden üretilemedi."
      );
    } finally {
      setRegenerating(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Bot className="h-4 w-4 text-accent" aria-hidden="true" />
              <span>Doğal Dil Açıklaması · {symbol}</span>
            </CardTitle>
            <CardDescription>
              Gözlenen metrikleri, teknik yorumu ve veri belirsizliklerini net şekilde ayıran kanıta dayalı özet.
            </CardDescription>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {data ? (
              <Badge
                variant={
                  data.status === "INSUFFICIENT_DATA"
                    ? "warning"
                    : data.fallbackUsed
                    ? "info"
                    : "positive"
                }
              >
                {translateAiStatus(data.status)}
              </Badge>
            ) : null}
            <Button
              variant="outline"
              size="sm"
              onClick={handleRegenerate}
              isLoading={regenerating}
              disabled={loading}
            >
              <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
              <span>Yeniden Üret</span>
            </Button>
          </div>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {loading && !data ? (
          <div className="space-y-3" aria-busy="true" aria-label="Açıklama yükleniyor">
            <Skeleton className="h-14 w-full" />
            <div className="grid gap-3 md:grid-cols-3">
              <Skeleton className="h-28" />
              <Skeleton className="h-28" />
              <Skeleton className="h-28" />
            </div>
          </div>
        ) : null}

        {errorMessage ? (
          <Alert variant="error" title="Açıklama Kullanılamıyor">
            <p>{errorMessage}</p>
            <div className="mt-2">
              <Button variant="outline" size="sm" onClick={() => fetchExplanation()}>
                Tekrar Dene
              </Button>
            </div>
          </Alert>
        ) : null}

        {data ? (
          <>
            {data.fallbackUsed ? (
              <Alert variant="info" title="Deterministik Şablon Yedeği Etkin">
                Harici yapay zekâ sağlayıcısı ({translateAiProvider(data.requestedProviderId)}) yedekleme gerekçesi döndü:{" "}
                <strong>{translateFallbackReason(data.fallbackReason)}</strong>. Yalnızca kalıcı gerçek verilerden üretilen deterministik şablon metni gösteriliyor.
              </Alert>
            ) : null}

            {data.status === "INSUFFICIENT_DATA" ? (
              <Alert variant="warning" title="Yetersiz Veri (INSUFFICIENT_DATA) Güvenlik Kuralı">
                Altta yatan nicel veriler eksik veya bayat olduğu için harici LLM çağrıları durduruldu.
              </Alert>
            ) : null}

            <div className="rounded-md border border-border bg-surface-muted/40 p-4 space-y-1.5">
              <p className="text-sm font-medium leading-relaxed text-foreground">
                {data.explanation.summary}
              </p>
              <p className="text-xs text-muted">{data.explanation.confidenceNote}</p>
            </div>

            <div className="grid gap-4 lg:grid-cols-3">
              <div className="space-y-2 rounded-md border border-border bg-surface-muted/20 p-3.5">
                <h3 className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted">
                  <CheckCircle2 className="h-3.5 w-3.5 text-positive" aria-hidden="true" />
                  <span>Gözlenen Gerçekler</span>
                </h3>
                <ul className="space-y-1.5 text-xs leading-relaxed text-foreground/90">
                  {data.explanation.observedFacts.map((item) => (
                    <li
                      key={item}
                      className="rounded border border-border/60 bg-surface px-2.5 py-1.5"
                    >
                      {item}
                    </li>
                  ))}
                </ul>
              </div>

              <div className="space-y-2 rounded-md border border-border bg-surface-muted/20 p-3.5">
                <h3 className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted">
                  <Compass className="h-3.5 w-3.5 text-accent" aria-hidden="true" />
                  <span>Teknik Yorum</span>
                </h3>
                <ul className="space-y-1.5 text-xs leading-relaxed text-foreground/90">
                  {data.explanation.interpretation.map((item) => (
                    <li
                      key={item}
                      className="rounded border border-border/60 bg-surface px-2.5 py-1.5"
                    >
                      {item}
                    </li>
                  ))}
                </ul>
              </div>

              <div className="space-y-2 rounded-md border border-border bg-surface-muted/20 p-3.5">
                <h3 className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted">
                  <AlertTriangle className="h-3.5 w-3.5 text-warning" aria-hidden="true" />
                  <span>Belirsizlikler ve Riskler</span>
                </h3>
                <ul className="space-y-1.5 text-xs leading-relaxed text-foreground/90">
                  {data.explanation.uncertaintiesAndRisks.map((item) => (
                    <li
                      key={item}
                      className="rounded border border-border/60 bg-surface px-2.5 py-1.5"
                    >
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-md border border-border bg-surface-muted/40 px-3 py-2 text-[11px] text-muted">
              <span className="inline-flex items-center gap-1">
                <Info className="h-3 w-3 text-accent" aria-hidden="true" />
                Sağlayıcı: <strong className="text-foreground">{translateAiProvider(data.providerId)}</strong>
              </span>
              <span>
                Model: <strong className="text-foreground">{data.modelName}</strong>
              </span>
              <span>
                İstem (Prompt): <strong className="text-foreground">{data.promptVersion}</strong>
              </span>
              <span className="font-mono">Koşu: {data.analysisRunId}</span>
              <span>
                Üretim:{" "}
                <strong className="text-foreground">
                  {formatDateTime(data.generatedAt, timezone)}
                </strong>
              </span>
            </div>

            <DecisionSupportDisclaimer disclaimer={data.disclaimer} />
          </>
        ) : null}
      </CardContent>

      {data ? (
        <CardFooter>
          <span>Skor Koşusu Kimliği: {data.scoreRunId}</span>
          <span>
            Mod: {data.simulatedData ? "Simüle Akış" : translateProviderMode(data.dataSource.mode)}
          </span>
        </CardFooter>
      ) : null}
    </Card>
  );
}
