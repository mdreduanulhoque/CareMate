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
