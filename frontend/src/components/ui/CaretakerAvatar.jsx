import { useState } from 'react';

const SIZES = {
  sm: 'h-12 w-12 text-sm',
  md: 'h-16 w-16 text-lg',
  lg: 'h-20 w-20 text-xl',
};

function getInitials(name) {
  if (!name) return 'CB';
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function CaretakerAvatar({ src, name, size = 'md', showDot = false, available = false }) {
  const [failedSrc, setFailedSrc] = useState(null);
  const sizeClass = SIZES[size] || SIZES.md;
  const showImage = Boolean(src && failedSrc !== src);

  return (
    <div className="relative inline-flex shrink-0">
      {showImage ? (
        <img
          src={src}
          alt={`${name || 'Caretaker'} profile photo`}
          onError={() => setFailedSrc(src)}
          className={`${sizeClass} rounded-2xl border border-slate-200 object-cover bg-slate-100`}
        />
      ) : (
        <div
          className={`${sizeClass} flex items-center justify-center rounded-2xl bg-teal-700 font-bold text-white shadow-xs`}
        >
          {getInitials(name)}
        </div>
      )}

      {showDot && (
        <span
          aria-label={available ? 'Available' : 'Not available'}
          className={`absolute -bottom-1 -right-1 h-4 w-4 rounded-full border-2 border-white ${
            available ? 'bg-emerald-500' : 'bg-slate-400'
          }`}
        />
      )}
    </div>
  );
}
