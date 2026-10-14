import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
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

function MailHarness({
  mode = 'mail',
  likelyCustomer = null,
  onOnboard = () => {},
  matchingCustomer,
}: {
  mode?: 'mail' | 'split';
  likelyCustomer?: 'checking' | 'found' | 'none' | null;
  onOnboard?: () => void;
  matchingCustomer?: {
    id: string;
    companyName: string;
    country: string;
    status: string;
  };
}) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  return (
    <CustomerMailWorkspace
      mode={mode}
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
      onOnboard={onOnboard}
      onboardingBusy={false}
      likelyCustomer={likelyCustomer}
      matchingCustomer={matchingCustomer}
    />
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('mail workspace', () => {
  it('keeps folders in the Full Mail grid at a 1280px desktop width', () => {
    const bounds = vi
      .spyOn(HTMLElement.prototype, 'getBoundingClientRect')
      .mockReturnValue({ width: 1084 } as DOMRect);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    try {
      render(
        <QueryClientProvider client={client}>
          <MemoryRouter initialEntries={['/mail']}>
            <MailHarness />
          </MemoryRouter>
        </QueryClientProvider>,
      );
      expect(
        document.querySelector('.mmp-live[data-folder-placement=collapsed]'),
      ).toBeNull();
      expect(
        screen.getByRole('navigation', { name: 'Mail folders' }),
      ).toBeTruthy();
    } finally {
      bounds.mockRestore();
    }
  });

  it('uses measured Mail width to open a folder overlay when the reader needs space', () => {
    const bounds = vi
      .spyOn(HTMLElement.prototype, 'getBoundingClientRect')
      .mockReturnValue({ width: 900 } as DOMRect);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    try {
      render(
        <QueryClientProvider client={client}>
          <MemoryRouter initialEntries={['/mail']}>
            <MailHarness />
          </MemoryRouter>
        </QueryClientProvider>,
      );
      const workspace = document.querySelector('.mmp-live') as HTMLElement;
      expect(workspace.dataset.folderPlacement === 'collapsed').toBe(true);
      fireEvent.click(
        screen.getAllByRole('button', { name: 'Toggle folders' })[0],
      );
      expect(workspace.dataset.folderPlacement === 'overlay').toBe(true);
      fireEvent.click(
        screen.getAllByRole('button', { name: 'Toggle folders' })[0],
      );
      expect(workspace.dataset.folderPlacement === 'collapsed').toBe(true);
    } finally {
      bounds.mockRestore();
    }
  });

  it('keeps a manual folder choice predictable as the Mail container resizes', () => {
    let measuredWidth = 900;
    const bounds = vi
      .spyOn(HTMLElement.prototype, 'getBoundingClientRect')
      .mockImplementation(() => ({ width: measuredWidth }) as DOMRect);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    try {
      render(
        <QueryClientProvider client={client}>
          <MemoryRouter initialEntries={['/mail']}>
            <MailHarness />
          </MemoryRouter>
        </QueryClientProvider>,
      );
      const workspace = document.querySelector('.mmp-live') as HTMLElement;
      expect(workspace.dataset.folderPlacement === 'collapsed').toBe(true);
      fireEvent.click(
        screen.getAllByRole('button', { name: 'Toggle folders' })[0],
      );
      expect(workspace.dataset.folderPlacement === 'overlay').toBe(true);
      measuredWidth = 1100;
      fireEvent(window, new Event('resize'));
      expect(workspace.dataset.folderPlacement === 'inline').toBe(true);
      fireEvent.click(
        screen.getAllByRole('button', { name: 'Toggle folders' })[0],
      );
      measuredWidth = 900;
      fireEvent(window, new Event('resize'));
      measuredWidth = 1100;
      fireEvent(window, new Event('resize'));
      expect(workspace.dataset.folderPlacement === 'collapsed').toBe(true);
    } finally {
      bounds.mockRestore();
    }
  });

  it('keeps the Meridian-style Full Mail controls tied to real message data', () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/mail']}>
          <MailHarness />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    const workspace = document.querySelector('.mmp-live') as HTMLElement;
    const ribbon = within(workspace).getByLabelText('Mail actions');
    const openComposer = vi.fn();
    window.addEventListener('ff-open-mail-composer', openComposer, {
      once: true,
    });
    fireEvent.click(within(ribbon).getByRole('button', { name: /New mail/i }));
    expect(openComposer).toHaveBeenCalledOnce();
    expect(
      within(ribbon).getByRole('textbox', { name: 'Search loaded mail' }),
    ).toBeTruthy();
    const foldersButton = within(ribbon).getByRole('button', {
      name: 'Toggle folders',
    });
    const wasCollapsed = workspace.dataset.folderPlacement === 'collapsed';
    fireEvent.click(foldersButton);
    expect(workspace.dataset.folderPlacement === 'collapsed').toBe(
      !wasCollapsed,
    );
    expect(foldersButton.getAttribute('aria-expanded')).toBe(
      String(wasCollapsed),
    );
    expect(within(workspace).queryByText('roshan@example.com')).toBeNull();
    const selectAll = within(workspace).getByRole('checkbox', {
      name: 'Select all shown messages',
    }) as HTMLInputElement;
    fireEvent.click(selectAll);
    expect(selectAll.checked).toBe(true);
    expect(
      (
        within(workspace).getByRole('checkbox', {
          name: 'Select Freight enquiry',
        }) as HTMLInputElement
      ).checked,
    ).toBe(true);
  });

  it('shows AI onboarding in the full reading pane for an unknown sender', () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/mail']}>
          <MailHarness likelyCustomer="none" />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.click(
      screen.getByRole('button', { name: /Roshan Singh.*Freight enquiry/ }),
    );
    const destinations = screen.getByRole('navigation', {
      name: 'Mail destinations',
    });
    expect(within(destinations).getAllByRole('link')).toHaveLength(3);
    fireEvent.click(
      screen.getAllByRole('button', { name: 'Toggle folders' })[0],
    );
    expect(
      screen.getByRole('navigation', { name: 'Mail folders' }),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'AI insights' }));
    const context = within(screen.getByLabelText('FreightFlow intelligence'));
    expect(context.getByText(/Source email: Freight enquiry/)).toBeTruthy();
    expect(
      context.getByRole('button', {
        name: 'Onboard Customer with AI',
      }),
    ).toBeTruthy();
  });

  it('expands the selected email directly beneath its row in split mode and offers AI onboarding for an unknown sender', () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const onboard = vi.fn();
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/mail']}>
          <MailHarness mode="split" likelyCustomer="none" onOnboard={onboard} />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    const rowButton = screen.getByRole('button', {
      name: /Roshan Singh.*Freight enquiry/,
    });
    expect(
      screen.queryByRole('navigation', { name: 'Mail destinations' }),
    ).toBeNull();
    fireEvent.click(rowButton);
    expect(rowButton.getAttribute('aria-expanded')).toBe('true');
    const row = rowButton.closest('.mmp-row');
    const expansion = row?.nextElementSibling;
    expect(expansion?.classList.contains('mmp-inline')).toBe(true);
    const inline = within(expansion as HTMLElement);
    expect(inline.getByText('Please quote one container.')).toBeTruthy();
    fireEvent.click(
      inline.getByRole('button', { name: 'Onboard Customer with AI' }),
    );
    expect(onboard).toHaveBeenCalledOnce();
  });

  it('keeps linked customer and enquiry actions in the reading pane', () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/mail']}>
          <MailHarness
            likelyCustomer="found"
            matchingCustomer={{
              id: 'customer-1',
              companyName: 'Acme Freight',
              country: 'India',
              status: 'ACTIVE',
            }}
          />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.click(
      screen.getByRole('button', { name: /Roshan Singh.*Freight enquiry/ }),
    );
    fireEvent.click(screen.getByRole('button', { name: 'AI insights' }));
    const reader = within(screen.getByLabelText('FreightFlow intelligence'));
    expect(
      reader.getByRole('link', { name: 'View Customer' }).getAttribute('href'),
    ).toBe('/customers/customer-1');
    expect(
      reader.getByRole('link', { name: 'Create Enquiry' }).getAttribute('href'),
    ).toContain('sourceMessageId=message-1');
  });

  it('keeps the selected message when switching between full and inline layouts', () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    function LayoutHarness() {
      const [mode, setMode] = useState<'mail' | 'split'>('mail');
      return (
        <>
          <button
            type="button"
            onClick={() =>
              setMode((current) => (current === 'mail' ? 'split' : 'mail'))
            }
          >
            Switch layout
          </button>
          <MailHarness mode={mode} />
        </>
      );
    }
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/mail']}>
          <LayoutHarness />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.click(
      screen.getByRole('button', { name: /Roshan Singh.*Freight enquiry/ }),
    );
    expect(
      within(screen.getByLabelText('Selected message')).getByText(
        'Please quote one container.',
      ),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Switch layout' }));
    const selectedRow = screen.getByRole('button', {
      name: /Roshan Singh.*Freight enquiry/,
    });
    expect(selectedRow.getAttribute('aria-current')).toBe('true');
    expect(selectedRow.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByLabelText('Expanded message')).toBeNull();
    fireEvent.click(selectedRow);
    expect(selectedRow.getAttribute('aria-expanded')).toBe('true');
    expect(
      within(screen.getByLabelText('Expanded message')).getByText(
        'Please quote one container.',
      ),
    ).toBeTruthy();
    fireEvent.click(selectedRow);
    expect(selectedRow.getAttribute('aria-expanded')).toBe('false');
    expect(selectedRow.getAttribute('aria-current')).toBe('true');
    expect(screen.queryByLabelText('Expanded message')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Switch layout' }));
    expect(
      within(screen.getByLabelText('Selected message')).getByText(
        'Please quote one container.',
      ),
    ).toBeTruthy();
  });

  it('shows the same attachment from the mail API in both readers', async () => {
    vi.mocked(mailApi.inboundAttachments).mockResolvedValue([
      {
        id: 'attachment-1',
        filename: 'rates.pdf',
        mediaType: 'application/pdf',
        sizeBytes: 1024,
      },
    ]);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    function LayoutHarness() {
      const [mode, setMode] = useState<'mail' | 'split'>('mail');
      return (
        <>
          <button type="button" onClick={() => setMode('split')}>
            Side by side
          </button>
          <MailHarness mode={mode} />
        </>
      );
    }
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/mail']}>
          <LayoutHarness />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    fireEvent.click(
      screen.getByRole('button', { name: /Roshan Singh.*Freight enquiry/ }),
    );
    expect(
      await within(screen.getByLabelText('Selected message')).findByRole(
        'button',
        { name: 'rates.pdf (1 KB)' },
      ),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Side by side' }));
    expect(screen.queryByLabelText('Expanded message')).toBeNull();
    fireEvent.click(
      screen.getByRole('button', { name: /Roshan Singh.*Freight enquiry/ }),
    );
    expect(
      within(screen.getByLabelText('Expanded message')).getByRole('button', {
        name: 'rates.pdf (1 KB)',
      }),
    ).toBeTruthy();
    expect(mailApi.inboundAttachments).toHaveBeenCalledWith('message-1');
  });

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
      screen.getByRole('heading', { name: 'Select an item to read' }),
    ).toBeTruthy();
    fireEvent.click(
      screen.getByRole('button', { name: /Roshan Singh.*Freight enquiry/ }),
    );
    expect(document.querySelector('.mmp-intelligence')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Ai' }));
    fireEvent.click(screen.getByRole('button', { name: 'AI insights' }));
    expect(document.querySelector('.mmp-intelligence')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Close intelligence' }));
    fireEvent.click(screen.getByRole('button', { name: 'View' }));
    const initiallyCollapsed = !!document.querySelector(
      '.mmp-live[data-folder-placement=collapsed]',
    );
    fireEvent.click(
      screen.getByRole('button', {
        name: initiallyCollapsed ? 'Show folders' : 'Hide folders',
      }),
    );
    expect(
      !!document.querySelector('.mmp-live[data-folder-placement=collapsed]'),
    ).toBe(!initiallyCollapsed);
    expect(
      within(screen.getByLabelText('Selected message')).getByText(
        'Please quote one container.',
      ),
    ).toBeTruthy();
    expect(mailApi.replyDraft).not.toHaveBeenCalled();
    const openReply = vi.fn();
    window.addEventListener('ff-open-mail-composer', openReply, { once: true });
    fireEvent.click(
      within(screen.getByLabelText('Selected message')).getByRole('button', {
        name: 'Reply all',
      }),
    );
    await waitFor(() =>
      expect(mailApi.replyDraft).toHaveBeenCalledWith('message-1', true),
    );
    await waitFor(() => expect(openReply).toHaveBeenCalledOnce());
    expect((openReply.mock.calls[0][0] as CustomEvent).detail.draftId).toBe(
      'draft-1',
    );
  });
});
