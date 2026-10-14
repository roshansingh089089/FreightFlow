import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { WorkspaceLayoutContext } from '../../app/layouts/WorkspaceLayout';
import CustomerWorkspace from './CustomerWorkspace';

vi.mock('../../api', () => ({
  apiFetch: vi.fn(async () => ({
    ok: true,
    json: async () => ({ content: [], totalElements: 0, totalPages: 0 }),
  })),
}));
vi.mock('../mail/mailConnection', () => ({
  mailConnectionKey: ['mail-connection'],
  mailCountsKey: () => ['mail-counts'],
  getCurrentMailConnection: async () => ({ active: false }),
  getMailCounts: async () => ({ unread: 0, inbox: 0 }),
}));

afterEach(cleanup);

describe('customer route Mail composition', () => {
  it('uses the shared split and Mail component classes', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/customers']}>
          <WorkspaceLayoutContext.Provider
            value={{ mode: 'split', setMode: () => {} }}
          >
            <CustomerWorkspace />
          </WorkspaceLayoutContext.Provider>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(
      document.querySelector('.cw-layout.cw-split.ff-mail-split'),
    ).toBeTruthy();
    expect(document.querySelector('.cw-mail-shell.ff-mail-pane')).toBeTruthy();
    expect(
      document.querySelector('.cw-directory.ff-platform-pane'),
    ).toBeTruthy();
    expect(document.querySelector('.mmp-live.split')).toBeTruthy();
    expect(await screen.findByLabelText('Customer directory')).toBeTruthy();
  });
});
