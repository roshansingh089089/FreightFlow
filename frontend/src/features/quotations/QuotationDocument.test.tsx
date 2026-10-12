import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../shared/lib/workspace';
import {
  QuotationPreview,
  type QuotationDocumentModel,
} from './QuotationDocument';

vi.mock('../../shared/lib/workspace', () => ({ api: vi.fn() }));

const model: QuotationDocumentModel = {
  number: 'QT-2026-000001',
  status: 'SENT',
  companyName: 'FreightFlow Logistics',
  companyCountry: 'India',
  customerName: 'Acme Exporters',
  contactName: 'Asha Patel',
  contactPosition: 'Buyer',
  contactEmail: 'asha@example.com',
  customerAddress: 'A long customer address, Mumbai, India',
  preparedBy: 'Roshan Singh',
  preparedByEmail: 'roshan@example.com',
  issueDate: '2026-10-11',
  validUntil: '2026-10-31',
  customerReference: null,
  mode: 'OCEAN',
  origin: 'Mumbai',
  destination: 'Bangkok',
  finalDestination: 'Chiang Mai',
  cargo: 'Machinery · 1 × 40HC',
  incoterm: 'CIF',
  transitTime: '14 days',
  currency: 'USD',
  charges: [],
  total: 0,
  included: [],
  excluded: ['Door delivery'],
  terms: 'Rates subject to availability.',
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  document.body.style.overflow = '';
});

describe('quotation preview', () => {
  it('keeps the document in a modal and restores focus and scrolling on Escape', async () => {
    vi.mocked(api).mockResolvedValue(model);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    function Harness() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>
            Open preview
          </button>
          {open && (
            <QuotationPreview
              id="quote-1"
              role="SALES"
              onClose={() => setOpen(false)}
            />
          )}
        </>
      );
    }
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <Harness />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    const opener = screen.getByRole('button', { name: 'Open preview' });
    opener.focus();
    fireEvent.click(opener);
    expect(document.body.style.overflow).toBe('hidden');
    expect(await screen.findByText('Acme Exporters')).toBeTruthy();
    expect(screen.getByText('Final place of delivery')).toBeTruthy();
    expect(screen.getByText('Chiang Mai')).toBeTruthy();
    expect(screen.getByText('No charges saved yet.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'View in Mail' })).toBeTruthy();
    expect(document.querySelector('.quote-modal-scroll')).toBeTruthy();
    expect(document.querySelector('.quote-modal-actions')).toBeTruthy();
    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(document.body.style.overflow).toBe('');
    expect(document.activeElement).toBe(opener);
  });
});
