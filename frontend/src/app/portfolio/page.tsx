import { AuthGate } from "@/components/layout/auth-gate";
import { PortfolioView } from "@/components/portfolio/portfolio-view";

export default function PortfolioPage() {
  return (
    <AuthGate>
      <PortfolioView />
    </AuthGate>
  );
}
