import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { useState } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import CustomerMailWorkspace, {
  type MailDetail,
} from './CustomerMailWorkspace';
import { mailApi } from './mailApi';

vi.mock('./mailApi', () => ({
  mailApi: {
    replyDraft: vi.fn(async () => ({ id: 'draft-1' })),
    inboundAttachments: vi.fn(async () => []),
  },
}));

const detail: MailDetail = {
  id: 'message-1',
  fromName: 'Roshan Singh',
  fromEmail: 'roshan@example.com',
  toEmail: 'desk@example.com',
  subject: 'Freight enquiry',
  bodyText: 'Please quote one container.',
  receivedAt: '2026-10-10T08:30:00Z',
  providerThreadId: '',
};

function MailHarness() {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  return (
    <CustomerMailWorkspace
      mode="mail"
      items={[{ ...detail, isRead: false }]}
      selectedId={selectedId}
      onSelect={setSelectedId}
      detail={selectedId ? detail : undefined}
      detailLoading={false}
      onRetryDetail={() => {}}
      connected
      loading={false}
      notConnected={false}
      onRetryInbox={() => {}}
      syncing={false}
      syncError=""
      syncSummary=""
      onSync={() => {}}
      onManual={() => {}}
      onConnect={() => {}}
      onUse={() => {}}
      onOnboard={() => {}}
      onboardingBusy={false}
      likelyCustomer={null}
    />
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('mail workspace', () => {
  it('opens the selected message and creates a reply draft only when requested', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/mail']}>
          <Routes>
            <Route path="/mail" element={<MailHarness />} />
            <Route path="/mail/compose" element={<h1>Editable composer</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(
      screen.getByRole('heading', { name: 'Select a message' }),
    ).toBeTruthy();
    fireEvent.click(
      screen.getByRole('button', { name: /Roshan Singh.*Freight enquiry/ }),
    );
    expect(screen.getByText('Please quote one container.')).toBeTruthy();
    expect(mailApi.replyDraft).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Reply all' }));
    await waitFor(() =>
      expect(mailApi.replyDraft).toHaveBeenCalledWith('message-1', true),
    );
    expect(
      await screen.findByRole('heading', { name: 'Editable composer' }),
    ).toBeTruthy();
  });
});
