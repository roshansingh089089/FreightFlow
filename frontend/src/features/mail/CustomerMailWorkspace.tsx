import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowRight,
  FileText,
  Inbox,
  Mail,
  Plus,
  Search,
  Send,
  Sparkles,
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { apiFetch } from '../../api';
import { Button, Input, Tabs } from '../../shared/components/PageElements';
import { mailApi } from './mailApi';

export type MailItem = {
  id: string;
  fromName: string;
  fromEmail: string;
  subject: string;
  receivedAt: string;
  isRead: boolean;
};
export type MailDetail = {
  id: string;
  fromName: string;
  fromEmail: string;
  toEmail: string;
  subject: string;
  bodyText: string;
  receivedAt: string;
  providerThreadId: string;
  threadId?: string | null;
};
type Props = {
  mode: 'mail' | 'split';
  compact?: boolean;
  items: MailItem[];
  inboxUnreadCount?: number;
  inboxTotalCount?: number;
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  detail?: MailDetail;
  detailLoading: boolean;
  detailError?: string;
  onRetryDetail: () => void;
  connected: boolean;
  loading: boolean;
  loadingMailbox?: string;
  activeMailbox?: string;
  notConnected: boolean;
  inboxError?: string;
  onRetryInbox: () => void;
  syncing: boolean;
  syncError: string;
  syncSummary: string;
  onSync: () => void;
  onManual: () => void;
  onConnect: () => void;
  onUse: () => void;
  onOnboard: () => void;
  onboardingBusy: boolean;
  likelyCustomer: 'checking' | 'found' | 'none' | null;
  matchingCustomer?: {
    id: string;
    companyName: string;
    country: string;
    status: string;
    primaryContact?: { email: string; phone: string };
  };
  customerOptions?: { id: string; companyName: string }[];
};
const formattedDate = (value: string, short = false) =>
  value
    ? new Intl.DateTimeFormat(
        undefined,
        short
          ? {
              month: 'short',
              day: 'numeric',
              hour: 'numeric',
              minute: '2-digit',
            }
          : { dateStyle: 'medium', timeStyle: 'short' },
      ).format(new Date(value))
    : 'Date unavailable';
const initials = (value: string) =>
  value
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0] || '')
    .join('')
    .toUpperCase() || '?';
const folders = [
  { name: 'Inbox', icon: Inbox, active: true },
  { name: 'Sent', icon: Send, path: '/mail/stored?status=SENT' },
  { name: 'Drafts', icon: FileText, path: '/mail/drafts' },
];

function ReplyActionButton({
  all,
  messageId,
  connected,
  busy,
  onReply,
}: {
  all: boolean;
  messageId: string | null;
  connected: boolean;
  busy: boolean;
  onReply: (messageId: string, all: boolean) => void;
}) {
  return (
    <Button
      variant="secondary"
      className="cm-mail-action"
      disabled={!messageId || !connected || busy}
      title={!messageId ? 'Select a message to reply' : undefined}
      onClick={() => messageId && onReply(messageId, all)}
    >
      {all ? 'Reply all' : 'Reply'}
    </Button>
  );
}

