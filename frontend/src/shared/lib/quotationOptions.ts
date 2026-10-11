// Values and Incoterm scope presets from the supplied Meridian Freight OS reference.
export const INCOTERMS = [
  'EXW',
  'FCA',
  'FOB',
  'CFR',
  'CIF',
  'CPT',
  'CIP',
  'DAP',
  'DPU',
  'DDP',
] as const;

export const QUOTE_CURRENCIES = [
  'USD',
  'THB',
  'INR',
  'AED',
  'MYR',
  'VND',
  'IDR',
  'SGD',
  'EUR',
] as const;

export const CONTAINER_TYPES = ['20GP', '40GP', '40HC', '45HC'] as const;

export const CHARGE_DESCRIPTIONS = [
  'Ocean Freight',
  'Air freight',
  'Road freight',
  'Pickup & trucking',
  'Export customs clearance',
  'Origin charges',
  'Destination charges',
  'Haulage to final place',
  'Import customs clearance',
  'Door delivery',
  'Duties & taxes',
  'Cargo insurance',
  'Terminal handling charge (THC)',
  'B/L fee',
  'Seal fee',
  'Documentation fee',
  'Delivery order (DO) fee',
  'CFS charges',
  'Fumigation',
  'Certificate of Origin',
  'Warehousing',
] as const;

type Scope =
  | 'pickup'
  | 'expCus'
  | 'origin'
  | 'freight'
  | 'dest'
  | 'haulD'
  | 'impCus'
  | 'delivery'
  | 'duties'
  | 'insurance';

const TERM_SCOPE: Record<string, Scope[]> = {
  EXW: ['pickup', 'expCus', 'origin', 'freight'],
  FCA: ['freight', 'dest', 'haulD'],
  FOB: ['freight', 'dest', 'haulD'],
  CFR: ['origin', 'freight'],
  CPT: ['origin', 'freight'],
  CIF: ['origin', 'freight', 'insurance'],
  CIP: ['origin', 'freight', 'insurance'],
  DAP: ['expCus', 'origin', 'freight', 'dest', 'haulD', 'delivery'],
  DPU: ['expCus', 'origin', 'freight', 'dest', 'haulD', 'delivery'],
  DDP: [
    'pickup',
    'expCus',
    'origin',
    'freight',
    'dest',
    'haulD',
    'impCus',
    'delivery',
    'duties',
    'insurance',
  ],
};

export function freightLabel(mode?: string) {
  return mode === 'AIR'
    ? 'Air freight'
    : mode === 'ROAD'
      ? 'Road freight'
      : 'Ocean Freight';
}

export function scopeOptions(mode?: string, hasFinalPlace = false) {
  const options: { key: Scope; description: string }[] = [
    { key: 'pickup', description: 'Pickup & trucking' },
    { key: 'expCus', description: 'Export customs clearance' },
    { key: 'origin', description: 'Origin charges' },
    { key: 'freight', description: freightLabel(mode) },
    { key: 'dest', description: 'Destination charges' },
    { key: 'haulD', description: 'Haulage to final place' },
    { key: 'impCus', description: 'Import customs clearance' },
    { key: 'delivery', description: 'Door delivery' },
    { key: 'duties', description: 'Duties & taxes' },
    { key: 'insurance', description: 'Cargo insurance' },
  ];
  return options.filter(({ key }) =>
    mode === 'ROAD'
      ? [
          'pickup',
          'expCus',
          'freight',
          'impCus',
          'delivery',
          'duties',
          'insurance',
        ].includes(key)
      : key !== 'haulD' || (mode !== 'ROAD' && hasFinalPlace),
  );
}

export function defaultScopeDescriptions(
  term: string,
  mode?: string,
  hasFinalPlace = false,
) {
  const selected = TERM_SCOPE[term.toUpperCase()] || ['freight'];
  return scopeOptions(mode, hasFinalPlace)
    .filter(({ key }) => selected.includes(key))
    .map(({ description }) => description);
}
