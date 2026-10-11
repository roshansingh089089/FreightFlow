import { describe, expect, it } from 'vitest';
import {
  CONTAINER_TYPES,
  defaultScopeDescriptions,
  INCOTERMS,
  QUOTE_CURRENCIES,
} from './quotationOptions';

describe('Meridian quotation field values', () => {
  it('provides the reference Incoterms, currencies, and container types', () => {
    expect(INCOTERMS).toEqual([
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
    ]);
    expect(QUOTE_CURRENCIES).toEqual([
      'USD',
      'THB',
      'INR',
      'AED',
      'MYR',
      'VND',
      'IDR',
      'SGD',
      'EUR',
    ]);
    expect(CONTAINER_TYPES).toEqual(['20GP', '40GP', '40HC', '45HC']);
  });

  it('selects scope from the Incoterm without inventing prices', () => {
    expect(defaultScopeDescriptions('EXW', 'OCEAN')).toEqual([
      'Pickup & trucking',
      'Export customs clearance',
      'Origin charges',
      'Ocean Freight',
    ]);
    expect(defaultScopeDescriptions('CIF', 'OCEAN')).toEqual([
      'Origin charges',
      'Ocean Freight',
      'Cargo insurance',
    ]);
    expect(defaultScopeDescriptions('DDP', 'ROAD')).toEqual([
      'Pickup & trucking',
      'Export customs clearance',
      'Road freight',
      'Import customs clearance',
      'Door delivery',
      'Duties & taxes',
      'Cargo insurance',
    ]);
    expect(defaultScopeDescriptions('FOB', 'OCEAN', true)).toEqual([
      'Ocean Freight',
      'Destination charges',
      'Haulage to final place',
    ]);
  });
});
