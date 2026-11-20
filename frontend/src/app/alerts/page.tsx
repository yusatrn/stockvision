"use client";

import React, { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { AuthGate } from "@/components/layout/auth-gate";
import { AlertsView } from "@/components/alerts/alerts-view";

function AlertsContent() {
  const searchParams = useSearchParams();
  const initialSymbol = searchParams.get("symbol") ?? undefined;
  return <AlertsView initialSymbol={initialSymbol} />;
}

export default function AlertsPage() {
  return (
    <AuthGate>
      <Suspense fallback={<div className="p-6 text-sm text-muted">Alarmlar yükleniyor...</div>}>
        <AlertsContent />
      </Suspense>
    </AuthGate>
  );
}
