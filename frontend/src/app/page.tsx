import { AuthGate } from "@/components/layout/auth-gate";
import { DashboardView } from "@/components/dashboard/dashboard-view";

export default function HomePage() {
  return (
    <AuthGate>
      <DashboardView />
    </AuthGate>
  );
}
