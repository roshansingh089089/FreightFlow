import { useQueries, useQuery } from '@tanstack/react-query';
import { apiFetch } from '../../api';
import { SearchableSelect } from '../../shared/components/SearchableSelect';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { mailApi } from './mailApi';

export default function StoredMail() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedId = searchParams.get('messageId');
  const selected = useQuery({
    queryKey: ['stored-mail-detail', selectedId],
    queryFn: () => mailApi.storedDetail(selectedId!),
    enabled: !!selectedId,
  });
  const sentCopy = useQuery({
    queryKey: ['sent-copy-status', selectedId],
    queryFn: () => mailApi.sentCopyStatus(selectedId!),
    enabled: !!selectedId && selected.data?.status === 'SENT',
    refetchInterval: 15000,
  });
  const attachmentQueries = useQueries({
    queries: (selected.data?.attachmentIds || []).map((id) => ({
      queryKey: ['outbound-attachment', id],
      queryFn: () => mailApi.attachmentMetadata(id),
    })),
  });
  const selectMessage = (id: string) => {
    const next = new URLSearchParams(searchParams);
    next.set('messageId', id);
    setSearchParams(next);
  };
  const download = async (id: string, filename: string) => {
    const response = await apiFetch(`/mail/attachments/${id}`);
    if (!response.ok) return;
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  };
  const quotationId = searchParams.get('quotationId');
  const [subject, setSubject] = useState(''),
    [recipient, setRecipient] = useState(''),
    [sender, setSender] = useState(''),
    [status, setStatus] = useState(searchParams.get('status') || ''),
    [from, setFrom] = useState(''),
    [to, setTo] = useState(''),
    [page, setPage] = useState(0);
  const [reviewId, setReviewId] = useState<string | null>(null);
  const [reviewNote, setReviewNote] = useState('');
  const [reviewError, setReviewError] = useState('');
  const [reviewBusy, setReviewBusy] = useState(false);
  const params = new URLSearchParams({
    subject,
    recipient,
    sender,
    status,
    page: String(page),
    size: '20',
  });
  if (from) params.set('from', new Date(from).toISOString());
  if (to) params.set('to', new Date(`${to}T23:59:59`).toISOString());
  if (quotationId) params.set('quotationId', quotationId);
  const results = useQuery({
    queryKey: ['stored-mail', params.toString()],
    queryFn: () => mailApi.stored(params),
    refetchInterval: 5000,
  });
  const reconcile = async (
    resolution: 'CONFIRMED_SUBMITTED' | 'CONFIRMED_NOT_SUBMITTED',
  ) => {
    if (!reviewId || !reviewNote.trim()) return;
    setReviewBusy(true);
    setReviewError('');
    try {
      await mailApi.reconcile(reviewId, resolution, reviewNote);
      setReviewId(null);
      setReviewNote('');
      await results.refetch();
    } catch (cause) {
      setReviewError((cause as Error).message);
    } finally {
      setReviewBusy(false);
    }
  };
  return (
    <div className="mail-page">
      <header className="mail-page-header">
        <div>
          <Link to="/mail">← Mail</Link>
          <h1>{quotationId ? 'Quotation mail' : 'Stored mail'}</h1>
          <p>
            Messages saved or sent from FreightFlow. This is not a full mailbox
            archive.
          </p>
        </div>
        <Link to="/mail/compose">Compose email</Link>
      </header>
      <div className="card mail-page-filter">
        <label>
          Subject
          <input
            value={subject}
            onChange={(event) => {
              setSubject(event.target.value);
              setPage(0);
            }}
          />
        </label>
        <label>
          Recipient
          <input
            value={recipient}
            onChange={(event) => {
              setRecipient(event.target.value);
              setPage(0);
            }}
          />
        </label>
        <label>
          Sender
          <input
            value={sender}
            onChange={(event) => {
              setSender(event.target.value);
              setPage(0);
            }}
          />
        </label>
        <label>
          Status
          <SearchableSelect ariaLabel="Status" value={status} onChange={(next) => { setStatus(next); setPage(0); }} placeholder="All" allowClear options={['DRAFT', 'QUEUED', 'SENDING', 'SENT', 'NEEDS_REVIEW', 'FAILED'].map((value) => ({ value, label: value.replaceAll('_', ' ') }))} />
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
      {results.isLoading && <p>Searching stored mail…</p>}
      {results.error && <p role="alert">{(results.error as Error).message}</p>}
      {results.data?.content.length === 0 && (
        <p>No stored messages match those filters.</p>
      )}
      <div className="card mail-page-list">
        {results.data?.content.map((message) => (
          <div className="mail-page-row" key={message.id}>
            <button type="button" aria-current={selectedId === message.id ? 'true' : undefined} onClick={() => selectMessage(message.id)}><strong>{message.subject || '(No subject)'}</strong></button>
            <p>
              To {message.to.map((item) => item.email).join(', ')} ·{' '}
              {message.status} · {new Date(message.createdAt).toLocaleString()}
            </p>
            {message.status === 'DRAFT' && (
              <Link to={`/mail/compose?draftId=${message.id}`}>
                Continue draft
              </Link>
            )}
            {message.status === 'NEEDS_REVIEW' && (
              <div className="mt-2">
                <p>
                  SMTP outcome is uncertain. Check the provider Sent folder
                  before recording the result.
                </p>
                {reviewId === message.id ? (
                  <div className="grid gap-2">
                    <label>
                      Reconciliation note
                      <input
                        value={reviewNote}
                        onChange={(event) => setReviewNote(event.target.value)}
                      />
                    </label>
                    {reviewError && <p role="alert">{reviewError}</p>}
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        disabled={reviewBusy || !reviewNote.trim()}
                        onClick={() => void reconcile('CONFIRMED_SUBMITTED')}
                      >
                        Confirm submitted
                      </button>
                      <button
                        type="button"
                        disabled={reviewBusy || !reviewNote.trim()}
                        onClick={() =>
                          void reconcile('CONFIRMED_NOT_SUBMITTED')
                        }
                      >
                        Confirm not submitted
                      </button>
                      <button type="button" onClick={() => setReviewId(null)}>
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => {
                      setReviewId(message.id);
                      setReviewError('');
                    }}
                  >
                    Review outcome
                  </button>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
      {selectedId && (
        <section className="card mail-page-message" aria-label="Selected mail">
          {selected.isLoading && <p>Loading message…</p>}
          {selected.error && <p role="alert">Could not open message: {(selected.error as Error).message}</p>}
          {selected.data && <>
            <h2>{selected.data.subject || '(No subject)'}</h2>
            <p><strong>{selected.data.status}</strong> · {selected.data.sentAt ? new Date(selected.data.sentAt).toLocaleString() : 'Not sent'}</p>
            {selected.data.status === 'NEEDS_REVIEW' && <p>SMTP outcome is uncertain. Review the provider Sent folder before resending.</p>}
            {selected.data.status === 'SENT' && sentCopy.data && <p>Provider Sent copy: {sentCopy.data.status === 'SYNCED' ? 'Synced' : sentCopy.data.status === 'RETRY' ? 'Retrying' : 'Pending'}{sentCopy.data.errorCode ? ` (${sentCopy.data.errorCode})` : ''}</p>}
            {selected.data.status === 'SENT' && sentCopy.error && <p role="alert">Could not check provider Sent copy.</p>}
            {selected.data.status === 'FAILED' && <p role="alert">Delivery failed{selected.data.errorCode ? `: ${selected.data.errorCode}` : '.'}</p>}
            <p>From: {selected.data.fromAddress || 'Mailbox not assigned'}</p>
            <p>To: {selected.data.to.map((a) => a.email).join(', ') || '—'}</p>
            {selected.data.cc.length > 0 && <p>CC: {selected.data.cc.map((a) => a.email).join(', ')}</p>}
            {selected.data.bcc.length > 0 && <p>BCC: {selected.data.bcc.map((a) => a.email).join(', ')}</p>}
            <div style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{selected.data.bodyText}</div>
            {selected.data.attachmentIds.length > 0 && <div>
              <h3>Attachments</h3>
              {attachmentQueries.map((query, index) => <p key={selected.data!.attachmentIds[index]}>
                {query.isLoading && 'Loading attachment…'}
                {query.error && <span role="alert">Attachment unavailable</span>}
                {query.data && <button type="button" onClick={() => void download(query.data!.id, query.data!.filename)}>{query.data.filename} ({Math.ceil(query.data.sizeBytes / 1024)} KB)</button>}
              </p>)}
            </div>}
          </>}
        </section>
      )}
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
