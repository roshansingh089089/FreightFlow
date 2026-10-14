import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AppLayout } from './Shell';

vi.mock('../../shared/lib/workspace', () => ({
  useData: () => ({
    isLoading: false,
    isError: false,
    data: { fullName: 'Roshan Singh', companyName: 'FreightFlow' },
  }),
  api: vi.fn(),
  post: vi.fn(),
}));
vi.mock('../../features/mail/mailConnection', () => ({
  mailConnectionKey: ['mail-connection'],
  mailCountsKey: () => ['mail-counts'],
  getCurrentMailConnection: async () => ({ active: false }),
  getMailCounts: async () => ({ unread: 0 }),
}));
vi.mock('../../features/customers/CustomerWorkspace', () => ({
  default: () => <div>Mail workspace</div>,
}));
vi.mock('../../features/ai/AiAssist', () => ({
  GlobalAiDrawer: () => <div>AI drawer</div>,
}));

afterEach(() => {
  cleanup();
  sessionStorage.clear();
});

function renderShell() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route
              path="/dashboard"
              element={
                <label>
                  Draft note
                  <input aria-label="Draft note" />
                </label>
              }
            />
            <Route path="/enquiries" element={<h1>Enquiry list</h1>} />
            <Route path="/quotations" element={<h1>Quotation list</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('shared workspace shell', () => {
  it('renders one functional global header across routes', async () => {
    renderShell();
    await screen.findByLabelText('Draft note');
    const header = document.querySelector('.app-global-header');
    expect(document.querySelectorAll('.app-global-header')).toHaveLength(1);
    expect(screen.getByRole('group', { name: 'Screen layout' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'AI Assist' }));
    expect(screen.getByText('AI drawer')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Notifications' }));
    expect(
      screen.getByText('No recent notifications in this session.'),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Account menu' }));
    expect(screen.getByLabelText('Search workspace')).toBeTruthy();
    fireEvent.click(screen.getByRole('link', { name: 'Quotations' }));
    expect(
      await screen.findByRole('heading', { name: 'Quotation list' }),
    ).toBeTruthy();
    expect(document.querySelectorAll('.app-global-header')).toHaveLength(1);
    expect(document.querySelector('.app-global-header')).toBe(header);
  });

  it('keeps an in-progress page mounted across layout modes', async () => {
    renderShell();
    const note = await screen.findByLabelText('Draft note');
    fireEvent.change(note, { target: { value: 'Unsaved customer notes' } });
    fireEvent.click(
      within(screen.getByRole('group', { name: 'Screen layout' })).getByRole(
        'button',
        { name: 'Mail' },
      ),
    );
    expect(document.querySelector('.app-shell-mail-mode')).toBeTruthy();
    expect(
      screen.getByRole('link', { name: 'Sales Tracker' }).getAttribute('title'),
    ).toBe('Sales Tracker');
    expect(
      document.querySelector('.app-platform-pane')?.hasAttribute('hidden'),
    ).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Side by side' }));
    expect(document.querySelector('.app-shell-mail-mode')).toBeNull();
    expect(document.querySelector('.app-shell-mail-layout')).toBeTruthy();
    expect(
      document.querySelector('.app-panes-split.ff-mail-split'),
    ).toBeTruthy();
    expect(document.querySelector('.app-mail-pane.ff-mail-pane')).toBeTruthy();
    expect(
      document.querySelector('.app-platform-pane.ff-platform-pane'),
    ).toBeTruthy();
    expect(screen.getByLabelText('Draft note')).toBe(note);
    expect((note as HTMLInputElement).value).toBe('Unsaved customer notes');
    fireEvent.click(
      within(screen.getByRole('group', { name: 'Screen layout' })).getByRole(
        'button',
        { name: 'Platform' },
      ),
    );
    expect(
      document.querySelector('.app-mail-pane')?.hasAttribute('hidden'),
    ).toBe(true);
  });

  it('uses the same sidebar while navigating between routes', async () => {
    renderShell();
    await screen.findByLabelText('Draft note');
    const sidebar = document.querySelector('.app-sidebar');
    fireEvent.click(screen.getByRole('link', { name: 'Sales Tracker' }));
    expect(
      await screen.findByRole('heading', { name: 'Enquiry list' }),
    ).toBeTruthy();
    expect(document.querySelector('.app-sidebar')).toBe(sidebar);
    fireEvent.click(screen.getByRole('link', { name: 'Quotations' }));
    expect(
      await screen.findByRole('heading', { name: 'Quotation list' }),
    ).toBeTruthy();
    expect(document.querySelector('.app-sidebar')).toBe(sidebar);
    fireEvent.click(screen.getByRole('button', { name: 'Go back' }));
    expect(
      await screen.findByRole('heading', { name: 'Enquiry list' }),
    ).toBeTruthy();
  });
});
