import { useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowRight,
  Mail,
  PanelLeft,
  Plus,
  Search,
  Sparkles,
  Users,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { apiFetch } from '../../api';
import { useWorkspaceLayout } from '../../app/layouts/WorkspaceLayout';
import CustomerMailWorkspace, {
  type MailDetail,
  type MailItem,
} from '../mail/CustomerMailWorkspace';
import {
  getCurrentMailConnection,
  getMailCounts,
  mailConnectionKey,
  mailCountsKey,
} from '../mail/mailConnection';

type Mode = 'mail' | 'split' | 'customers';
type Customer = {
  id: string;
  companyName: string;
  customerCode: string;
  customerType: string;
  country: string;
  status: string;
  primaryContact?: { name: string; email: string; phone: string };
};
type Page = { content: Customer[]; totalElements: number; totalPages: number };
class RequestError extends Error {
  constructor(
    message: string,
    readonly code?: string,
  ) {
    super(message);
  }
}

async function request(path: string, options?: RequestInit) {
  const response = await apiFetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new RequestError(
      body?.message || `Request failed (${response.status})`,
      body?.code,
    );
  }
  return response.json();
}
function customerSearch(term: string, size = 20, page = 0) {
  return request(
    `/customers?search=${encodeURIComponent(term)}&size=${size}&page=${page}`,
  ) as Promise<Page>;
}
const clean = (value?: string | null) => value?.trim() || '';
const types = ['EXPORTER', 'IMPORTER', 'FREIGHT_PARTNER', 'OTHER'];
function usePossibleCustomers(terms: string[]) {
  const queries = useQueries({
    queries: [
      ...new Set(
        terms.map((term) => clean(term)).filter((term) => term.length >= 2),
      ),
    ].map((term) => ({
      queryKey: ['customers', 'possible', term],
      queryFn: () => customerSearch(term, 8),
    })),
  });
  return {
    matches: [
      ...new Map(
        queries
          .flatMap((query) => query.data?.content || [])
          .map((customer) => [customer.id, customer]),
      ).values(),
    ],
    isLoading: queries.some((query) => query.isLoading),
    isError: queries.some((query) => query.isError),
    isSuccess: queries.length > 0 && queries.every((query) => query.isSuccess),
  };
}

