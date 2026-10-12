import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { useData } from '../../shared/lib/workspace';
import QuotationDirectory from './QuotationDirectory';

vi.mock('../../shared/lib/workspace', () => ({
  useData: vi.fn((key: string) =>
    key === 'me'
      ? { data: { role: 'SALES' }, isLoading: false }
      : {
          data: {
            totalElements: 1,
            content: [
              {
                id: 'quote-1',
                quotationNumber: 'QT-2026-000001',
                customerName: 'Acme Exporters',
                currency: 'USD',
                totalAmount: 1200,
                status: 'SENT',
                createdAt: '2026-10-11T00:00:00Z',
                shipmentSnapshot:
                  '{"origin":"Mumbai","destination":"Bangkok","podCode":"THBKK","fpodCode":"THCNX","commodity":"Machinery"}',
              },
            ],
            totalPages: 1,
          },
          isLoading: false,
        },
  ),
  date: () => '11 Oct 2026',
  fmt: (value: number) => String(value),
  api: vi.fn(async () => [
    { code: 'THCNX', name: 'Chiang Mai', countryCode: 'TH' },
  ]),
}));

describe('quotation directory', () => {
  it('resolves a legacy final place code and sends supported status filters to the API', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <QuotationDirectory />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByText('Mumbai → Bangkok → Chiang Mai'),
    ).toBeTruthy();
    expect(screen.getByText('Machinery')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Drafts' }));
    expect(useData).toHaveBeenCalledWith(
      'quotes',
      expect.stringContaining('status=DRAFT'),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Sent' }));
    expect(useData).toHaveBeenCalledWith(
      'quotes',
      expect.stringContaining('status=SENT'),
    );
    expect(screen.queryByRole('button', { name: 'Accepted' })).toBeNull();
  });
});
