import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from '../../api';
import { MailApiError, mailApi } from './mailApi';

vi.mock('../../api', () => ({ apiFetch: vi.fn() }));

afterEach(() => vi.clearAllMocks());

describe('mail API errors', () => {
  it('preserves the backend code and status for a failed send', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        code: 'MAILBOX_CHANGED',
        message: 'Reconnect the draft original mailbox',
      }),
    } as Response);

    await expect(mailApi.send('draft-1', 'send-key')).rejects.toMatchObject({
      name: 'MailApiError',
      code: 'MAILBOX_CHANGED',
      status: 409,
      message: 'Reconnect the draft original mailbox',
    } satisfies Partial<MailApiError>);
    expect(apiFetch).toHaveBeenCalledWith(
      '/mail/drafts/draft-1/send',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ idempotencyKey: 'send-key' }),
      }),
    );
  });
});
