import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, post } from '../../shared/lib/workspace';
import { EnquiryForm } from './EnquiryPages';

const customerId = '11111111-1111-4111-8111-111111111111';

vi.mock('../../shared/lib/workspace', () => ({
  useData: () => ({
    data: {
      content: [
        { id: '11111111-1111-4111-8111-111111111111', companyName: 'Acme' },
      ],
    },
  }),
  api: vi.fn(async (path: string) => {
    if (path === '/mail/messages/22222222-2222-4222-8222-222222222222')
      return {
        id: '22222222-2222-4222-8222-222222222222',
        subject: 'FCL Laem Chabang to Nhava Sheva',
        fromEmail: 'shipper@example.test',
        bodyText: 'Please quote 2 x 40HC for general cargo.',
      };
    if (path === '/enquiries/saved-enquiry')
      return {
        status: 'DRAFT',
        enquiry: {
          id: 'saved-enquiry',
          status: 'DRAFT',
          customerId,
          mode: 'ROAD',
          origin: 'Bangkok',
          destination: 'Chennai',
          commodity: 'Machinery',
        },
      };
    if (path.startsWith('/reference/locations')) {
      if (path.includes('Laem'))
        return [{ code: 'THLCH', name: 'Laem Chabang', countryCode: 'TH' }];
      if (path.includes('Nhava'))
        return [
          {
            code: 'INNSA',
            name: 'Nhava Sheva (Jawaharlal Nehru)',
            countryCode: 'IN',
          },
        ];
      return [];
    }
    return { contacts: [] };
  }),
  post: vi.fn(),
  put: vi.fn(),
  qc: { invalidateQueries: vi.fn() },
  toast: vi.fn(),
  date: (value: string) => value,
}));

