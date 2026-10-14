import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CountryFlag, countryCodeForFlag } from './CountryFlag';

describe('CountryFlag', () => {
  it('resolves stored CRM country names even without reference API codes', () => {
    expect(countryCodeForFlag(undefined, 'Vietnam')).toBe('VN');
    expect(countryCodeForFlag(undefined, 'India')).toBe('IN');
    expect(countryCodeForFlag(undefined, 'Thailand')).toBe('TH');
    expect(countryCodeForFlag(undefined, 'Côte d’Ivoire')).toBe('CI');
    render(<CountryFlag name="Vietnam" />);
    expect(
      screen.getByRole('img', { name: 'Vietnam flag' }).getAttribute('src'),
    ).toBe('https://flagcdn.com/vn.svg');
  });

  it('shows the country flag glyph when the SVG cannot load', () => {
    render(<CountryFlag name="India" />);
    fireEvent.error(screen.getByRole('img', { name: 'India flag' }));
    expect(screen.getByRole('img', { name: 'India flag' }).textContent).toBe(
      '🇮🇳',
    );
  });

  it('keeps a readable fallback for unknown country values', () => {
    render(<CountryFlag name="Unknown place" />);
    expect(
      screen.getByLabelText('Unknown place flag unavailable'),
    ).toBeTruthy();
  });
});
