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
import { post } from '../../shared/lib/workspace';
import { CustomerForm } from './CustomerPages';

vi.mock('../../shared/lib/workspace', async (importOriginal) => {
  const original =
    await importOriginal<typeof import('../../shared/lib/workspace')>();
  return {
    ...original,
    api: vi.fn(async (path: string) =>
      path === '/tenant/users' ? [] : { content: [] },
    ),
    post: vi.fn(),
  };
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('new customer AI prefill', () => {
  it('fills extracted details into editable fields without replacing an employee edit', async () => {
    vi.mocked(post).mockResolvedValue({
      extracted: {
        customerName: 'FreightFlow',
        contactName: 'Roshan Singh',
        contactEmail: 'roshan@example.com',
        contactPhone: '+91 8774376473',
        customerCountry: 'India',
      },
      warnings: [],
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/customers/new']}>
          <Routes>
            <Route path="/customers/new" element={<CustomerForm />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.change(screen.getByLabelText('Company Name *'), {
      target: { value: 'Corrected Company' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Paste details' }));
    fireEvent.change(screen.getByLabelText('Customer email or signature'), {
      target: {
        value:
          'Best regards,\nRoshan Singh\nFreightFlow\nE: roshan@example.com',
      },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Fill with AI' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/ai/customers/extract', {
        text: 'Best regards,\nRoshan Singh\nFreightFlow\nE: roshan@example.com',
      }),
    );
    expect(await screen.findByDisplayValue('Roshan Singh')).toBeTruthy();
    expect(screen.getByDisplayValue('Corrected Company')).toBeTruthy();
    expect(screen.getByDisplayValue('roshan@example.com')).toBeTruthy();
    expect(
      screen.getByRole('button', { name: 'Create Customer' }),
    ).toBeTruthy();
  });
});