beforeEach(() => {
  vi.mocked(post).mockImplementation(async (path: string) => {
    if (path === '/ai/shipment-extract')
      return {
        extracted: {
          customerName: 'Acme',
          mode: 'OCEAN',
          origin: 'Laem Chabang',
          destination: 'Nhava Sheva',
          portOfLoading: 'Laem Chabang',
          portOfDischarge: 'Nhava Sheva',
          shipmentType: 'FCL',
          commodity: 'General cargo',
          incoterm: 'FOB',
          readyDate: '2026-10-20',
          containerType: '40HC',
          containerQuantity: 2,
          grossWeight: 18000,
          weightUnit: 'KG',
          pickupRequired: true,
          notes: 'Earliest sailing after cargo readiness',
        },
        missingFields: ['Insurance'],
        warnings: [],
      };
    if (path === '/enquiries') return { id: 'saved-enquiry' };
    throw new Error(`Unexpected request: ${path}`);
  });
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('single-page enquiry intake', () => {
  it('marks missing required fields invalid and clears the state after correction', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/new']}>
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Create Enquiry' }));
    expect(await screen.findByText('Select a customer')).toBeTruthy();
    expect(
      screen.getByLabelText(/^Customer \*/).getAttribute('aria-invalid'),
    ).toBe('true');
    expect(screen.getByLabelText(/^Mode \*/).getAttribute('aria-invalid')).toBe(
      'true',
    );
    expect(
      screen.getByLabelText(/^Origin \*/).getAttribute('aria-invalid'),
    ).toBe('true');
    expect(
      screen.getByLabelText(/^Commodity \*/).getAttribute('aria-invalid'),
    ).toBe('true');
    fireEvent.change(screen.getByLabelText(/^Origin \*/), {
      target: { value: 'Bangkok' },
    });
    await waitFor(() =>
      expect(
        screen.getByLabelText(/^Origin \*/).getAttribute('aria-invalid'),
      ).toBe('false'),
    );
  });

  it('keeps C-term destination charges reference-only in Sales Tracker', () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/new']}>
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText('Mode *'), {
      target: { value: 'OCEAN' },
    });
    fireEvent.change(screen.getByLabelText('Incoterm'), {
      target: { value: 'CIF' },
    });
    expect(
      screen
        .getByRole('button', { name: /Cargo insurance/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    fireEvent.click(
      screen.getByRole('button', { name: /Destination charges/ }),
    );
    expect(
      (screen.getByLabelText(/Show destination charges/) as HTMLInputElement)
        .checked,
    ).toBe(true);
    expect(
      screen
        .getByRole('button', { name: /Destination charges/ })
        .getAttribute('aria-pressed'),
    ).toBe('false');
    fireEvent.change(screen.getByLabelText('Incoterm'), {
      target: { value: 'FOB' },
    });
    expect(screen.queryByLabelText(/Show destination charges/)).toBeNull();
    expect(
      screen
        .getByRole('button', { name: /Destination charges/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
  });
  it('applies and saves Incoterm scope and preferred quote currency in Sales Tracker', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/new']}>
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
            <Route path="/enquiries/:id" element={<h1>Saved enquiry</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    const summary = screen.getByLabelText('Enquiry Summary');
    const intake = summary.closest('form')!;
    const main = intake.querySelector('.enquiry-form-main')!;
    expect(main.contains(intake.querySelector('.enquiry-customer-card'))).toBe(
      true,
    );
    expect(main.contains(intake.querySelector('.enquiry-services-card'))).toBe(
      true,
    );
    expect(
      main.contains(intake.querySelector('.enquiry-additional-card')),
    ).toBe(true);
    expect(summary.parentElement).toBe(intake);
    expect(main.contains(summary)).toBe(false);
    fireEvent.change(screen.getByLabelText('Customer *'), {
      target: { value: customerId },
    });
    fireEvent.change(screen.getByLabelText('Mode *'), {
      target: { value: 'ROAD' },
    });
    fireEvent.change(screen.getByLabelText('Incoterm'), {
      target: { value: 'EXW' },
    });
    expect(
      screen
        .getByRole('button', { name: /Pickup & trucking/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    expect(
      screen
        .getByRole('button', { name: /Road freight/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    fireEvent.click(screen.getByRole('button', { name: /Cargo insurance/ }));
    fireEvent.change(screen.getByLabelText('Quote currency'), {
      target: { value: 'THB' },
    });
    fireEvent.change(screen.getByLabelText('Origin *'), {
      target: { value: 'Bangkok' },
    });
    fireEvent.change(screen.getByLabelText('Destination *'), {
      target: { value: 'Chennai' },
    });
    fireEvent.change(screen.getByLabelText('Commodity *'), {
      target: { value: 'Machinery' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Create Enquiry' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/enquiries',
        expect.objectContaining({
          incoterm: 'EXW',
          quoteCurrency: 'THB',
          insuranceRequired: true,
          serviceScope: expect.arrayContaining([
            'Pickup & trucking',
            'Export customs clearance',
            'Road freight',
            'Cargo insurance',
          ]),
        }),
      ),
    );
  });
  it('creates a manual enquiry with the existing API', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/new']}>
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
            <Route path="/enquiries/:id" element={<h1>Saved enquiry</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText('Customer *'), {
      target: { value: customerId },
    });
    fireEvent.change(screen.getByLabelText('Mode *'), {
      target: { value: 'ROAD' },
    });
    fireEvent.change(screen.getByLabelText('Origin *'), {
      target: { value: 'Bangkok' },
    });
    fireEvent.change(screen.getByLabelText('Destination *'), {
      target: { value: 'Chennai' },
    });
    fireEvent.change(screen.getByLabelText('Commodity *'), {
      target: { value: 'Machinery' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Create Enquiry' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/enquiries',
        expect.objectContaining({
          status: 'NEW',
          customerId,
          mode: 'ROAD',
          commodity: 'Machinery',
        }),
      ),
    );
    expect(
      await screen.findByRole('heading', { name: 'Saved enquiry' }),
    ).toBeTruthy();
  });

  it('keeps the source message association when creating from Mail', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter
          initialEntries={[
            '/enquiries/new?sourceMessageId=22222222-2222-4222-8222-222222222222',
          ]}
        >
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
            <Route path="/enquiries/:id" element={<h1>Saved enquiry</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/ai/shipment-extract',
        expect.objectContaining({
          text: expect.stringContaining('FCL Laem Chabang'),
        }),
      ),
    );
    await screen.findByRole('button', { name: 'Confirm route' });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm route' }));
    fireEvent.click(screen.getByRole('button', { name: 'Create Enquiry' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/enquiries',
        expect.objectContaining({
          sourceMessageId: '22222222-2222-4222-8222-222222222222',
        }),
      ),
    );
  });

  it('fills route text from a canonical port and clears incompatible cargo on mode change', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/new']}>
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText('Mode *'), {
      target: { value: 'OCEAN' },
    });
    fireEvent.change(screen.getByLabelText('Port of loading (POL) *'), {
      target: { value: 'Laem' },
    });
    fireEvent.focus(screen.getByLabelText('Port of loading (POL) *'));
    fireEvent.click(
      await screen.findByRole('option', { name: /Laem Chabang · TH · THLCH/ }),
    );
    await waitFor(() =>
      expect(
        (screen.getByLabelText('Origin *') as HTMLInputElement).value,
      ).toBe('Laem Chabang'),
    );
    expect(screen.getByLabelText('Enquiry Summary').textContent).toContain(
      'THLCH',
    );
    fireEvent.change(screen.getByLabelText('Shipment type'), {
      target: { value: 'FCL' },
    });
    fireEvent.change(screen.getByLabelText('Container Type'), {
      target: { value: '40HC' },
    });
    fireEvent.change(screen.getByLabelText('Mode *'), {
      target: { value: 'ROAD' },
    });
    expect(screen.queryByLabelText('Container Type')).toBeNull();
    expect(
      (screen.getByLabelText('Truck load') as HTMLSelectElement).value,
    ).toBe('');
  });

  it('points to an unselected route location and accepts its canonical suggestion', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/new']}>
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText('Customer *'), {
      target: { value: customerId },
    });
    fireEvent.change(screen.getByLabelText('Mode *'), {
      target: { value: 'OCEAN' },
    });
    fireEvent.change(screen.getByLabelText('Port of loading (POL) *'), {
      target: { value: 'Laem' },
    });
    fireEvent.change(screen.getByLabelText('Origin *'), {
      target: { value: 'Laem Chabang' },
    });
    fireEvent.change(screen.getByLabelText('Destination *'), {
      target: { value: 'Nhava Sheva' },
    });
    fireEvent.change(screen.getByLabelText('Commodity *'), {
      target: { value: 'General cargo' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Create Enquiry' }));
    expect(
      await screen.findByText(
        'Choose a loading location from the suggestions.',
      ),
    ).toBeTruthy();
    expect(
      screen
        .getByLabelText('Port of loading (POL) *')
        .getAttribute('aria-invalid'),
    ).toBe('true');
    fireEvent.focus(screen.getByLabelText('Port of loading (POL) *'));
    fireEvent.click(
      await screen.findByRole('option', { name: /Laem Chabang · TH · THLCH/ }),
    );
    expect(
      screen
        .getByLabelText('Port of loading (POL) *')
        .getAttribute('aria-invalid'),
    ).toBe('false');
  });

  it('updates the summary and persists a draft through the enquiry API', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/new']}>
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
            <Route path="/enquiries/:id/edit" element={<EnquiryForm />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText('Customer *'), {
      target: { value: customerId },
    });
    fireEvent.change(screen.getByLabelText('Mode *'), {
      target: { value: 'ROAD' },
    });
    fireEvent.change(screen.getByLabelText('Origin *'), {
      target: { value: 'Bangkok' },
    });
    fireEvent.change(screen.getByLabelText('Destination *'), {
      target: { value: 'Chennai' },
    });
    fireEvent.change(screen.getByLabelText('Commodity *'), {
      target: { value: 'Machinery' },
    });
    expect(screen.getByLabelText('Enquiry Summary').textContent).toContain(
      'Bangkok → Chennai',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Save Draft' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/enquiries',
        expect.objectContaining({
          status: 'DRAFT',
          mode: 'ROAD',
          origin: 'Bangkok',
          destination: 'Chennai',
        }),
      ),
    );
    await waitFor(() =>
      expect(
        (screen.getByLabelText('Origin *') as HTMLInputElement).value,
      ).toBe('Bangkok'),
    );
    expect(
      await screen.findByRole('button', { name: 'Save Draft' }),
    ).toBeTruthy();
  });

  it('prefills editable fields, preserves manual edits, and submits the form', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/new']}>
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
            <Route path="/enquiries/:id" element={<h1>Saved enquiry</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    fireEvent.change(screen.getByLabelText('Origin *'), {
      target: { value: 'My manually entered origin' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Cargo insurance/ }));
    fireEvent.click(screen.getByText('Paste requirements and extract with AI'));
    fireEvent.change(screen.getByLabelText('Customer requirements'), {
      target: { value: 'FCL from Laem Chabang to Nhava Sheva' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Extract with AI/ }));

    await waitFor(() =>
      expect(
        (screen.getByLabelText('Destination *') as HTMLInputElement).value,
      ).toBe('Nhava Sheva'),
    );
    expect((screen.getByLabelText('Origin *') as HTMLInputElement).value).toBe(
      'My manually entered origin',
    );
    expect(
      (screen.getByLabelText('Customer *') as HTMLSelectElement).value,
    ).toBe(customerId);
    expect((screen.getByLabelText('Mode *') as HTMLSelectElement).value).toBe(
      'OCEAN',
    );
    expect(
      (screen.getByLabelText('Port of loading (POL) *') as HTMLInputElement)
        .value,
    ).toBe('Laem Chabang');
    expect(
      (screen.getByLabelText('Port of discharge (POD) *') as HTMLInputElement)
        .value,
    ).toBe('Nhava Sheva');
    expect(
      (screen.getByLabelText('Cargo-ready date') as HTMLInputElement).value,
    ).toBe('2026-10-20');
    expect((screen.getByLabelText('Incoterm') as HTMLInputElement).value).toBe(
      'FOB',
    );
    expect(
      (screen.getByLabelText('Shipment type') as HTMLSelectElement).value,
    ).toBe('FCL');
    expect(
      (screen.getByLabelText('Container Type') as HTMLInputElement).value,
    ).toBe('40HC');
    expect(
      (screen.getByLabelText('Container Quantity') as HTMLInputElement).value,
    ).toBe('2');
    expect(
      (screen.getByLabelText('Gross Weight') as HTMLInputElement).value,
    ).toBe('18000');
    expect(
      screen
        .getByRole('button', { name: /Pickup & trucking/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    expect(
      screen
        .getByRole('button', { name: /Cargo insurance/ })
        .getAttribute('aria-pressed'),
    ).toBe('true');
    expect(
      (
        screen.getByLabelText(
          'Special instructions / notes',
        ) as HTMLTextAreaElement
      ).value,
    ).toBe('Earliest sailing after cargo readiness');
    expect(
      screen
        .getByText('Paste requirements and extract with AI')
        .closest('details')?.open,
    ).toBe(false);
    expect(screen.getByText(/Check manually: Insurance/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Create Enquiry' }));
    expect(
      await screen.findByText(
        /confirm the locations before creating the enquiry/i,
      ),
    ).toBeTruthy();
    expect(
      vi.mocked(post).mock.calls.some(([path]) => path === '/enquiries'),
    ).toBe(false);
    fireEvent.click(screen.getByRole('button', { name: 'Confirm route' }));
    fireEvent.click(screen.getByRole('button', { name: 'Create Enquiry' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/enquiries',
        expect.objectContaining({
          customerId,
          origin: 'My manually entered origin',
          destination: 'Nhava Sheva',
          portOfLoading: 'Laem Chabang',
          portOfDischarge: 'Nhava Sheva',
          polCode: 'THLCH',
          podCode: 'INNSA',
          cargoType: 'FCL',
          containerType: '40HC',
          containerQuantity: 2,
          grossWeight: 18000,
          readyDate: '2026-10-20',
          incoterm: 'FOB',
          pickupRequired: true,
          notes: 'Earliest sailing after cargo readiness',
        }),
      ),
    );
    expect(
      await screen.findByRole('heading', { name: 'Saved enquiry' }),
    ).toBeTruthy();
  });

  it('shows the extraction error without clearing manual fields', async () => {
    vi.mocked(post).mockRejectedValueOnce(new Error('AI service unavailable'));
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/new']}>
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText('Origin *'), {
      target: { value: 'Manual origin' },
    });
    fireEvent.change(screen.getByLabelText('Customer requirements'), {
      target: { value: 'Please quote one container' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Extract with AI/ }));
    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'AI service unavailable',
    );
    expect((screen.getByLabelText('Origin *') as HTMLInputElement).value).toBe(
      'Manual origin',
    );
  });

  it('requires an employee choice for an ambiguous AI port', async () => {
    vi.mocked(api).mockImplementation(async (path: string) => {
      if (path.includes('Laem'))
        return [
          { code: 'THLCH', name: 'Laem Chabang', countryCode: 'TH' },
          { code: 'THABC', name: 'Laem Chabang East', countryCode: 'TH' },
        ];
      if (path.includes('Nhava'))
        return [
          {
            code: 'INNSA',
            name: 'Nhava Sheva (Jawaharlal Nehru)',
            countryCode: 'IN',
          },
        ];
      return { contacts: [] };
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/enquiries/new']}>
          <Routes>
            <Route path="/enquiries/new" element={<EnquiryForm />} />
            <Route path="/enquiries/:id" element={<h1>Saved enquiry</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText('Customer requirements'), {
      target: { value: 'FCL Laem Chabang to Nhava Sheva' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Extract with AI/ }));
    await screen.findByRole('button', { name: 'Confirm route' });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm route' }));
    fireEvent.click(screen.getByRole('button', { name: 'Create Enquiry' }));
    expect(
      await screen.findByText(
        /Select a canonical loading and discharge location/,
      ),
    ).toBeTruthy();
    fireEvent.focus(screen.getByLabelText('Port of loading (POL) *'));
    fireEvent.click(
      await screen.findByRole('option', { name: /Laem Chabang · TH · THLCH/ }),
    );
    expect(screen.getByText('UN/LOCODE THLCH')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Create Enquiry' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/enquiries',
        expect.objectContaining({ polCode: 'THLCH', podCode: 'INNSA' }),
      ),
    );
  });
});