export default function CustomerWorkspace({
  embeddedMail = false,
}: {
  embeddedMail?: boolean;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { mode: layoutMode, setMode: setLayoutMode } = useWorkspaceLayout();
  const mode: Mode = embeddedMail
    ? 'mail'
    : layoutMode === 'platform'
      ? 'customers'
      : layoutMode;
  const mailMode: 'mail' | 'split' =
    (embeddedMail && layoutMode === 'split') || mode === 'split'
      ? 'split'
      : 'mail';
  const setMode = (value: Mode) =>
    setLayoutMode(value === 'customers' ? 'platform' : value);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [type, setType] = useState('');
  const [country, setCountry] = useState('');
  const [status, setStatus] = useState('');
  const [aiQuestion, setAiQuestion] = useState('');
  const aiQuestionRef = useRef<HTMLInputElement>(null);
  const [aiResults, setAiResults] = useState<{
    entity: string;
    results: { id: string; label: string; detail: string }[];
  } | null>(null);
  const [aiWorking, setAiWorking] = useState(false);
  const [aiError, setAiError] = useState('');
  const [selectedMailId, setSelectedMailId] = useState<string | null>(null);
  const [mailSearchParams] = useSearchParams();
  const requestedMessageId =
    mailSearchParams.get('messageId') ||
    mailSearchParams.get('sourceMessageId');
  const [syncing, setSyncing] = useState(false);
  const [syncError, setSyncError] = useState('');
  const [syncSummary, setSyncSummary] = useState('');
  const currentConnection = useQuery({
    queryKey: mailConnectionKey,
    queryFn: getCurrentMailConnection,
  });
  const activeConnection = currentConnection.data?.active
    ? currentConnection.data
    : null;
  const activeConnectionId = activeConnection?.id;
  const inbox = useQuery({
    queryKey: ['mail', 'messages', activeConnectionId],
    queryFn: () => request('/mail/messages') as Promise<MailItem[]>,
    enabled: !!activeConnectionId,
    refetchInterval: 60_000,
  });
  const mailCounts = useQuery({
    queryKey: mailCountsKey(activeConnectionId),
    queryFn: getMailCounts,
    enabled: !!activeConnectionId,
    refetchInterval: 60_000,
  });
  const mailDetail = useQuery({
    queryKey: ['mail', 'message', activeConnectionId, selectedMailId],
    queryFn: () =>
      request(`/mail/messages/${selectedMailId}`) as Promise<MailDetail>,
    enabled: !!activeConnectionId && !!selectedMailId,
  });
  useEffect(() => {
    setSelectedMailId(requestedMessageId);
  }, [activeConnectionId, requestedMessageId]);
  const mailConnected = !!activeConnection;
  const notConnected =
    !currentConnection.isPending &&
    !currentConnection.isError &&
    !mailConnected;
  const directory = useQuery({
    queryKey: ['customers', 'workspace', search, page, type, country, status],
    queryFn: () =>
      request(
        `/customers?search=${encodeURIComponent(search)}&page=${page}&type=${type}&country=${encodeURIComponent(country)}&status=${status}`,
      ) as Promise<Page>,
    enabled: !embeddedMail,
  });
  const mailMatches = usePossibleCustomers([
    mailDetail.data?.fromEmail || '',
    mailDetail.data?.fromName || '',
  ]);
  const exactMailMatches = mailMatches.matches.filter(
    (customer) =>
      customer.primaryContact?.email?.toLowerCase() ===
      mailDetail.data?.fromEmail?.toLowerCase(),
  );
  const useMail = () => {
    if (mailDetail.data?.id)
      navigate(
        `/customers/new?sourceMessageId=${encodeURIComponent(mailDetail.data.id)}`,
      );
  };
  const syncMail = async () => {
    if (!activeConnectionId) return;
    setSyncing(true);
    setSyncError('');
    setSyncSummary('');
    try {
      const summary = await request('/mail/sync', { method: 'POST' });
      const latest = await queryClient.fetchQuery({
        queryKey: mailConnectionKey,
        queryFn: getCurrentMailConnection,
        staleTime: 0,
      });
      if (!latest?.active || latest.id !== activeConnectionId) {
        setSyncError(
          'The active mailbox changed during sync. Reload the inbox before syncing again.',
        );
        return;
      }
      setSyncSummary(
        `Fetched ${summary.fetched}, added ${summary.inserted}, already stored ${summary.skippedDuplicates}, failed ${summary.failed}.`,
      );
      await queryClient.invalidateQueries({
        queryKey: ['mail', 'messages', activeConnectionId],
      });
      await queryClient.invalidateQueries({
        queryKey: mailCountsKey(activeConnectionId),
      });
    } catch (error) {
      setSyncError(
        error instanceof Error ? error.message : 'Mail sync failed.',
      );
    } finally {
      setSyncing(false);
    }
  };
  const askCustomers = async () => {
    if (!aiQuestion.trim()) {
      aiQuestionRef.current?.focus();
      return;
    }
    setAiWorking(true);
    setAiError('');
    setAiResults(null);
    try {
      setAiResults(
        await request('/ai/search', {
          method: 'POST',
          body: JSON.stringify({ text: aiQuestion }),
        }),
      );
    } catch (error) {
      setAiError(
        error instanceof Error ? error.message : 'AI search is unavailable.',
      );
    } finally {
      setAiWorking(false);
    }
  };
  return (
    <div className="customer-workspace">
      {mode === 'customers' && (
        <div className="cw-heading">
          <div>
            <span className="cw-eyebrow">CUSTOMERS</span>
            <h1>Customers</h1>
            <p>
              {directory.data?.totalElements?.toLocaleString() ?? '—'} customers
              in your workspace
            </p>
          </div>
          <div className="cw-heading-actions">
            <button className="btn secondary" onClick={() => setMode('split')}>
              <PanelLeft size={16} /> Side by side
            </button>
            <Link className="btn primary" to="/customers/new">
              <Plus size={16} /> Onboard customer
            </Link>
          </div>
        </div>
      )}
      {mode === 'customers' && (
        <div className="cw-ai-search">
          <Sparkles size={18} />
          <input
            ref={aiQuestionRef}
            aria-label="Ask AI about customer records"
            placeholder="Ask AI – e.g. Vietnam customers I haven't quoted this month"
            value={aiQuestion}
            onChange={(event) => setAiQuestion(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !aiWorking) void askCustomers();
            }}
          />
          <button
            type="button"
            className="btn primary cw-ai-submit"
            disabled={aiWorking}
            onClick={() => void askCustomers()}
          >
            {aiWorking ? 'Searching…' : 'Ask AI'}
          </button>
        </div>
      )}
      {mode === 'customers' && aiError && (
        <p className="cw-error" role="alert">
          {aiError}
        </p>
      )}
      {mode === 'customers' && aiResults && (
        <div className="cw-ai-results" role="status">
          <strong>{aiResults.results?.length || 0} matching records</strong>
          {aiResults.results?.map((item) => (
            <Link
              key={item.id}
              to={`/${aiResults.entity === 'ENQUIRY' ? 'enquiries' : aiResults.entity === 'QUOTATION' ? 'quotations' : 'customers'}/${item.id}`}
            >
              {item.label}
              <small>{item.detail}</small>
            </Link>
          ))}
        </div>
      )}
      <div
        className={`cw-layout cw-${mode}${selectedMailId ? ' has-mail-selection' : ' no-mail-selection'}`}
      >
        {mode === 'customers' ? (
          <button
            className="cw-rail"
            onClick={() => setMode('split')}
            aria-label="Show mail and customers side by side"
            title="Show side by side"
          >
            <Mail size={19} />
          </button>
        ) : (
          <section className="cw-mail-shell" aria-label="Mail workspace">
            <CustomerMailWorkspace
              mode={mailMode}
              compact={mailMode === 'split'}
              items={activeConnectionId ? inbox.data || [] : []}
              inboxUnreadCount={mailCounts.data?.unread}
              inboxTotalCount={mailCounts.data?.inbox}
              selectedId={selectedMailId}
              onSelect={setSelectedMailId}
              detail={activeConnectionId ? mailDetail.data : undefined}
              detailLoading={mailDetail.isLoading}
              detailError={mailDetail.error?.message}
              onRetryDetail={() => mailDetail.refetch()}
              connected={mailConnected}
              loading={
                currentConnection.isPending ||
                (!!activeConnectionId && inbox.isPending)
              }
              loadingMailbox={activeConnection?.emailAddress}
              activeMailbox={activeConnection?.emailAddress}
              notConnected={notConnected}
              inboxError={
                currentConnection.error?.message || inbox.error?.message
              }
              onRetryInbox={() => {
                void currentConnection.refetch();
                void inbox.refetch();
              }}
              syncing={syncing}
              syncError={syncError}
              syncSummary={syncSummary}
              onSync={syncMail}
              onManual={() => navigate('/customers/new')}
              onConnect={() => window.location.assign('/settings')}
              onUse={useMail}
              onOnboard={useMail}
              onboardingBusy={false}
              likelyCustomer={
                !mailDetail.data
                  ? null
                  : mailMatches.isLoading
                    ? 'checking'
                    : mailMatches.isSuccess
                      ? mailMatches.matches.length
                        ? 'found'
                        : 'none'
                      : null
              }
              matchingCustomer={
                exactMailMatches.length === 1 ? exactMailMatches[0] : undefined
              }
              customerOptions={directory.data?.content}
            />
          </section>
        )}
        {mode === 'mail' ? null : (
          <section
            className="cw-pane cw-directory"
            aria-label="Customer directory"
          >
            <div className="cw-pane-head">
              <span className="cw-pane-icon">
                <Users size={18} />
              </span>
              <div>
                <h2>Customer directory</h2>
                <p>{directory.data?.totalElements ?? '—'} customers</p>
              </div>
              {mode === 'split' && (
                <Link className="cw-pane-add" to="/customers/new">
                  <Plus size={14} /> Add customer
                </Link>
              )}
            </div>
            <div className="cw-directory-tools">
              <label className="cw-search">
                <Search size={16} />
                <input
                  aria-label="Search customers"
                  value={search}
                  onChange={(event) => {
                    setSearch(event.target.value);
                    setPage(0);
                  }}
                  placeholder="Search company, email or phone"
                />
              </label>
              <div className="cw-filters">
                <select
                  aria-label="Customer type"
                  value={type}
                  onChange={(event) => {
                    setType(event.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">All types</option>
                  {types.map((value) => (
                    <option key={value} value={value}>
                      {value.replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
                <input
                  aria-label="Country"
                  value={country}
                  onChange={(event) => {
                    setCountry(event.target.value);
                    setPage(0);
                  }}
                  placeholder="Country"
                />
                <select
                  aria-label="Customer status"
                  value={status}
                  onChange={(event) => {
                    setStatus(event.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">All statuses</option>
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
              </div>
            </div>
            <div className="cw-list-head">
              <span>COMPANY</span>
              <span>PRIMARY CONTACT</span>
              {mode === 'customers' && <span>TYPE / COUNTRY</span>}
              <span>STATUS</span>
              <span />
            </div>
            {directory.isLoading ? (
              <div className="cw-list-state">Loading customers…</div>
            ) : directory.isError ? (
              <div className="cw-list-state cw-error">
                Could not load customers.
              </div>
            ) : directory.data?.content.length ? (
              directory.data.content.map((customer) => (
                <div className="cw-customer-row" key={customer.id}>
                  <div className="cw-company">
                    <span className="cw-avatar">
                      {customer.companyName
                        .split(' ')
                        .slice(0, 2)
                        .map((word) => word[0])
                        .join('')
                        .toUpperCase()}
                    </span>
                    <span>
                      <Link to={`/customers/${customer.id}`}>
                        {customer.companyName}
                      </Link>
                      <small>{customer.customerCode}</small>
                    </span>
                  </div>
                  <div className="cw-contact">
                    <strong>{customer.primaryContact?.name || '—'}</strong>
                    <small>
                      {customer.primaryContact?.email || 'No email'}
                    </small>
                  </div>
                  {mode === 'customers' && (
                    <div className="cw-type">
                      <strong>
                        {customer.customerType?.replaceAll('_', ' ')}
                      </strong>
                      <small>{customer.country}</small>
                    </div>
                  )}
                  <span
                    className={`cw-status ${customer.status === 'ACTIVE' ? 'is-active' : ''}`}
                  >
                    {customer.status}
                  </span>
                  <Link
                    className="cw-row-action"
                    to={`/customers/${customer.id}`}
                    aria-label={`View ${customer.companyName}`}
                  >
                    <ArrowRight size={16} />
                  </Link>
                </div>
              ))
            ) : (
              <div className="cw-list-state">
                No customers match these filters.{' '}
                <Link to="/customers/new">Add a customer</Link>
              </div>
            )}
            {directory.data && directory.data.totalPages > 1 && (
              <div className="cw-pagination">
                <span>
                  Page {page + 1} of {directory.data.totalPages}
                </span>
                <div>
                  <button
                    disabled={page === 0}
                    onClick={() => setPage(page - 1)}
                  >
                    Previous
                  </button>
                  <button
                    disabled={page + 1 >= directory.data.totalPages}
                    onClick={() => setPage(page + 1)}
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </section>
        )}
      </div>
    </div>
  );
}
