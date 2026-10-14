import { Globe2 } from 'lucide-react';
import { useState } from 'react';

const normalizeCountry = (value: string) =>
  value
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]/g, '')
    .toLowerCase();

const countryNames = (() => {
  const names = new Map<string, string>();
  if (typeof Intl.DisplayNames !== 'function') return names;
  const display = new Intl.DisplayNames(['en'], { type: 'region' });
  for (let first = 65; first <= 90; first += 1) {
    for (let second = 65; second <= 90; second += 1) {
      const code = String.fromCharCode(first, second);
      const name = display.of(code);
      if (name && name !== code && name !== 'Unknown Region') {
        names.set(normalizeCountry(name), code);
      }
    }
  }
  Object.entries({
    'Czech Republic': 'CZ',
    'Ivory Coast': 'CI',
    'South Korea': 'KR',
    'North Korea': 'KP',
    'United States of America': 'US',
    USA: 'US',
    UK: 'GB',
    'Viet Nam': 'VN',
    Türkiye: 'TR',
    Turkey: 'TR',
    'United Arab Emirates': 'AE',
  }).forEach(([name, code]) => names.set(normalizeCountry(name), code));
  return names;
})();

export function countryCodeForFlag(code?: string, name?: string) {
  const supplied = code?.trim().toUpperCase();
  if (supplied && /^[A-Z]{2}$/.test(supplied)) return supplied;
  const value = name?.trim();
  if (!value) return undefined;
  if (/^[A-Za-z]{2}$/.test(value)) return value.toUpperCase();
  return countryNames.get(normalizeCountry(value));
}

const flagEmoji = (code: string) =>
  String.fromCodePoint(
    ...Array.from(code).map((letter) => 0x1f1e6 + letter.charCodeAt(0) - 65),
  );

export function CountryFlag({ code, name }: { code?: string; name?: string }) {
  const [failedCode, setFailedCode] = useState<string | null>(null);
  const resolvedCode = countryCodeForFlag(code, name);
  if (!resolvedCode)
    return (
      <Globe2
        size={18}
        aria-label={
          name ? `${name} flag unavailable` : 'Country flag unavailable'
        }
      />
    );
  if (failedCode === resolvedCode)
    return (
      <span
        className="ff-country-flag-emoji"
        role="img"
        aria-label={`${name || resolvedCode} flag`}
      >
        {flagEmoji(resolvedCode)}
      </span>
    );
  return (
    <img
      className="ff-country-flag"
      src={`https://flagcdn.com/${resolvedCode.toLowerCase()}.svg`}
      alt={`${name || resolvedCode} flag`}
      width="22"
      height="16"
      loading="lazy"
      onError={() => setFailedCode(resolvedCode)}
    />
  );
}
