const BADGE_STYLES = {
  CUSTOMER: 'bg-teal-100 text-teal-800 border-teal-200',
  CARETAKER: 'bg-sky-100 text-sky-800 border-sky-200',
  ADMIN: 'bg-indigo-100 text-indigo-800 border-indigo-200',
  PENDING: 'bg-amber-100 text-amber-900 border-amber-200',
  ACTIVE: 'bg-emerald-100 text-emerald-800 border-emerald-200',
};

const LABELS = {
  CUSTOMER: 'Patient Relative',
  CARETAKER: 'Caretaker / Nurse',
  ADMIN: 'Administrator',
};

export function StatusBadge({ status, label }) {
  const style = BADGE_STYLES[status] || 'bg-slate-100 text-slate-700 border-slate-200';
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${style}`}
    >
      {label || LABELS[status] || status}
    </span>
  );
}
