import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useData } from '../../shared/lib/workspace';
import QuotationDirectory from './QuotationDirectory';

vi.mock('./QuotationDocument', () => ({
  QuotationPreview: ({ id, onClose }: { id: string; onClose: () => void }) => (
    <div role="dialog" aria-modal="true" aria-label={`Preview ${id}`}>
      <button type="button" onClick={onClose}>
        Close preview
      </button>
    </div>
  ),
}));

const rows = [
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
  {
    id: 'quote-2',
    quotationNumber: 'QT-2026-000002',
    customerName: 'Blue Water',
    currency: 'USD',
    totalAmount: 2400,
    status: 'DRAFT',
    createdAt: '2026-10-10T00:00:00Z',
    shipmentSnapshot: '{"origin":"Chennai","destination":"Dubai"}',
  },
];

vi.mock('../../shared/lib/workspace', () => ({
  useData: vi.fn((key: string) =>
    key === 'me'
      ? { data: { role: 'SALES' }, isLoading: false }
      : {
          data: { totalElements: 2, content: rows, totalPages: 1 },
          isLoading: false,
        },
  ),
  date: () => '11 Oct 2026',
  fmt: (value: number) => String(value),
  api: vi.fn(async (path: string) =>
    path.startsWith('/quotations/')
      ? rows.find((row) => path === `/quotations/${row.id}`)
      : [{ code: 'THCNX', name: 'Chiang Mai', countryCode: 'TH' }],
  ),
}));

function renderDirectory() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <QuotationDirectory />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(cleanup);

describe('quotation directory', () => {
  it('selects a quotation from its ID or row without opening preview, then returns to selection after preview', async () => {
    renderDirectory();
    expect(await screen.findByText('Final: Chiang Mai')).toBeTruthy();
    expect(screen.getByTitle('Mumbai → Bangkok → Chiang Mai')).toBeTruthy();
    expect(screen.getByText('Machinery')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Close details' })).toBeNull();
    expect(
      document.querySelector('.crm-record-layout.crm-panel-hidden'),
    ).toBeTruthy();

    fireEvent.click(
      screen.getByRole('button', { name: 'Show details for QT-2026-000001' }),
    );
    expect(screen.getByRole('button', { name: 'Close details' })).toBeTruthy();
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(
      (await screen.findByRole('link', { name: 'View in Mail' })).getAttribute(
        'href',
      ),
    ).toBe('/mail/stored?quotationId=quote-1');
    expect(document.querySelector('tr.crm-selected')?.textContent).toContain(
      'QT-2026-000001',
    );

    fireEvent.click(screen.getByRole('button', { name: 'Preview / PDF' }));
    expect(
      screen.getByRole('dialog', { name: 'Preview quote-1' }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Close preview' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(document.querySelector('tr.crm-selected')?.textContent).toContain(
      'QT-2026-000001',
    );
    expect(screen.getByRole('button', { name: 'Close details' })).toBeTruthy();

    fireEvent.click(screen.getByText('Blue Water'));
    expect(document.querySelector('tr.crm-selected')?.textContent).toContain(
      'QT-2026-000002',
    );
    expect(
      await screen.findByRole('link', { name: 'Edit draft' }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Close details' }));
    expect(
      document.querySelector('.crm-record-layout.crm-panel-hidden'),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Close details' })).toBeNull();
  });

  it('retains the existing status filters', () => {
    renderDirectory();
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

  it('supports keyboard row selection without activating a nested quotation button twice', async () => {
    renderDirectory();
    const row = screen.getByRole('row', { name: /QT-2026-000002.*Blue Water/ });
    fireEvent.keyDown(row, { key: 'Enter' });
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(
      await screen.findByRole('link', { name: 'Edit draft' }),
    ).toBeTruthy();
    expect(row.getAttribute('aria-selected')).toBe('true');
    fireEvent.keyDown(
      screen.getByRole('button', { name: 'Show details for QT-2026-000001' }),
      { key: 'Enter' },
    );
    expect(row.getAttribute('aria-selected')).toBe('true');
  });
});
