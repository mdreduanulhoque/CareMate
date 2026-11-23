const DHAKA_TIMEZONE = 'Asia/Dhaka';

const dhakaDayFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: DHAKA_TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const dhakaTimeFormatter = new Intl.DateTimeFormat('en-BD', {
  timeZone: DHAKA_TIMEZONE,
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
});

const dhakaDateTimeFormatter = new Intl.DateTimeFormat('en-BD', {
  timeZone: DHAKA_TIMEZONE,
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
});

const dhakaDateTimePartsFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: DHAKA_TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

export function formatDhakaChatTime(isoString) {
  if (!isoString) {
    return '';
  }
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const isSameDhakaDay = dhakaDayFormatter.format(date) === dhakaDayFormatter.format(new Date());
  return isSameDhakaDay ? dhakaTimeFormatter.format(date) : dhakaDateTimeFormatter.format(date);
}

export function formatDhakaDateTime(isoString) {
  if (!isoString) {
    return '—';
  }
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return dhakaDateTimeFormatter.format(date);
}

export function formatDhakaShiftRange(startIso, endIso) {
  if (!startIso || !endIso) {
    return '—';
  }
  const start = new Date(startIso);
  const end = new Date(endIso);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return '—';
  }
  const sameDay = dhakaDayFormatter.format(start) === dhakaDayFormatter.format(end);
  if (sameDay) {
    return `${dhakaDateTimeFormatter.format(start)} – ${dhakaTimeFormatter.format(end)}`;
  }
  return `${dhakaDateTimeFormatter.format(start)} → ${dhakaDateTimeFormatter.format(end)}`;
}

export function getTodayDhakaDateString() {
  return dhakaDayFormatter.format(new Date());
}

/**
 * Formats an Instant/Date into a YYYY-MM-DDTHH:mm string in Asia/Dhaka for datetime-local inputs.
 */
export function toDhakaDateTimeInput(date) {
  const parts = dhakaDateTimePartsFormatter.formatToParts(date);
  const map = {};
  parts.forEach((p) => {
    map[p.type] = p.value;
  });
  const hour = map.hour === '24' ? '00' : map.hour;
  return `${map.year}-${map.month}-${map.day}T${hour}:${map.minute}`;
}

/**
 * Interprets a datetime-local value (YYYY-MM-DDTHH:mm) strictly in Asia/Dhaka (UTC+06:00)
 * and converts it to a UTC ISO-8601 Instant string for the backend API.
 */
export function dhakaInputToUtcIso(localDateTimeValue) {
  if (!localDateTimeValue || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(localDateTimeValue)) {
    return null;
  }
  const parsed = new Date(`${localDateTimeValue}:00+06:00`);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }
  return parsed.toISOString();
}

export function formatDurationLabel(startIso, endIso) {
  if (!startIso || !endIso) {
    return '';
  }
  const diffMs = new Date(endIso).getTime() - new Date(startIso).getTime();
  if (!Number.isFinite(diffMs) || diffMs <= 0) {
    return '';
  }
  const totalMinutes = Math.round(diffMs / 60000);
  const days = Math.floor(totalMinutes / (24 * 60));
  const hours = Math.floor((totalMinutes % (24 * 60)) / 60);
  const minutes = totalMinutes % 60;
  const pieces = [];
  if (days > 0) pieces.push(`${days} day${days > 1 ? 's' : ''}`);
  if (hours > 0) pieces.push(`${hours} hr${hours > 1 ? 's' : ''}`);
  if (minutes > 0 && days === 0) pieces.push(`${minutes} min`);
  return pieces.join(' ');
}
