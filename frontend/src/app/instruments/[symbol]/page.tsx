import { AuthGate } from "@/components/layout/auth-gate";
import { InstrumentDetailView } from "@/components/instruments/instrument-detail-view";

export default async function InstrumentDetailPage({
  params,
}: {
  params: Promise<{ symbol: string }>;
}) {
  const { symbol } = await params;
  const normalizedSymbol = decodeURIComponent(symbol).toUpperCase();

  return (
    <AuthGate>
      <InstrumentDetailView symbol={normalizedSymbol} />
    </AuthGate>
  );
}
