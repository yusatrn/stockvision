import { AuthGate } from "@/components/layout/auth-gate";
import { ScreenerView } from "@/components/screener/screener-view";

export default function ScreenerPage() {
  return (
    <AuthGate>
      <ScreenerView />
    </AuthGate>
  );
}
