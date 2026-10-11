import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { mailApi } from './mailApi';

export default function MailDrafts() {
  const drafts = useQuery({
    queryKey: ['mail-drafts'],
    queryFn: () => mailApi.drafts('DRAFT'),
  });
  return (
    <div className="mail-page">
      <header className="mail-page-header">
        <div>
          <Link to="/mail">← Mail</Link>
          <h1>Drafts</h1>
          <p>Messages saved for review before sending.</p>
        </div>
        <div className="flex gap-3">
          <Link to="/mail/stored">Stored mail</Link>
          <Link to="/mail/compose">Compose email</Link>
        </div>
      </header>
      {drafts.isLoading && <p>Loading drafts…</p>}
      {drafts.error && <p role="alert">{(drafts.error as Error).message}</p>}
      {!drafts.isLoading && drafts.data?.content.length === 0 && (
        <p>No drafts yet.</p>
      )}
      <div className="card mail-page-list">
        {drafts.data?.content.map((draft) => (
          <Link
            key={draft.id}
            to={`/mail/compose?draftId=${draft.id}`}
            className="mail-page-row"
          >
            <strong>{draft.subject || '(No subject)'}</strong>
            <p>
              To:{' '}
              {draft.to.map((address) => address.email).join(', ') ||
                'No recipient'}{' '}
              · Updated {new Date(draft.updatedAt).toLocaleString()}
            </p>
          </Link>
        ))}
      </div>
    </div>
  );
}
