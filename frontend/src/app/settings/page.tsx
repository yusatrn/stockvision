import { AuthGate } from "@/components/layout/auth-gate";
import { SettingsView } from "@/components/settings/settings-view";

export default function SettingsPage() {
  return (
    <AuthGate>
      <SettingsView />
    </AuthGate>
  );
}
