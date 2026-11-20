import { AuthGate } from "@/components/layout/auth-gate";
import { MarketsView } from "@/components/markets/markets-view";

export default function MarketsPage() {
  return (
    <AuthGate>
      <MarketsView />
    </AuthGate>
  );
}
