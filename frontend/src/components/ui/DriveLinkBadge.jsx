import { CheckCircle2, Clock, ExternalLink, RefreshCw, ShieldAlert } from 'lucide-react';

export function DriveLinkBadge({ url, reachable, note, onRecheck, rechecking = false }) {
  let stateClass = 'border-amber-200 bg-amber-50 text-amber-900';
  let statusText = 'Checking Drive access...';
  let StatusIcon = Clock;

  if (reachable === true) {
    stateClass = 'border-emerald-200 bg-emerald-50 text-emerald-900';
    statusText = note || 'Publicly reachable';
    StatusIcon = CheckCircle2;
  } else if (reachable === false) {
    stateClass = 'border-rose-200 bg-rose-50 text-rose-900';
    statusText = note || 'Link appears private or unreachable';
    StatusIcon = ShieldAlert;
  }

  const isSafeDriveUrl =
    typeof url === 'string' && /^https:\/\/(drive|docs)\.google\.com\//i.test(url);

  return (
    <div className="space-y-2 rounded-xl border border-slate-200 bg-slate-50/70 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        {isSafeDriveUrl ? (
          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex min-h-[44px] max-w-full items-center gap-1.5 text-sm font-semibold text-teal-700 underline-offset-2 hover:text-teal-900 hover:underline"
          >
            <ExternalLink className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span className="truncate">Open Google Drive Documents</span>
          </a>
        ) : (
          <span className="text-xs font-semibold text-rose-700">Invalid document URL</span>
        )}

        {onRecheck && (
          <button
            type="button"
            onClick={onRecheck}
            disabled={rechecking}
            className="inline-flex min-h-[44px] items-center gap-1 rounded-lg border border-slate-300 bg-white px-3 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-60"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${rechecking ? 'animate-spin' : ''}`} />
            <span>Re-check</span>
          </button>
        )}
      </div>

      <div className={`flex items-start gap-2 rounded-lg border px-2.5 py-1.5 text-xs font-medium ${stateClass}`}>
        <StatusIcon className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span>{statusText}</span>
      </div>
    </div>
  );
}
