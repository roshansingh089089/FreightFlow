import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ReferenceLocationPicker } from './ReferenceLocationPicker';

vi.mock('../lib/workspace', () => ({
  api: vi.fn(async () => [
    {
      code: 'CNSHA',
      name: 'Shanghai',
      countryCode: 'CN',
      countryName: 'China',
      locationTypes: ['PORT'],
    },
  ]),
}));
afterEach(cleanup);

describe('ReferenceLocationPicker', () => {
  it('shows backend country metadata, SVG flag, type and code, then selects by keyboard', async () => {
    const onSelect = vi.fn();
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const view = render(
      <QueryClientProvider client={client}>
        <ReferenceLocationPicker
          id="pol"
          label="Port of loading"
          mode="OCEAN"
          value="Shan"
          code=""
          onTextChange={vi.fn()}
          onSelect={onSelect}
        />
      </QueryClientProvider>,
    );
    fireEvent.focus(screen.getByRole('combobox', { name: 'Port of loading' }));
    const option = await screen.findByRole('option', {
      name: 'Shanghai · CN · CNSHA',
    });
    expect(option.textContent).toContain('China');
    expect(option.textContent).toContain('PORT');
    expect(option.textContent).toContain('CNSHA');
    expect(option.querySelector('img')?.getAttribute('src')).toBe(
      'https://flagcdn.com/cn.svg',
    );
    fireEvent.keyDown(
      screen.getByRole('combobox', { name: 'Port of loading' }),
      { key: 'Enter' },
    );
    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'CNSHA' }),
    );
    view.unmount();
  });

  it('shows a flag fallback for invalid country metadata', async () => {
    const { CountryFlag } = await import('./CountryFlag');
    render(<CountryFlag code="?" name="Unknown" />);
    expect(screen.getByLabelText('Unknown flag unavailable')).toBeTruthy();
  });
});
