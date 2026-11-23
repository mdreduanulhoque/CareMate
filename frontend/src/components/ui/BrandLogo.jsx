import { HeartHandshake } from 'lucide-react';

export function BrandLogo({ compact = false }) {
  return (
    <div className="inline-flex items-center gap-2.5 select-none">
      <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-700 text-white shadow-sm">
        <HeartHandshake className="h-5 w-5" strokeWidth={2.2} />
      </div>
      {!compact && (
        <div className="leading-tight">
          <div className="text-lg font-bold tracking-tight text-slate-900">CareBridge</div>
          <div className="text-[11px] font-medium text-teal-700">Verified Hospital Care</div>
        </div>
      )}
    </div>
  );
}
