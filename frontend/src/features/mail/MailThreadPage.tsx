import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { mailApi } from './mailApi';

export default function MailThreadPage() {
  const { id } = useParams();
  const nav = useNavigate();
  const qc = useQueryClient();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const thread = useQuery({
    queryKey: ['mail-thread', id],
    queryFn: () => mailApi.thread(id!),
    enabled: !!id,
  });
  const messages = useQuery({
    queryKey: ['mail-thread-messages', id],
    queryFn: () => mailApi.threadMessages(id!),
    enabled: !!id,
    refetchInterval: 5000,
  });
  const latestReplyable = messages.data
    ?.filter(
      (message) => message.direction === 'INBOUND' || message.status === 'SENT',
    )
    .at(-1);
  const reply = async (messageId: string | undefined, all: boolean) => {
    if (!messageId || busy) return;
    setBusy(true);
    setError('');
    try {
      const draft = await mailApi.replyDraft(messageId, all);
      qc.setQueryData(['mail-draft', draft.id], draft);
      nav(`/mail/compose?draftId=${draft.id}`);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="mail-page">
      <header className="mail-page-header">
        <div>
          <Link to="/mail">← Mail</Link>
          <h1>{thread.data?.normalizedSubject || 'Conversation'}</h1>
          <p>Messages in this mailbox conversation</p>
          {thread.data?.resolutionStatus !== 'NORMAL' &&
            thread.data?.resolutionStatus && (
              <p role="status">
                This conversation needs review:{' '}
                {thread.data.resolutionStatus
                  .replaceAll('_', ' ')
                  .toLowerCase()}
                .
              </p>
            )}
        </div>
        <div className="flex gap-2">
          <button
            disabled={!latestReplyable || busy}
            onClick={() => void reply(latestReplyable?.id, false)}
          >
            Reply
          </button>
          <button
            disabled={!latestReplyable || busy}
            onClick={() => void reply(latestReplyable?.id, true)}
          >
            Reply all
          </button>
        </div>
      </header>
      {error && <p role="alert">{error}</p>}
      {thread.error && <p role="alert">{(thread.error as Error).message}</p>}
      {messages.error && (
        <p role="alert">{(messages.error as Error).message}</p>
      )}
      {messages.isLoading && <p>Loading conversation…</p>}
      {messages.data?.map((message) => (
        <article className="card mail-page-message" key={message.id}>
          <strong>{message.subject || '(No subject)'}</strong>
          <p>
            {message.fromAddress} · {message.direction} ·{' '}
            {new Date(message.at).toLocaleString()}
          </p>
          <p className="whitespace-pre-wrap break-words">{message.bodyText}</p>
          {message.direction === 'INBOUND' && (
            <Link to={`/mail?messageId=${message.id}`}>Open in inbox</Link>
          )}
          {(message.direction === 'INBOUND' || message.status === 'SENT') && (
            <button
              type="button"
              disabled={busy}
              onClick={() => void reply(message.id, false)}
            >
              Reply to this message
            </button>
          )}
        </article>
      ))}
    </div>
  );
}
