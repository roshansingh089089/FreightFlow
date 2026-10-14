import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LandingPage from './LandingPage';

function renderLanding(authenticated = false) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: authenticated,
      json: async () => ({ fullName: 'Test User' }),
    }),
  );
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <LandingPage />
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('public landing page', () => {
  it('shows login and signup links, an interactive demo and FAQ', () => {
    renderLanding();
    expect(
      screen.getAllByRole('link', { name: /log in/i })[0].getAttribute('href'),
    ).toBe('/login');
    expect(
      screen
        .getAllByRole('link', { name: /get started/i })[0]
        .getAttribute('href'),
    ).toBe('/signup');
    expect(screen.queryByText('1. Customer enquiry')).toBeNull();
    expect(screen.queryByText('Smart Mail')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Next demo' }));
    expect(screen.getByText('02 / AI EXTRACTION')).toBeTruthy();
    fireEvent.click(
      screen.getByRole('button', {
        name: /Can I connect my existing mailbox/i,
      }),
    );
    expect(screen.getByText(/product roadmap includes Gmail/i)).toBeTruthy();
  });
});
