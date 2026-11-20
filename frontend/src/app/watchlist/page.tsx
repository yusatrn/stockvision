import { AuthGate } from "@/components/layout/auth-gate";
import { WatchlistView } from "@/components/watchlist/watchlist-view";

export default function WatchlistPage() {
  return (
    <AuthGate>
      <WatchlistView />
    </AuthGate>
  );
}
