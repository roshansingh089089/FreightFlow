import { apiFetch } from '../../api';

export type Address = { email: string; displayName?: string };
export class MailApiError extends Error {
  constructor(
    message: string,
    readonly code: string | undefined,
    readonly status: number,
  ) {
    super(message);
    this.name = 'MailApiError';
  }
}
export type Draft = {
  id: string;
  status: string;
  customerId: string | null;
  quotationId: string | null;
  to: Address[];
  cc: Address[];
  bcc: Address[];
  subject: string;
  bodyText: string;
  attachmentIds: string[];
  createdAt: string;
  updatedAt: string;
  errorCode: string | null;
  composeMode: 'NEW' | 'REPLY' | 'REPLY_ALL';
  connectionId: string | null;
  threadId: string | null;
  replyToMessageId: string | null;
  replyToOutboundMessageId: string | null;
  reconciledAt: string | null;
  reconciliationNote: string | null;
  threadingWarning: string | null;
};
export type DraftInput = Pick<
  Draft,
  | 'customerId'
  | 'quotationId'
  | 'to'
  | 'cc'
  | 'bcc'
  | 'subject'
  | 'bodyText'
  | 'attachmentIds'
> & { inReplyTo?: string | null; references?: string | null };
export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
};
export type Conversation = {
  key: string;
  messages: {
    id: string;
    direction: 'INBOUND' | 'OUTBOUND';
    subject: string;
    fromAddress: string;
    toAddress: string;
    status: string;
    at: string;
  }[];
};

async function json<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await apiFetch(path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options?.headers },
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new MailApiError(
      error.message || `Mail request failed (${response.status})`,
      typeof error.code === 'string' ? error.code : undefined,
      response.status,
    );
  }
  return response.status === 204
    ? (undefined as T)
    : (response.json() as Promise<T>);
}
export const mailApi = {
  drafts: (status = 'DRAFT', page = 0) =>
    json<Page<Draft>>(
      `/mail/drafts?status=${encodeURIComponent(status)}&page=${page}`,
    ),
  draft: (id: string) => json<Draft>(`/mail/drafts/${id}`),
  create: (input: DraftInput) =>
    json<Draft>('/mail/drafts', {
      method: 'POST',
      body: JSON.stringify(input),
    }),
  update: (id: string, input: DraftInput) =>
    json<Draft>(`/mail/drafts/${id}`, {
      method: 'PUT',
      body: JSON.stringify(input),
    }),
  discard: (id: string) =>
    json<void>(`/mail/drafts/${id}`, { method: 'DELETE' }),
  send: (id: string, key: string) =>
    json<Draft>(`/mail/drafts/${id}/send`, {
      method: 'POST',
      body: JSON.stringify({ idempotencyKey: key }),
    }),
  reconcile: (
    id: string,
    resolution: 'CONFIRMED_SUBMITTED' | 'CONFIRMED_NOT_SUBMITTED',
    note: string,
  ) =>
    json<Draft>(`/mail/drafts/${id}/reconcile`, {
      method: 'POST',
      body: JSON.stringify({ resolution, note }),
    }),
  replyDraft: (messageId: string, all = false) =>
    json<Draft>(`/mail/drafts/${all ? 'reply-all' : 'reply'}/${messageId}`, {
      method: 'POST',
    }),
  threads: (page = 0) => json<Page<MailThread>>(`/mail/threads?page=${page}`),
  thread: (id: string) => json<MailThread>(`/mail/threads/${id}`),
  linkCustomer: (threadId: string, customerId: string) =>
    json<MailThread>(`/mail/threads/${threadId}/customer`, {
      method: 'POST',
      body: JSON.stringify({ customerId }),
    }),
  unlinkCustomer: (threadId: string) =>
    json<void>(`/mail/threads/${threadId}/customer`, { method: 'DELETE' }),
  threadMessages: (threadId: string) =>
    json<ThreadMessage[]>(`/mail/threads/${threadId}/messages`),
  inboundAttachments: (messageId: string) =>
    json<MailAttachment[]>(`/mail/messages/${messageId}/attachments`),
  customerHistory: (id: string, page = 0) =>
    json<Page<Draft>>(`/customers/${id}/sent-mail?page=${page}`),
  customerThreads: (id: string, page = 0) =>
    json<Page<MailThread>>(`/customers/${id}/mail-threads?page=${page}`),
  customerConversations: (id: string) =>
    json<Conversation[]>(`/customers/${id}/conversations`),
  stored: (params: URLSearchParams) =>
    json<Page<Draft>>(`/mail/stored?${params.toString()}`),
  search: (params: URLSearchParams) =>
    json<Page<MailSearchItem>>(`/mail/search?${params.toString()}`),
  upload: async (file: File) => {
    const body = new FormData();
    body.append('file', file);
    const response = await apiFetch('/mail/attachments', {
      method: 'POST',
      body,
    });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.message || 'Attachment upload failed');
    }
    return response.json() as Promise<{
      id: string;
      filename: string;
      mediaType: string;
      sizeBytes: number;
    }>;
  },
};

export type MailThread = {
  id: string;
  normalizedSubject: string;
  customerId: string | null;
  resolutionStatus: 'NORMAL' | 'AMBIGUOUS_REFERENCE' | 'AMBIGUOUS_CUSTOMER';
  lastActivityAt: string;
};
export type ThreadMessage = {
  id: string;
  direction: 'INBOUND' | 'OUTBOUND';
  fromAddress: string;
  toAddress: string;
  subject: string;
  bodyText: string;
  at: string;
  messageId: string | null;
  status: string | null;
};
export type MailSearchItem = {
  id: string;
  threadId: string | null;
  direction: 'INBOUND' | 'OUTBOUND';
  fromAddress: string;
  recipients: string;
  subject: string;
  status: string;
  read: boolean;
  customerId: string | null;
  quotationId: string | null;
  at: string;
};
export type MailAttachment = {
  id: string;
  filename: string;
  mediaType: string;
  sizeBytes: number;
};
