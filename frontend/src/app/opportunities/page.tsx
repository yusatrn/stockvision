import { AuthGate } from "@/components/layout/auth-gate";
import { OpportunitiesView } from "@/components/opportunities/opportunities-view";

export default function OpportunitiesPage() {
  return (
    <AuthGate>
      <OpportunitiesView />
    </AuthGate>
  );
}
