import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Dashboard } from './DashboardPage';

vi.mock('../../shared/lib/workspace', () => ({
  useData: () => ({ data: { fullName: 'Roshan Singh' } }),
  date: (value: string) => value,
  fmt: (value: number) => String(value),
  api: vi.fn(async (path: string) => {
    if (path === '/dashboard/summary')
      return {
        newEnquiries: 2,
        quotesPending: 1,
        quotesSent: 3,
        customers: 5,
        recentEnquiries: [
          {
            id: 'enquiry-1',
            enquiryCode: 'ENQ-001',
            customerName: 'Northstar Trading',
            origin: 'Shanghai',
            destination: 'Hamburg',
            status: 'NEW',
            createdAt: '2026-10-11T08:00:00Z',
          },
        ],
        expiringQuotes: [],
      };
    if (path.includes('/quotations')) return { content: [], totalElements: 0 };
    if (path.includes('status=ACTIVE'))
      return { content: [], totalElements: 4 };
    return { content: [], totalElements: 0 };
  }),
}));

afterEach(cleanup);

describe('Dashboard', () => {
  it('shows API metrics and routes the AI action to the existing assistant', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const onOpen = vi.fn();
    window.addEventListener('ff-open-global-ai', onOpen);
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <Dashboard />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect((await screen.findAllByText('ENQ-001')).length).toBeGreaterThan(0);
    expect(
      screen.getByText('Active Customers').parentElement?.textContent,
    ).toContain('4');
    expect(
      screen.getByText(/Deterministic summary based on current records/),
    ).toBeTruthy();
    expect(
      screen.getByRole('link', { name: /New Quotation/ }).getAttribute('href'),
    ).toBe('/enquiries');
    fireEvent.click(screen.getByRole('button', { name: /Ask FreightFlow AI/ }));
    expect(onOpen).toHaveBeenCalledOnce();
    window.removeEventListener('ff-open-global-ai', onOpen);
  });
});
