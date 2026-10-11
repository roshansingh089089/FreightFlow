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
import { apiFetch } from '../../api';
import EmailComposer from './EmailComposer';
import { MailApiError, mailApi, type Draft } from './mailApi';

vi.mock('./mailApi', () => ({
  MailApiError: class extends Error {
    constructor(
      message: string,
      readonly code: string,
      readonly status: number,
    ) {
      super(message);
    }
  },
  mailApi: {
    draft: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    send: vi.fn(),
    upload: vi.fn(),
    threadMessages: vi.fn(async () => []),
  },
}));
vi.mock('../../api', () => ({ apiFetch: vi.fn() }));

const reply: Draft = {
  id: 'reply-1',
  status: 'DRAFT',
  customerId: null,
  quotationId: null,
  to: [{ email: 'sender@example.com' }],
  cc: [{ email: 'colleague@example.com' }],
  bcc: [],
  subject: 'Re: Freight rates',
  bodyText: '',
  attachmentIds: [],
  createdAt: '',
  updatedAt: '',
  errorCode: null,
  composeMode: 'REPLY_ALL',
  connectionId: 'connection-1',
  threadId: 'thread-1',
  replyToMessageId: 'message-1',
  replyToOutboundMessageId: null,
  reconciledAt: null,
  reconciliationNote: null,
  threadingWarning: null,
};

