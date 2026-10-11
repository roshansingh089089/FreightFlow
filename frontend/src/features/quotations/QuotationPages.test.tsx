import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, post } from '../../shared/lib/workspace';
import { QuoteForm } from './QuotationPages';

vi.mock('../../shared/lib/workspace', () => ({
  api: vi.fn(async () => ({
    enquiry: {
      customerName: 'Acme',
      origin: 'Laem Chabang',
      destination: 'Nhava Sheva',
      polCode: 'THLCH',
      podCode: 'INNSA',
      portOfLoading: 'Laem Chabang',
      portOfDischarge: 'Nhava Sheva',
      mode: 'OCEAN',
      commodity: 'General cargo',
    },
  })),
  post: vi.fn(async (path: string) =>
    path === '/fx/quote-preview'
      ? { currency: 'EUR', total: 75, lines: [] }
      : { id: 'saved-quote' },
  ),
  put: vi.fn(),
  qc: { invalidateQueries: vi.fn() },
  toast: vi.fn(),
  date: (value: string) => value,
  fmt: (value: number) => String(value),
  useData: vi.fn(),
}));
vi.mock('../ai/AiAssist', () => ({
  AiActionCard: () => null,
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('quotation builder', () => {
  it.each([
    ['pol', 'Port of loading (POL)', 'polCode', 'OCEAN'],
    ['pod', 'Port of discharge (POD)', 'podCode', 'OCEAN'],
    ['fpod', 'Final place (FPOD)', 'fpodCode', 'ALL'],
  ] as const)(
    'searches, selects, clears and reselects %s repeatedly from cached results',
    async (_field, label, codeKey, mode) => {
      vi.mocked(api).mockImplementation(async (path: string) => {
        if (path.startsWith('/reference/locations'))
          return [{ code: 'USNYC', name: 'New York', countryCode: 'US' }];
        return {
          enquiry: {
            customerName: 'Acme',
            origin: 'Laem Chabang',
            destination: 'Nhava Sheva',
            polCode: 'THLCH',
            podCode: 'INNSA',
            portOfLoading: 'Laem Chabang',
            portOfDischarge: 'Nhava Sheva',
            mode: 'OCEAN',
            commodity: 'General cargo',
          },
        };
      });
      const client = new QueryClient({
        defaultOptions: { queries: { retry: false } },
      });
      render(
        <QueryClientProvider client={client}>
          <MemoryRouter
            initialEntries={['/enquiries/enquiry-1/quotations/new']}
          >
            <Routes>
              <Route
                path="/enquiries/:enquiryId/quotations/new"
                element={<QuoteForm />}
              />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>,
      );
      await screen.findByText(
        'Laem Chabang → Nhava Sheva · OCEAN · General cargo',
      );
      const input = screen.getByLabelText(label) as HTMLInputElement;
      for (let cycle = 0; cycle < 3; cycle++) {
        fireEvent.change(input, { target: { value: 'New' } });
        if (cycle === 0) {
          await new Promise((resolve) => setTimeout(resolve, 100));
          expect(
            vi
              .mocked(api)
              .mock.calls.filter(([path]) =>
                String(path).startsWith('/reference/locations'),
              ),
          ).toHaveLength(0);
        }
        const option = await screen.findByRole('option', {
          name: 'New York · US · USNYC',
        });
        expect(input.getAttribute('aria-expanded')).toBe('true');
        fireEvent.click(option);
        expect(input.value).toBe('New York');
        expect(input.parentElement?.textContent).toContain('UN/LOCODE USNYC');
        fireEvent.change(input, { target: { value: '' } });
        expect(input.value).toBe('');
        expect(input.parentElement?.textContent).not.toContain(
          'UN/LOCODE USNYC',
        );
      }
      fireEvent.change(input, { target: { value: 'New Yor' } });
      expect(
        await screen.findByRole('option', { name: 'New York · US · USNYC' }),
      ).toBeTruthy();
      fireEvent.change(input, { target: { value: 'New' } });
      fireEvent.click(
        await screen.findByRole('option', { name: 'New York · US · USNYC' }),
      );
      expect(input.parentElement?.textContent).toContain('UN/LOCODE USNYC');
      if (codeKey === 'fpodCode') {
        fireEvent.change(input, { target: { value: '' } });
        expect(input.parentElement?.textContent).not.toContain(
          'UN/LOCODE USNYC',
        );
      }
      const calls = vi
        .mocked(api)
        .mock.calls.map(([path]) => String(path))
        .filter((path) => path.startsWith('/reference/locations'));
      expect(calls.filter((path) => path.includes('q=New&'))).toHaveLength(1);
      expect(
        calls.filter((path) => path.includes('q=New%20Yor&')),
      ).toHaveLength(1);
      expect(calls.every((path) => path.includes(`mode=${mode}`))).toBe(true);
      fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
      fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
      fireEvent.click(screen.getByRole('button', { name: 'Save draft' }));
      await waitFor(() =>
        expect(post).toHaveBeenCalledWith(
          '/enquiries/enquiry-1/quotations',
          expect.objectContaining({
            shipmentOverride: expect.objectContaining({
              [codeKey]: codeKey === 'fpodCode' ? null : 'USNYC',
            }),
          }),
        ),
      );
    },
  );

  it('keeps reference destination charges out of the live total', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/enquiry-1/quotations/new']}>
          <Routes>
            <Route
              path="/enquiries/:enquiryId/quotations/new"
              element={<QuoteForm />}
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await screen.findByText(
      'Laem Chabang → Nhava Sheva · OCEAN · General cargo',
    );
    fireEvent.change(screen.getByLabelText('Incoterm'), {
      target: { value: 'CIF' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
    fireEvent.click(screen.getByRole('button', { name: /Add charge/ }));
    fireEvent.change(screen.getByLabelText('Charge 4 description'), {
      target: { value: 'Destination charges' },
    });
    fireEvent.change(screen.getByLabelText('Charge 4 unit price'), {
      target: { value: '100' },
    });
    expect(screen.getByText('Total').parentElement?.textContent).toContain(
      'USD 0',
    );
  });
  it('applies Meridian scope defaults when an Incoterm is selected', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/enquiry-1/quotations/new']}>
          <Routes>
            <Route
              path="/enquiries/:enquiryId/quotations/new"
              element={<QuoteForm />}
            />
            <Route path="/quotations/:id" element={<h1>Saved quotation</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByText(
      'Laem Chabang → Nhava Sheva · OCEAN · General cargo',
    );
    fireEvent.change(screen.getByLabelText('Incoterm'), {
      target: { value: 'CIF' },
    });
    expect(
      screen
        .getByRole('button', { name: /Origin charges/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    expect(
      screen
        .getByRole('button', { name: /Cargo insurance/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    expect(
      screen
        .getByRole('button', { name: /Pickup & trucking/ })
        .getAttribute('aria-pressed'),
    ).toBe('false');

    fireEvent.change(screen.getByLabelText('Incoterm'), {
      target: { value: 'FOB' },
    });
    expect(
      screen
        .getByRole('button', { name: /Destination charges/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    expect(
      screen
        .getByRole('button', { name: /Cargo insurance/ })
        .getAttribute('aria-pressed'),
    ).toBe('false');
    expect(screen.getByRole('option', { name: 'VND' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
    fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/enquiries/enquiry-1/quotations',
        expect.objectContaining({
          shipmentOverride: expect.objectContaining({ incoterm: 'FOB' }),
          lines: expect.arrayContaining([
            expect.objectContaining({
              description: 'Destination charges',
              unitPrice: 0,
            }),
          ]),
        }),
      ),
    );
  });
  it('saves CIF destination charges as reference without including them in scope', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/enquiry-1/quotations/new']}>
          <Routes>
            <Route
              path="/enquiries/:enquiryId/quotations/new"
              element={<QuoteForm />}
            />
            <Route path="/quotations/:id" element={<h1>Saved quotation</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await screen.findByText(
      'Laem Chabang → Nhava Sheva · OCEAN · General cargo',
    );
    fireEvent.change(screen.getByLabelText('Incoterm'), {
      target: { value: 'CIF' },
    });
    fireEvent.click(screen.getByLabelText(/Show destination charges/));
    expect(
      screen
        .getByRole('button', { name: /Destination charges/ })
        .getAttribute('aria-pressed'),
    ).toBe('false');
    fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
    fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/enquiries/enquiry-1/quotations',
        expect.objectContaining({
          shipmentOverride: expect.objectContaining({
            scope: expect.not.arrayContaining(['Destination charges']),
          }),
          lines: expect.arrayContaining([
            expect.objectContaining({
              description: 'Destination charges',
              pricingTreatment: 'FOR_REFERENCE',
            }),
          ]),
        }),
      ),
    );
  });
  it('prefills the quotation from saved Sales Tracker scope and currency', async () => {
    vi.mocked(api).mockResolvedValueOnce({
      enquiry: {
        customerName: 'Acme',
        origin: 'Laem Chabang',
        destination: 'Nhava Sheva',
        polCode: 'THLCH',
        podCode: 'INNSA',
        mode: 'OCEAN',
        commodity: 'General cargo',
        incoterm: 'CIF',
        quoteCurrency: 'THB',
        serviceScope: [
          'Origin charges',
          'Ocean Freight',
          'Cargo insurance',
          'Warehousing',
        ],
        referenceDestinationCharges: true,
      },
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/enquiry-1/quotations/new']}>
          <Routes>
            <Route
              path="/enquiries/:enquiryId/quotations/new"
              element={<QuoteForm />}
            />
            <Route path="/quotations/:id" element={<h1>Saved quotation</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await screen.findByText(
      'Laem Chabang → Nhava Sheva · OCEAN · General cargo',
    );
    await waitFor(() =>
      expect(
        (screen.getByLabelText('Quote currency') as HTMLSelectElement).value,
      ).toBe('THB'),
    );
    expect(
      screen
        .getByRole('button', { name: /Warehousing/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    expect(
      (screen.getByLabelText(/Show destination charges/) as HTMLInputElement)
        .checked,
    ).toBe(true);
  });

  it('keeps charge edits and saves an explicit draft', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/enquiry-1/quotations/new']}>
          <Routes>
            <Route
              path="/enquiries/:enquiryId/quotations/new"
              element={<QuoteForm />}
            />
            <Route path="/quotations/:id" element={<h1>Saved quotation</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(
      await screen.findByText(
        'Laem Chabang → Nhava Sheva · OCEAN · General cargo',
      ),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Pickup & trucking/ }));
    expect(
      screen
        .getByRole('button', { name: /Pickup & trucking/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    fireEvent.change(screen.getByLabelText('Quote currency'), {
      target: { value: 'EUR' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
    fireEvent.click(screen.getByRole('button', { name: /Add charge/ }));
    fireEvent.change(screen.getByLabelText('Charge 3 description'), {
      target: { value: 'Documentation' },
    });
    fireEvent.change(screen.getByLabelText('Charge 3 unit price'), {
      target: { value: '75' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
    await screen.findByText('EUR 75');
    fireEvent.click(screen.getByRole('button', { name: 'Save draft' }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/enquiries/enquiry-1/quotations',
        expect.objectContaining({
          currency: 'EUR',
          shipmentOverride: expect.objectContaining({
            polCode: 'THLCH',
            podCode: 'INNSA',
          }),
          lines: expect.arrayContaining([
            expect.objectContaining({
              description: 'Pickup & trucking',
              quantity: 1,
              unitPrice: 0,
            }),
            expect.objectContaining({
              description: 'Documentation',
              quantity: 1,
              unitPrice: 75,
            }),
          ]),
        }),
      ),
    );
    expect(
      await screen.findByRole('heading', { name: 'Saved quotation' }),
    ).toBeTruthy();
  });

  it('previews INR charges in THB before saving the quotation', async () => {
    vi.mocked(post).mockImplementation(async (path: string) =>
      path === '/fx/quote-preview'
        ? {
            currency: 'THB',
            total: 34.66,
            lines: [
              {
                convertedAmount: 34.66,
                originalCurrency: 'INR',
                originalAmount: 100,
                rate: 0.3466348083,
                source: 'ECB_REFERENCE',
                observationDate: '2026-10-09',
              },
            ],
          }
        : { id: 'saved-quote' },
    );
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/enquiry-1/quotations/new']}>
          <Routes>
            <Route
              path="/enquiries/:enquiryId/quotations/new"
              element={<QuoteForm />}
            />
            <Route path="/quotations/:id" element={<h1>Saved quotation</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByText(
        'Laem Chabang → Nhava Sheva · OCEAN · General cargo',
      ),
    ).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Quote currency'), {
      target: { value: 'THB' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
    fireEvent.change(screen.getByLabelText('Charge 1 unit price'), {
      target: { value: '100' },
    });
    fireEvent.change(screen.getByLabelText('Charge 1 currency'), {
      target: { value: 'INR' },
    });
    await waitFor(
      () =>
        expect(post).toHaveBeenCalledWith(
          '/fx/quote-preview',
          expect.objectContaining({
            currency: 'THB',
            lines: [
              expect.objectContaining({ unitPrice: 100, currency: 'INR' }),
            ],
          }),
        ),
      { timeout: 3000 },
    );
    expect((await screen.findAllByText(/THB 34\.66/)).length).toBeGreaterThan(
      0,
    );
    fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/enquiries/enquiry-1/quotations',
        expect.objectContaining({
          currency: 'THB',
          lines: [expect.objectContaining({ currency: 'INR', unitPrice: 100 })],
        }),
      ),
    );
  });

  it('shows an unavailable-rate error instead of treating INR 100 as THB 100', async () => {
    vi.mocked(post).mockImplementation(async (path: string) => {
      if (path === '/fx/quote-preview')
        throw new Error('ECB reference rates are unavailable');
      return { id: 'saved-quote' };
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/enquiry-1/quotations/new']}>
          <Routes>
            <Route
              path="/enquiries/:enquiryId/quotations/new"
              element={<QuoteForm />}
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByText(
        'Laem Chabang → Nhava Sheva · OCEAN · General cargo',
      ),
    ).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Quote currency'), {
      target: { value: 'THB' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Continue/ }));
    fireEvent.change(screen.getByLabelText('Charge 1 unit price'), {
      target: { value: '100' },
    });
    fireEvent.change(screen.getByLabelText('Charge 1 currency'), {
      target: { value: 'INR' },
    });
    expect(await screen.findByText('Rate unavailable')).toBeTruthy();
    expect(screen.getByRole('alert').textContent).toContain(
      'ECB reference rates are unavailable',
    );
    expect(screen.queryByText('THB 100')).toBeNull();
  });
});
