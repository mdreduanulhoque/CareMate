import { AlertCircle, CheckCircle2, Info, ShieldAlert } from 'lucide-react';

const STYLES = {
  error: {
    container: 'border-rose-200 bg-rose-50 text-rose-800',
    Icon: AlertCircle,
  },
  warning: {
    container: 'border-amber-200 bg-amber-50 text-amber-900',
    Icon: ShieldAlert,
  },
  success: {
    container: 'border-emerald-200 bg-emerald-50 text-emerald-900',
    Icon: CheckCircle2,
  },
  info: {
    container: 'border-teal-200 bg-teal-50 text-teal-900',
    Icon: Info,
  },
};

export function AlertBanner({ variant = 'info', title, children, className = '' }) {
  const { container, Icon } = STYLES[variant] || STYLES.info;

  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={`flex items-start gap-3 rounded-xl border p-3.5 text-sm ${container} ${className}`}
    >
      <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1 space-y-0.5">
        {title && <p className="font-semibold">{title}</p>}
        <div className="leading-relaxed">{children}</div>
      </div>
    </div>
  );
}