function mount(path = '/mail/compose') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/mail/compose" element={<EmailComposer />} />
          <Route path="/mail" element={<h1>Mail workspace</h1>} />
          <Route path="/settings" element={<h1>Mailbox settings</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('email composer', () => {
  it('sends the existing quotation draft after editing without losing its PDF or identity', async () => {
    const quotationDraft: Draft = {
      ...reply,
      id: 'quotation-draft-1',
      composeMode: 'NEW',
      quotationId: 'quotation-1',
      subject: 'Quotation QT-1',
      bodyText: 'Please find our quotation attached.',
      attachmentIds: ['pdf-attachment-1'],
      threadId: null,
      replyToMessageId: null,
      replyToOutboundMessageId: null,
    };
    vi.mocked(mailApi.draft).mockResolvedValue(quotationDraft);
    vi.mocked(mailApi.update).mockImplementation(async (_id, input) => ({
      ...quotationDraft,
      ...input,
    }));
    vi.mocked(mailApi.send).mockResolvedValue({
      ...quotationDraft,
      status: 'QUEUED',
    });
    mount('/mail/compose?draftId=quotation-draft-1');
    expect(await screen.findByDisplayValue('Quotation QT-1')).toBeTruthy();
    expect(screen.getByText(/Attachment pdf-atta/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Message'), {
      target: { value: 'Updated quotation message.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    await waitFor(() =>
      expect(mailApi.update).toHaveBeenCalledWith(
        'quotation-draft-1',
        expect.objectContaining({
          quotationId: 'quotation-1',
          subject: 'Quotation QT-1',
          bodyText: 'Updated quotation message.',
          attachmentIds: ['pdf-attachment-1'],
          to: [{ email: 'sender@example.com' }],
        }),
      ),
    );
    await waitFor(() =>
      expect(mailApi.send).toHaveBeenCalledWith(
        'quotation-draft-1',
        expect.any(String),
      ),
    );
    expect(mailApi.create).not.toHaveBeenCalled();
  });

  it.each([
    ['MAIL_NOT_CONNECTED', 'Connect an IMAP/SMTP mailbox'],
    ['MAILBOX_CHANGED', 'different mailbox'],
    ['MAIL_SEND_UNAVAILABLE', 'connected provider cannot send'],
  ])('shows %s with a mailbox settings action', async (code, explanation) => {
    vi.mocked(mailApi.draft).mockResolvedValue(reply);
    vi.mocked(mailApi.update).mockImplementation(async (_id, input) => ({
      ...reply,
      ...input,
    }));
    vi.mocked(mailApi.send).mockRejectedValue(
      new MailApiError('Backend message', code, 409),
    );
    mount('/mail/compose?draftId=reply-1');
    expect(await screen.findByDisplayValue('sender@example.com')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Message'), {
      target: { value: 'Please review the rates.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    expect(await screen.findByText(new RegExp(explanation))).toBeTruthy();
    expect(
      screen
        .getByRole('link', { name: 'Open mailbox settings' })
        .getAttribute('href'),
    ).toBe('/settings');
  });

  it('autosaves a new draft once and sends only after an explicit click', async () => {
    vi.mocked(mailApi.create).mockImplementation(async (input) => ({
      ...reply,
      id: 'new-1',
      composeMode: 'NEW',
      ...input,
    }));
    vi.mocked(mailApi.send).mockResolvedValue({
      ...reply,
      id: 'new-1',
      status: 'QUEUED',
    });
    mount();
    fireEvent.change(screen.getByLabelText('To'), {
      target: { value: 'sender@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Subject'), {
      target: { value: 'Freight rates' },
    });
    fireEvent.change(screen.getByLabelText('Message'), {
      target: { value: 'Please review.' },
    });
    await waitFor(() => expect(mailApi.create).toHaveBeenCalledTimes(1), {
      timeout: 3500,
    });
    expect(mailApi.send).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(screen.getByText('Saved')).toBeTruthy());
    expect(mailApi.create).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    await waitFor(() =>
      expect(mailApi.send).toHaveBeenCalledWith('new-1', expect.any(String)),
    );
  });

  it('restores reply-all recipients and keeps the original subject and thread on update', async () => {
    vi.mocked(mailApi.draft).mockResolvedValue(reply);
    vi.mocked(mailApi.update).mockImplementation(async (_id, input) => ({
      ...reply,
      ...input,
    }));
    vi.mocked(mailApi.send).mockResolvedValue({ ...reply, status: 'QUEUED' });
    mount('/mail/compose?draftId=reply-1');
    expect(await screen.findByDisplayValue('sender@example.com')).toBeTruthy();
    expect(screen.getByDisplayValue('colleague@example.com')).toBeTruthy();
    expect(screen.getByDisplayValue('Re: Freight rates')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Message'), {
      target: { value: 'Revised response.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));
    await waitFor(() =>
      expect(mailApi.update).toHaveBeenCalledWith(
        'reply-1',
        expect.objectContaining({
          subject: 'Re: Freight rates',
          bodyText: 'Revised response.',
          cc: [{ email: 'colleague@example.com' }],
        }),
      ),
    );
    expect(mailApi.send).toHaveBeenCalledWith('reply-1', expect.any(String));
  });

  it('writes an AI reply inline and preserves recipients and subject', async () => {
    vi.mocked(mailApi.draft).mockResolvedValue(reply);
    vi.mocked(mailApi.threadMessages).mockResolvedValue([
      {
        id: 'message-1',
        direction: 'INBOUND',
        fromAddress: 'sender@example.com',
        toAddress: '',
        subject: 'Freight rates',
        bodyText: 'Please advise on rates.',
        at: '',
        messageId: null,
        status: null,
      },
    ]);
    vi.mocked(apiFetch).mockResolvedValue({
      ok: true,
      json: async () => ({
        draft:
          'Dear Customer,\n\nThank you for your enquiry. Please confirm the cargo details.\n\nKind regards,',
        subject: 'Changed subject',
      }),
    } as Response);
    mount('/mail/compose?draftId=reply-1');
    expect(await screen.findByDisplayValue('sender@example.com')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'AI Assist' }));
    fireEvent.click(screen.getByRole('button', { name: 'Draft Email' }));
    await waitFor(() =>
      expect(
        (screen.getByLabelText('Message') as HTMLTextAreaElement).value,
      ).toContain('Thank you for your enquiry'),
    );
    expect(screen.queryByLabelText('AI draft preview')).toBeNull();
    expect(screen.queryByText('Changed subject')).toBeNull();
    expect(
      JSON.parse(vi.mocked(apiFetch).mock.calls[0][1]?.body as string),
    ).toEqual(
      expect.objectContaining({
        composeMode: 'REPLY_ALL',
        subject: 'Re: Freight rates',
        context: expect.stringContaining('Please advise on rates.'),
      }),
    );
    fireEvent.change(screen.getByLabelText('Message'), {
      target: { value: 'Edited suggested reply.' },
    });
    expect(screen.getByDisplayValue('Edited suggested reply.')).toBeTruthy();
    expect(screen.getByDisplayValue('Re: Freight rates')).toBeTruthy();
    expect(screen.getByDisplayValue('colleague@example.com')).toBeTruthy();
    expect(mailApi.send).not.toHaveBeenCalled();
  });

  it('generates inline, edits, saves and reopens a new draft', async () => {
    let stored: Draft | undefined;
    vi.mocked(mailApi.create).mockImplementation(async (input) => {
      stored = { ...reply, ...input, id: 'new-ai-1', composeMode: 'NEW' };
      return stored;
    });
    vi.mocked(mailApi.update).mockImplementation(async (_id, input) => {
      stored = { ...stored!, ...input };
      return stored;
    });
    vi.mocked(mailApi.draft).mockImplementation(async () => stored!);
    vi.mocked(apiFetch).mockResolvedValue({
      ok: true,
      json: async () => ({
        draft:
          'Dear Customer,\n\nPlease confirm the shipment details.\n\nKind regards,',
        subject: 'Shipment details request',
      }),
    } as Response);
    mount();
    fireEvent.change(screen.getByLabelText('To'), {
      target: { value: 'sender@example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'AI Assist' }));
    fireEvent.change(screen.getByLabelText('Instructions for AI draft'), {
      target: { value: 'Ask for shipment details' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Draft Email' }));
    await waitFor(() =>
      expect(
        (screen.getByLabelText('Message') as HTMLTextAreaElement).value,
      ).toContain('Please confirm the shipment details'),
    );
    expect(mailApi.send).not.toHaveBeenCalled();
    expect(screen.getByDisplayValue('Shipment details request')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Message'), {
      target: {
        value:
          'Dear Customer,\n\nPlease confirm package count and weight.\n\nKind regards,',
      },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    await waitFor(() =>
      expect(stored?.bodyText).toContain('package count and weight'),
    );
    cleanup();
    mount('/mail/compose?draftId=new-ai-1');
    await waitFor(() => expect(mailApi.draft).toHaveBeenCalledWith('new-ai-1'));
    await waitFor(() =>
      expect(
        (screen.getByLabelText('Message') as HTMLTextAreaElement).value,
      ).toContain('package count and weight'),
    );
    expect(screen.getByDisplayValue('Shipment details request')).toBeTruthy();
    expect(mailApi.send).not.toHaveBeenCalled();
  });

  it('rewrites inline and reverts consecutive AI edits without changing metadata', async () => {
    let generated = 0;
    vi.mocked(apiFetch).mockImplementation(
      async () =>
        ({
          ok: true,
          json: async () => ({
            draft: ++generated === 1 ? 'First AI rewrite' : 'Second AI rewrite',
            subject: 'Alternative subject',
          }),
        }) as Response,
    );
    mount();
    fireEvent.change(screen.getByLabelText('To'), {
      target: { value: 'recipient@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Subject'), {
      target: { value: 'Original subject' },
    });
    fireEvent.change(screen.getByLabelText('Message'), {
      target: { value: 'Manual\n\nbody  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'AI Assist' }));
    fireEvent.click(screen.getByRole('button', { name: 'Rewrite' }));
    await waitFor(() =>
      expect(screen.getByDisplayValue('First AI rewrite')).toBeTruthy(),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Improve Tone' }));
    await waitFor(() =>
      expect(screen.getByDisplayValue('Second AI rewrite')).toBeTruthy(),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Revert AI edit' }));
    expect(screen.getByDisplayValue('First AI rewrite')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Revert AI edit' }));
    expect(
      (screen.getByLabelText('Message') as HTMLTextAreaElement).value,
    ).toBe('Manual\n\nbody  ');
    expect(screen.getByDisplayValue('Original subject')).toBeTruthy();
    expect(screen.getByDisplayValue('recipient@example.com')).toBeTruthy();
    expect(
      screen.getByText(/Suggested subject: Alternative subject/),
    ).toBeTruthy();
    expect(mailApi.send).not.toHaveBeenCalled();
  });

  it('keeps AI tools and Send accessible with expanded Cc/Bcc', () => {
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Cc/Bcc' }));
    expect(screen.getByLabelText('Cc')).toBeTruthy();
    expect(screen.getByLabelText('Bcc')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'AI Assist' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Send' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'AI Assist' }));
    expect(screen.getByRole('button', { name: 'Draft Email' })).toBeTruthy();
    const composer = screen.getByRole('dialog', { name: 'Email composer' });
    const sections = Array.from(composer.querySelector('form')!.children);
    expect(sections.map((section) => section.className)).toEqual([
      'email-composer-fields',
      'email-ai-toolbar',
      'email-editor',
      'email-composer-extras',
      'email-composer-footer',
    ]);
  });

  it('replaces a nonempty subject only after the user chooses the suggestion', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      ok: true,
      json: async () => ({ draft: 'AI body', subject: 'Suggested subject' }),
    } as Response);
    mount();
    fireEvent.change(screen.getByLabelText('Subject'), {
      target: { value: 'Manual subject' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'AI Assist' }));
    fireEvent.click(screen.getByRole('button', { name: 'Draft Email' }));
    await waitFor(() =>
      expect(screen.getByDisplayValue('AI body')).toBeTruthy(),
    );
    expect(screen.getByDisplayValue('Manual subject')).toBeTruthy();
    fireEvent.click(
      screen.getByRole('button', { name: 'Use suggested subject' }),
    );
    expect(screen.getByDisplayValue('Suggested subject')).toBeTruthy();
  });

  it('autosaves an inline AI edit without sending', async () => {
    vi.mocked(mailApi.create).mockImplementation(async (input) => ({
      ...reply,
      ...input,
      id: 'ai-auto-1',
      composeMode: 'NEW',
    }));
    vi.mocked(mailApi.update).mockImplementation(async (_id, input) => ({
      ...reply,
      ...input,
      id: 'ai-auto-1',
      composeMode: 'NEW',
    }));
    vi.mocked(apiFetch).mockResolvedValue({
      ok: true,
      json: async () => ({ draft: 'AI message body', subject: '' }),
    } as Response);
    mount();
    fireEvent.change(screen.getByLabelText('To'), {
      target: { value: 'recipient@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Subject'), {
      target: { value: 'Existing subject' },
    });
    await waitFor(() => expect(mailApi.create).toHaveBeenCalled(), {
      timeout: 3500,
    });
    fireEvent.click(screen.getByRole('button', { name: 'AI Assist' }));
    fireEvent.click(screen.getByRole('button', { name: 'Draft Email' }));
    await waitFor(() =>
      expect(screen.getByDisplayValue('AI message body')).toBeTruthy(),
    );
    await waitFor(
      () =>
        expect(mailApi.update).toHaveBeenCalledWith(
          'ai-auto-1',
          expect.objectContaining({
            bodyText: 'AI message body',
            subject: 'Existing subject',
            to: [{ email: 'recipient@example.com' }],
          }),
        ),
      { timeout: 3500 },
    );
    expect(mailApi.send).not.toHaveBeenCalled();
  });

  it('discards stale AI output when the user edits during generation', async () => {
    let complete!: (response: Response) => void;
    vi.mocked(apiFetch).mockReturnValue(
      new Promise((resolve) => {
        complete = resolve;
      }),
    );
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'AI Assist' }));
    fireEvent.click(screen.getByRole('button', { name: 'Draft Email' }));
    fireEvent.change(screen.getByLabelText('Message'), {
      target: { value: 'Newer manual text' },
    });
    complete({
      ok: true,
      json: async () => ({ draft: 'Stale AI text' }),
    } as Response);
    expect(
      await screen.findByText(/draft changed while AI was writing/i),
    ).toBeTruthy();
    expect(screen.getByDisplayValue('Newer manual text')).toBeTruthy();
  });

  it('shows an AI failure and retries without changing the manual draft', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'Assistant unavailable' }),
    } as Response);
    mount();
    fireEvent.change(screen.getByLabelText('Message'), {
      target: { value: 'My own message.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'AI Assist' }));
    fireEvent.click(screen.getByRole('button', { name: 'Draft Email' }));
    expect(await screen.findByText('Assistant unavailable')).toBeTruthy();
    expect(
      (screen.getByLabelText('Message') as HTMLTextAreaElement).value,
    ).toBe('My own message.');
    expect(mailApi.send).not.toHaveBeenCalled();
  });
});