export default function CustomerMailWorkspace(props: Props) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [replyBusy, setReplyBusy] = useState(false);
  const [replyError, setReplyError] = useState('');
  const [linkCustomerId, setLinkCustomerId] = useState('');
  const [linkBusy, setLinkBusy] = useState(false);
  const [summary, setSummary] = useState('');
  const [summaryBusy, setSummaryBusy] = useState(false);
  useEffect(() => setSummary(''), [props.detail?.id]);
  useEffect(() => setReplyError(''), [props.selectedId]);
  const summarize = async () => {
    if (!props.detail || summaryBusy) return;
    setSummaryBusy(true);
    setReplyError('');
    try {
      const response = await apiFetch('/ai/mail/draft', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: 'SUMMARIZE',
          context: props.detail.bodyText,
          currentDraft: '',
        }),
      });
      const result = await response.json();
      if (!response.ok)
        throw new Error(result.message || 'Could not summarize message');
      setSummary(result.draft);
    } catch (cause) {
      setReplyError((cause as Error).message);
    } finally {
      setSummaryBusy(false);
    }
  };
  const reply = async (messageId: string | null, all: boolean) => {
    if (!messageId || !props.connected || replyBusy) return;
    setReplyBusy(true);
    setReplyError('');
    try {
      const draft = await mailApi.replyDraft(messageId, all);
      qc.setQueryData(['mail-draft', draft.id], draft);
      navigate(`/mail/compose?draftId=${draft.id}`);
    } catch (error) {
      setReplyError((error as Error).message);
    } finally {
      setReplyBusy(false);
    }
  };
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const [search, setSearch] = useState('');
  const [checked, setChecked] = useState<string[]>([]);
  const [foldersOpen, setFoldersOpen] = useState(false);
  const [moreOpen, setMoreOpen] = useState(false);
  const folderTrigger = useRef<HTMLButtonElement>(null);
  const moreTrigger = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!foldersOpen && !moreOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      if (foldersOpen) folderTrigger.current?.focus();
      else moreTrigger.current?.focus();
      setFoldersOpen(false);
      setMoreOpen(false);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [foldersOpen, moreOpen]);
  const items = useMemo(
    () =>
      props.items.filter(
        (item) =>
          (filter === 'all' || !item.isRead) &&
          `${item.fromName} ${item.fromEmail} ${item.subject}`
            .toLowerCase()
            .includes(search.trim().toLowerCase()),
      ),
    [props.items, filter, search],
  );
  const toggleCheck = (id: string) =>
    setChecked((current) =>
      current.includes(id)
        ? current.filter((value) => value !== id)
        : [...current, id],
    );
  const detail =
    props.detail?.id === props.selectedId ? props.detail : undefined;
  const conversation = useQuery({
    queryKey: ['mail-thread-messages', detail?.threadId],
    queryFn: () => mailApi.threadMessages(detail!.threadId!),
    enabled: !!detail?.threadId,
  });
  const thread = useQuery({
    queryKey: ['mail-thread', detail?.threadId],
    queryFn: () => mailApi.thread(detail!.threadId!),
    enabled: !!detail?.threadId,
  });
  const changeCustomerLink = async (remove: boolean) => {
    if (!detail?.threadId || linkBusy) return;
    setLinkBusy(true);
    setReplyError('');
    try {
      if (remove) await mailApi.unlinkCustomer(detail.threadId);
      else {
        const id = linkCustomerId || props.matchingCustomer?.id;
        if (!id) throw new Error('Choose a customer first');
        await mailApi.linkCustomer(detail.threadId, id);
      }
      await qc.invalidateQueries({
        queryKey: ['mail-thread', detail.threadId],
      });
    } catch (error) {
      setReplyError((error as Error).message);
    } finally {
      setLinkBusy(false);
    }
  };
  const inboundAttachments = useQuery({
    queryKey: ['mail-inbound-attachments', detail?.id],
    queryFn: () => mailApi.inboundAttachments(detail!.id),
    enabled: !!detail?.id,
  });
  const downloadAttachment = async (id: string, filename: string) => {
    if (!detail) return;
    const response = await apiFetch(
      `/mail/messages/${detail.id}/attachments/${id}`,
    );
    if (!response.ok) {
      setReplyError('Could not download attachment');
      return;
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  };
  return (
    <div
      className={`cm-workspace cm-${props.mode}${props.compact ? ' cm-compact' : ''}${props.selectedId ? ' cm-has-selection' : ' cm-no-selection'}`}
    >
      <div className="cm-ribbon" role="toolbar" aria-label="Mail actions">
        <div className="cm-folder-menu">
          <button
            ref={folderTrigger}
            type="button"
            className="cm-folder-trigger"
            aria-expanded={foldersOpen}
            onClick={() => setFoldersOpen((value) => !value)}
          >
            <Inbox size={14} /> Folders
          </button>
          {foldersOpen && (
            <div className="cm-folder-popover" role="menu">
              <button
                type="button"
                role="menuitem"
                aria-current="page"
                onClick={() => setFoldersOpen(false)}
              >
                Inbox
              </button>
              {folders
                .filter((folder) => !folder.active)
                .map((folder) =>
                  folder.path ? (
                    <Link
                      role="menuitem"
                      key={folder.name}
                      to={folder.path}
                      onClick={() => setFoldersOpen(false)}
                    >
                      {folder.name}
                    </Link>
                  ) : (
                    <button
                      type="button"
                      role="menuitem"
                      key={folder.name}
                      disabled
                      title="Coming soon"
                    >
                      {folder.name}
                    </button>
                  ),
                )}
            </div>
          )}
        </div>
        <Link
          className="ui-button ui-button-primary cm-compose"
          to="/mail/compose"
        >
          <Plus size={15} aria-hidden="true" /> Compose
        </Link>
        <div className="cm-more-menu">
          <button
            ref={moreTrigger}
            type="button"
            aria-expanded={moreOpen}
            onClick={() => setMoreOpen((value) => !value)}
          >
            More
          </button>
          {moreOpen && (
            <div className="cm-more-popover" role="menu">
              <Link
                role="menuitem"
                to="/mail/search"
                onClick={() => setMoreOpen(false)}
              >
                Search mail
              </Link>
              <button
                role="menuitem"
                type="button"
                onClick={() => {
                  props.onManual();
                  setMoreOpen(false);
                }}
              >
                Paste email manually
              </button>
              <button
                role="menuitem"
                type="button"
                onClick={() => {
                  props.onConnect();
                  setMoreOpen(false);
                }}
              >
                Settings
              </button>
            </div>
          )}
        </div>
      </div>
      <nav className="cm-app-rail" aria-label="Mail apps">
        <span className="active" title="Mail">
          <Mail size={18} />
        </span>
        <Link to="/customers" title="Customers" aria-label="Customers">
          <Inbox size={18} />
        </Link>
      </nav>
      <nav className="cm-navigation" aria-label="Mail folders">
        <div className="cm-navigation-title">
          <Mail size={16} /> Mailbox
        </div>
        <div className="cm-navigation-group">
          {folders.map((folder) =>
            folder.path ? (
              <Link key={folder.name} to={folder.path} className="cm-folder">
                <folder.icon size={15} />
                <span>{folder.name}</span>
              </Link>
            ) : (
              <button
                type="button"
                key={folder.name}
                className={`cm-folder${folder.active ? ' active' : ''}`}
                disabled={!folder.active}
                title={folder.active ? undefined : 'Coming soon'}
                aria-current={folder.active ? 'page' : undefined}
              >
                <folder.icon size={15} />
                <span>{folder.name}</span>
                {folder.active && props.connected && (
                  <small aria-label="Unread Inbox messages">
                    {props.inboxUnreadCount ??
                      props.items.filter((item) => !item.isRead).length}
                  </small>
                )}
                {!folder.active && <em>Soon</em>}
              </button>
            ),
          )}
        </div>
        <button
          type="button"
          className="cm-manual-link"
          onClick={props.onManual}
        >
          Paste email manually <ArrowRight size={13} />
        </button>
      </nav>
      <section className="cm-list-pane" aria-label="Mail inbox">
        <div className="cm-list-toolbar">
          <div>
            <span className="cm-overline">MAIL INBOX</span>
            <h2>Messages</h2>
            {props.activeMailbox && (
              <small className="cm-connection-status">
                {props.activeMailbox}
              </small>
            )}
          </div>
          <div className="cm-list-actions">
            <Button
              variant="secondary"
              className="cm-sync"
              disabled={!props.connected || props.syncing || props.loading}
              onClick={props.onSync}
            >
              {props.syncing ? 'Syncing…' : 'Sync'}
            </Button>
          </div>
        </div>
        <div className="cm-search">
          <Search size={16} />
          <Input
            aria-label="Search loaded mail"
            placeholder="Search sender or subject"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        <div className="cm-list-tabs" role="group" aria-label="Message filter">
          <Tabs
            label="Message filter"
            options={[
              { value: 'all', label: 'All' },
              { value: 'unread', label: 'Unread' },
            ]}
            value={filter}
            onChange={setFilter}
          />
          {props.connected && (
            <span>
              {items.length} shown ·{' '}
              {props.inboxTotalCount ?? props.items.length} total
            </span>
          )}
        </div>
        {props.loading && (
          <p className="cm-list-state">
            {props.loadingMailbox
              ? `Loading messages for ${props.loadingMailbox}…`
              : 'Loading inbox…'}
          </p>
        )}
        {props.notConnected && (
          <div className="cm-list-state">
            <strong>No mailbox is connected.</strong>
            <p>Connect your account to see real messages.</p>
            <button type="button" onClick={props.onConnect}>
              Open mailbox settings
            </button>
          </div>
        )}
        {props.inboxError && !props.notConnected && (
          <div className="cm-list-state cm-error" role="alert">
            <p>{props.inboxError}</p>
            <button type="button" onClick={props.onRetryInbox}>
              Retry
            </button>
          </div>
        )}
        {props.syncError && (
          <div className="cm-inline-error" role="alert">
            {props.syncError}
          </div>
        )}
        {props.syncSummary && (
          <div className="cm-sync-summary" role="status">
            {props.syncSummary}
          </div>
        )}
        {props.connected &&
          !props.loading &&
          !props.inboxError &&
          props.items.length === 0 && (
            <p className="cm-list-state">
              No stored inbox messages yet. Sync mail to fetch the latest
              messages.
            </p>
          )}
        {props.connected &&
          !props.loading &&
          !props.inboxError &&
          props.items.length > 0 &&
          items.length === 0 && (
            <p className="cm-list-state">
              No messages match this search or filter.
            </p>
          )}
        <div className="cm-message-list">
          {!props.loading &&
            !props.inboxError &&
            items.map((item) => (
              <div
                className={`cm-message-row${props.selectedId === item.id ? ' selected' : ''}${item.isRead ? '' : ' unread'}`}
                key={item.id}
              >
                <input
                  type="checkbox"
                  aria-label={`Select ${item.subject || 'message'}`}
                  checked={checked.includes(item.id)}
                  onChange={() => toggleCheck(item.id)}
                />
                <button
                  type="button"
                  onClick={() => props.onSelect(item.id)}
                  aria-current={
                    props.selectedId === item.id ? 'true' : undefined
                  }
                >
                  <span className="cm-avatar">
                    {initials(item.fromName || item.fromEmail)}
                  </span>
                  <span className="cm-message-copy">
                    <span className="cm-message-top">
                      <strong>
                        {item.fromName || item.fromEmail || 'Unknown sender'}
                      </strong>
                      <time dateTime={item.receivedAt}>
                        {formattedDate(item.receivedAt, true)}
                      </time>
                    </span>
                    <span className="cm-message-subject">
                      {item.subject || '(no subject)'}
                    </span>
                    <span className="cm-message-meta">
                      {item.fromEmail || 'Sender unavailable'}{' '}
                      <em>{item.isRead ? 'Read' : 'Unread'}</em>
                    </span>
                  </span>
                </button>
              </div>
            ))}
        </div>
      </section>
      <div className="cm-reading-column">
        <section className="cm-detail-pane" aria-label="Selected message">
          {replyError && (
            <p className="cm-inline-error" role="alert">
              {replyError}
            </p>
          )}
          {props.selectedId && (
            <button
              type="button"
              className="cm-back-to-list"
              onClick={() => props.onSelect(null)}
            >
              ← Messages
            </button>
          )}
          {!props.selectedId && (
            <div className="cm-detail-empty">
              <Inbox size={31} />
              <h2>Select a message</h2>
              <p>Choose an email to read it and start customer intake.</p>
            </div>
          )}
          {props.selectedId && props.detailLoading && (
            <p className="cm-detail-state">Loading message…</p>
          )}
          {props.selectedId && props.detailError && (
            <div className="cm-detail-state cm-error" role="alert">
              <p>{props.detailError}</p>
              <button type="button" onClick={props.onRetryDetail}>
                Retry
              </button>
            </div>
          )}
          {detail && (
            <>
              <header className="cm-detail-header">
                <span className="cm-avatar large">
                  {initials(detail.fromName || detail.fromEmail)}
                </span>
                <div>
                  <strong>
                    {detail.fromName || detail.fromEmail || 'Unknown sender'}
                  </strong>
                  <span>{detail.fromEmail || 'Sender unavailable'}</span>
                  <small>To: {detail.toEmail || 'Not available'}</small>
                  <time dateTime={detail.receivedAt}>
                    {formattedDate(detail.receivedAt)}
                  </time>
                </div>
              </header>
              <div className="cm-detail-toolbar">
                <Link
                  className="cm-mail-action cm-mail-action-secondary"
                  to={`/enquiries/new?sourceMessageId=${detail.id}${props.matchingCustomer?.id ? `&customerId=${props.matchingCustomer.id}` : ''}`}
                >
                  Create Enquiry
                </Link>
                <ReplyActionButton
                  all={false}
                  messageId={detail.id}
                  connected={props.connected}
                  busy={replyBusy}
                  onReply={(id, all) => void reply(id, all)}
                />
                <ReplyActionButton
                  all
                  messageId={detail.id}
                  connected={props.connected}
                  busy={replyBusy}
                  onReply={(id, all) => void reply(id, all)}
                />
                <button
                  className="cm-mail-action cm-mail-action-ai"
                  type="button"
                  disabled={summaryBusy || !props.connected}
                  onClick={() => void summarize()}
                >
                  <Sparkles size={14} aria-hidden="true" />
                  {summaryBusy ? 'Summarizing…' : 'Summarize with AI'}
                </button>
              </div>
              {summary && (
                <div className="cm-context-card">
                  <strong>AI summary</strong>
                  <p>{summary}</p>
                  <small>Review against the original message.</small>
                </div>
              )}
              <div className="cm-detail-content">
                <div className="cm-detail-subject">
                  <span className="cm-overline">MESSAGE</span>
                  <h2>{detail.subject || '(no subject)'}</h2>
                </div>
                {props.likelyCustomer === 'none' && (
                  <div className="cm-onboard-panel">
                    <div>
                      <strong>No likely customer match found</strong>
                      <p>
                        Review the sender and create a customer record if
                        appropriate.
                      </p>
                    </div>
                    <button
                      type="button"
                      disabled={props.onboardingBusy}
                      onClick={props.onOnboard}
                    >
                      {props.onboardingBusy
                        ? 'Opening…'
                        : 'Create customer from email'}
                    </button>
                  </div>
                )}
                {props.likelyCustomer === 'checking' && (
                  <p className="cm-match-note">
                    Checking the customer directory for this sender…
                  </p>
                )}
                {props.likelyCustomer === 'found' && (
                  <p className="cm-match-note">
                    Possible customer match found. Review the directory before
                    creating a new record.
                  </p>
                )}
                <div className="cm-body">
                  {detail.bodyText || '(No plain-text body available)'}
                </div>
                {!!inboundAttachments.data?.length && (
                  <div className="cm-attachments">
                    <h3>Attachments</h3>
                    {inboundAttachments.data.map((attachment) => (
                      <button
                        type="button"
                        key={attachment.id}
                        onClick={() =>
                          void downloadAttachment(
                            attachment.id,
                            attachment.filename,
                          )
                        }
                      >
                        {attachment.filename} (
                        {Math.ceil(attachment.sizeBytes / 1024)} KB)
                      </button>
                    ))}
                  </div>
                )}
                {conversation.data && conversation.data.length > 1 && (
                  <div
                    className="cm-conversation"
                    aria-label="Conversation history"
                  >
                    <h3>Conversation</h3>
                    {conversation.data.map((message) => (
                      <article
                        className="cm-conversation-message"
                        key={message.id}
                      >
                        <strong>
                          {message.fromAddress || 'Unknown sender'}
                        </strong>
                        <small>
                          {new Date(message.at).toLocaleString()} ·{' '}
                          {message.direction}
                        </small>
                        <p>{message.bodyText}</p>
                      </article>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </section>
        {props.selectedId && (
          <details className="cm-context-pane" aria-label="Business context">
            <summary>Business context</summary>
            {thread.data?.resolutionStatus !== 'NORMAL' &&
              thread.data?.resolutionStatus && (
                <p className="cm-inline-error" role="status">
                  Review this conversation:{' '}
                  {thread.data.resolutionStatus
                    .replaceAll('_', ' ')
                    .toLowerCase()}
                  .
                </p>
              )}
            {detail?.threadId && (
              <div className="cm-context-card">
                <small>CONVERSATION CUSTOMER</small>
                {thread.data?.customerId ? (
                  <>
                    <strong>
                      {props.customerOptions?.find(
                        (c) => c.id === thread.data.customerId,
                      )?.companyName || 'Linked customer'}
                    </strong>
                    <button
                      type="button"
                      disabled={linkBusy}
                      onClick={() => void changeCustomerLink(true)}
                    >
                      Unlink customer
                    </button>
                  </>
                ) : (
                  <>
                    <select
                      aria-label="Choose customer to link"
                      value={linkCustomerId}
                      onChange={(event) =>
                        setLinkCustomerId(event.target.value)
                      }
                    >
                      <option value="">
                        {props.matchingCustomer
                          ? `Suggested: ${props.matchingCustomer.companyName}`
                          : 'Choose customer'}
                      </option>
                      {props.customerOptions?.map((customer) => (
                        <option key={customer.id} value={customer.id}>
                          {customer.companyName}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      disabled={
                        linkBusy || (!linkCustomerId && !props.matchingCustomer)
                      }
                      onClick={() => void changeCustomerLink(false)}
                    >
                      Link customer
                    </button>
                  </>
                )}
              </div>
            )}
            {props.matchingCustomer ? (
              <div className="cm-context-card">
                <small>CUSTOMER</small>
                <strong>{props.matchingCustomer.companyName}</strong>
                <span>
                  {props.matchingCustomer.status} ·{' '}
                  {props.matchingCustomer.country}
                </span>
                {props.matchingCustomer.primaryContact?.email && (
                  <span>{props.matchingCustomer.primaryContact.email}</span>
                )}
                {props.matchingCustomer.primaryContact?.phone && (
                  <span>{props.matchingCustomer.primaryContact.phone}</span>
                )}
                <Link to={`/customers/${props.matchingCustomer.id}`}>
                  View customer <ArrowRight size={13} />
                </Link>
              </div>
            ) : props.likelyCustomer === 'none' ? (
              <div className="cm-context-card">
                <small>NEW SENDER</small>
                <strong>Not a customer yet</strong>
                <p>Review the email and onboard this contact when ready.</p>
                <button
                  onClick={props.onOnboard}
                  disabled={props.onboardingBusy}
                >
                  Create customer from email
                </button>
              </div>
            ) : (
              <p className="cm-context-muted">Checking customer records…</p>
            )}
            <button type="button" onClick={props.onUse}>
              Create customer from email
            </button>
          </details>
        )}
      </div>
    </div>
  );
}
