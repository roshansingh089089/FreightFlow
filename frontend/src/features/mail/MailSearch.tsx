import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { mailApi } from './mailApi';
import { SearchableSelect } from '../../shared/components/SearchableSelect';

export default function MailSearch() {
  const [subject, setSubject] = useState('');
  const [sender, setSender] = useState('');
  const [recipient, setRecipient] = useState('');
  const [direction, setDirection] = useState('');
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const params = new URLSearchParams({
    subject,
    sender,
    recipient,
    direction,
    status,
    page: String(page),
  });
  if (from) params.set('from', new Date(from).toISOString());
  if (to) params.set('to', new Date(`${to}T23:59:59`).toISOString());
  const results = useQuery({
    queryKey: ['mail-search', params.toString()],
    queryFn: () => mailApi.search(params),
  });
  const field = (
    label: string,
    value: string,
    setter: (value: string) => void,
  ) => (
    <label>
      {label}
      <input
        value={value}
        onChange={(event) => {
          setter(event.target.value);
          setPage(0);
        }}
      />
    </label>
  );
  return (
    <div className="mail-page">
      <header className="mail-page-header">
        <div>
          <Link to="/mail">← Mail</Link>
          <h1>Search mail</h1>
          <p>
            Search imported inbox messages and FreightFlow drafts and sent mail.
          </p>
        </div>
      </header>
      <div className="card mail-page-filter">
        {field('Subject', subject, setSubject)}
        {field('Sender', sender, setSender)}
        {field('Recipient', recipient, setRecipient)}
        <label>
          Direction
          <SearchableSelect ariaLabel="Direction" value={direction} onChange={(next) => { setDirection(next); setPage(0); }} placeholder="All" allowClear options={[{ value: 'INBOUND', label: 'Received' }, { value: 'OUTBOUND', label: 'Outgoing' }]} />
        </label>
        <label>
          Status
          <SearchableSelect ariaLabel="Status" value={status} onChange={(next) => { setStatus(next); setPage(0); }} placeholder="All" allowClear options={['RECEIVED', 'DRAFT', 'QUEUED', 'SENDING', 'SENT', 'NEEDS_REVIEW', 'FAILED'].map((value) => ({ value, label: value.replaceAll('_', ' ') }))} />
        </label>
        <label>
          From
          <input
            type="date"
            value={from}
            onChange={(event) => {
              setFrom(event.target.value);
              setPage(0);
            }}
          />
        </label>
        <label>
          To
          <input
            type="date"
            value={to}
            onChange={(event) => {
              setTo(event.target.value);
              setPage(0);
            }}
          />
        </label>
      </div>
      {results.isLoading && <p>Searching mail…</p>}
      {results.error && <p role="alert">{(results.error as Error).message}</p>}
      {results.data?.content.length === 0 && (
        <p>No messages match those filters.</p>
      )}
      <div className="card mail-page-list">
        {results.data?.content.map((message) => (
          <div
            className="mail-page-row"
            key={`${message.direction}-${message.id}`}
          >
            <strong>{message.subject || '(No subject)'}</strong>
            <p>
              {message.fromAddress} · {message.direction} · {message.status} ·{' '}
              {new Date(message.at).toLocaleString()}
            </p>
            {message.status === 'DRAFT' && (
              <Link to={`/mail/compose?draftId=${message.id}`}>
                Continue draft
              </Link>
            )}
            {message.direction === 'INBOUND' && (
              <Link to={`/mail?messageId=${message.id}`}>Open message</Link>
            )}
          </div>
        ))}
      </div>
      {results.data && results.data.totalPages > 1 && (
        <div className="flex gap-3 mt-4">
          <button disabled={page === 0} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <span>
            Page {page + 1} of {results.data.totalPages}
          </span>
          <button
            disabled={page + 1 >= results.data.totalPages}
            onClick={() => setPage(page + 1)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
