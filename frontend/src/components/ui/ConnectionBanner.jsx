import { RefreshCw, WifiOff } from 'lucide-react';
import { useWebSocket } from '../../context/useWebSocket';

export function ConnectionBanner() {
  const { connectionStatus } = useWebSocket();

  if (connectionStatus === 'CONNECTED' || connectionStatus === 'CONNECTING') {
    return null;
  }

  const isReconnecting = connectionStatus === 'RECONNECTING';

  return (
    <div
      role="status"
      aria-live="polite"
      className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-xs font-medium text-amber-950"
    >
      <div className="mx-auto flex max-w-5xl items-center justify-center gap-2">
        {isReconnecting ? (
          <RefreshCw className="h-3.5 w-3.5 shrink-0 animate-spin text-amber-700" aria-hidden="true" />
        ) : (
          <WifiOff className="h-3.5 w-3.5 shrink-0 text-amber-700" aria-hidden="true" />
        )}
        <span>
          {isReconnecting
            ? 'Live updates reconnecting — your messages remain saved and will sync automatically.'
            : 'Real-time connection paused — retrying automatically.'}
        </span>
      </div>
    </div>
  );
}
