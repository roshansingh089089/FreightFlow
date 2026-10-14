import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Inbox, Plus, RefreshCw, Sparkles } from 'lucide-react';
import {
  Fragment,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { Link } from 'react-router-dom';
import { apiFetch } from '../../api';
import { SearchableSelect } from '../../shared/components/SearchableSelect';
import { mailApi } from './mailApi';
import { MailMessageContent } from './MailReadingParts';
import {
  MailAppRail,
  MailFolderPane,
  MailInboxHeader,
  MailInlineReader,
  MailIntelligencePanel,
  MailMessageList,
  MailMessageRow,
  MailReadingPane,
  MailRibbon,
  MailBusinessActions as PresentationBusinessActions,
} from './MeridianMailPresentation';
import { openMailComposer } from './openComposer';

export type MailItem = {
  id: string;
  fromName: string;
  fromEmail: string;
  subject: string;
  receivedAt: string;
  isRead: boolean;
  preview?: string | null;
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
const messageGroup = (value: string) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Earlier';
  const today = new Date();
  if (date.toDateString() === today.toDateString()) return 'Today';
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
  return new Intl.DateTimeFormat(undefined, {
    month: 'long',
    day: 'numeric',
  }).format(date);
};
const messageTime = (value: string) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(
    undefined,
    date.toDateString() === new Date().toDateString()
      ? { hour: 'numeric', minute: '2-digit' }
      : { month: 'short', day: 'numeric' },
  ).format(date);
};
function MailBusinessActions({
  detail,
  customerId,
  customerName,
  customer,
  likelyCustomer,
  enquiryPath,
  onboardingBusy,
  onOnboard,
}: {
  detail: MailDetail | undefined;
  customerId: string | undefined;
  customerName: string;
  customer: Props['matchingCustomer'];
  likelyCustomer: Props['likelyCustomer'];
  enquiryPath: string;
  onboardingBusy: boolean;
  onOnboard: () => void;
}) {
  if (
    !detail ||
    (!customerId && likelyCustomer !== 'none' && likelyCustomer !== 'found')
  )
    return null;
  return (
    <PresentationBusinessActions>
      {customerId ? (
        <>
          <div>
            <strong>{customerName}</strong>
            <p>
              {[
                customer?.status,
                customer?.country,
                customer?.primaryContact?.email,
              ]
                .filter(Boolean)
                .join(' · ') || 'Customer linked to this conversation'}
            </p>
            <div className="mmp-business-buttons">
              <Link to={`/customers/${customerId}`}>View Customer</Link>
              <Link to={enquiryPath}>Create Enquiry</Link>
            </div>
          </div>
        </>
      ) : likelyCustomer === 'none' ? (
        <>
          <div>
            <strong>New sender — not a customer yet</strong>
            <p>
              From {detail.fromName || detail.fromEmail} &lt;{detail.fromEmail}
              &gt;. AI can extract company, position, phone and address into an
              editable customer form.
            </p>
            <small>Source email: {detail.subject || '(no subject)'}</small>
            <div className="mmp-business-buttons">
              <button
                type="button"
                disabled={onboardingBusy}
                onClick={onOnboard}
              >
                {onboardingBusy ? 'Opening…' : 'Onboard Customer with AI'}
              </button>
              <Link to={enquiryPath}>Create Enquiry</Link>
            </div>
          </div>
        </>
      ) : (
        <>
          <div>
            <strong>Possible customer match</strong>
            <p>
              Review directory matches and duplicate checks before creating a
              customer.
            </p>
            <div className="mmp-business-buttons">
              <Link to="/customers">Review Customers</Link>
              <button
                type="button"
                disabled={onboardingBusy}
                onClick={onOnboard}
              >
                Onboard Customer with AI
              </button>
            </div>
          </div>
        </>
      )}
    </PresentationBusinessActions>
  );
}

export default function CustomerMailWorkspace(props: Props) {
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
    if (document.querySelector('.mail-floating-composer')) {
      setReplyError(
        'Close the current draft before replying to another message. Your draft will be saved.',
      );
      window.dispatchEvent(new Event('ff-restore-mail-composer'));
      return;
    }
    setReplyBusy(true);
    setReplyError('');
    try {
      const draft = await mailApi.replyDraft(messageId, all);
      qc.setQueryData(['mail-draft', draft.id], draft);
      openMailComposer({ draftId: draft.id });
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
  const workspaceRef = useRef<HTMLDivElement>(null);
  // The compact tracks reserve 52 + 180 + 330px, two dividers, and a 500px reader.
  const inlineFolderWidth = 960;
  const [mailWidth, setMailWidth] = useState(0);
  const [folderOverride, setFolderOverride] = useState<boolean | null>(null);
  const folderPlacement =
    folderOverride === false
      ? 'collapsed'
      : folderOverride === true || mailWidth >= inlineFolderWidth
        ? mailWidth >= inlineFolderWidth
          ? 'inline'
          : 'overlay'
        : 'collapsed';
  const folderCollapsed = folderPlacement === 'collapsed';
  const [insightsOpen, setInsightsOpen] = useState(
    () =>
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(min-width: 1371px)').matches,
  );
  const [sortNewest, setSortNewest] = useState(true);
  const [moreOpen, setMoreOpen] = useState(false);
  const [ribbonTab, setRibbonTab] = useState<'home' | 'view' | 'ai'>('home');
  const [density, setDensity] = useState<'comfortable' | 'compact'>('compact');
  const [readingOpen, setReadingOpen] = useState(true);
  const [expandedMessageId, setExpandedMessageId] = useState<string | null>(
    null,
  );
  const folderTrigger = useRef<HTMLButtonElement>(null);
  const moreTrigger = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (props.mode === 'split') setExpandedMessageId(null);
  }, [props.mode]);
  useLayoutEffect(() => {
    const element = workspaceRef.current;
    if (!element) return;
    const measure = () => {
      const width = element.getBoundingClientRect().width;
      if (width > 0) setMailWidth(width);
    };
    measure();
    if (typeof ResizeObserver === 'undefined') {
      window.addEventListener('resize', measure);
      return () => window.removeEventListener('resize', measure);
    }
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [props.mode]);
  useEffect(() => {
    if (props.mode !== 'mail' || folderPlacement !== 'overlay') return;
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setFolderOverride(false);
        folderTrigger.current?.focus();
      }
    };
    document.addEventListener('keydown', close);
    return () => document.removeEventListener('keydown', close);
  }, [folderPlacement, props.mode]);
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
      props.items
        .filter(
          (item) =>
            (filter === 'all' || !item.isRead) &&
            `${item.fromName} ${item.fromEmail} ${item.subject}`
              .toLowerCase()
              .includes(search.trim().toLowerCase()),
        )
        .sort((a, b) =>
          sortNewest
            ? Date.parse(b.receivedAt) - Date.parse(a.receivedAt)
            : Date.parse(a.receivedAt) - Date.parse(b.receivedAt),
        ),
    [props.items, filter, search, sortNewest],
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
  const linkedCustomerId =
    thread.data?.customerId || props.matchingCustomer?.id;
  const linkedCustomerName =
    props.matchingCustomer?.companyName ||
    props.customerOptions?.find((customer) => customer.id === linkedCustomerId)
      ?.companyName ||
    'Linked customer';
  const enquiryPath = detail
    ? `/enquiries/new?sourceMessageId=${encodeURIComponent(detail.id)}${linkedCustomerId ? `&customerId=${encodeURIComponent(linkedCustomerId)}` : ''}`
    : '/enquiries/new';
  const mailBanner = (
    <MailBusinessActions
      detail={detail}
      customerId={linkedCustomerId}
      customerName={linkedCustomerName}
      customer={props.matchingCustomer}
      likelyCustomer={props.likelyCustomer}
      enquiryPath={enquiryPath}
      onboardingBusy={props.onboardingBusy}
      onOnboard={props.onOnboard}
    />
  );
  const toggleFolders = () => {
    if (props.mode === 'split') setFoldersOpen((value) => !value);
    else setFolderOverride(folderCollapsed ? true : false);
  };
  const unread =
    props.inboxUnreadCount ?? props.items.filter((item) => !item.isRead).length;
  const renderFolders = (overlay = false) => (
    <MailFolderPane
      unread={unread}
      connected={props.connected}
      activeMailbox={props.activeMailbox}
      overlay={overlay}
      onClose={toggleFolders}
      onManual={props.onManual}
    />
  );
  const renderReader = (inline: boolean) => (
    <MailReadingPane inline={inline}>
      {replyError && (
        <p className="mmp-live-error" role="alert">
          {replyError}
        </p>
      )}
      {!detail && !props.selectedId && (
        <div className="mmp-reader-empty">
          <Inbox size={30} />
          <h2>Select an item to read</h2>
          <p>Choose a message from the list.</p>
        </div>
      )}
      {props.selectedId && props.detailLoading && (
        <p className="mmp-live-state">Loading message…</p>
      )}
      {props.selectedId && props.detailError && (
        <div className="mmp-live-error" role="alert">
          <p>{props.detailError}</p>
          <button type="button" onClick={props.onRetryDetail}>
            Retry
          </button>
        </div>
      )}
      {detail && (
        <>
          <div className="mmp-reading-head">
            {props.mode === 'mail' && (
              <button
                type="button"
                className="mmp-mobile-back"
                onClick={() => props.onSelect(null)}
              >
                ← Back to inbox
              </button>
            )}
            <h2>{detail.subject || '(no subject)'}</h2>
            <div className="mmp-sender">
              <span className="mmp-avatar large">
                {initials(detail.fromName || detail.fromEmail)}
              </span>
              <div>
                <strong>
                  {detail.fromName || detail.fromEmail || 'Unknown sender'}
                </strong>
                <span>&lt;{detail.fromEmail}&gt;</span>
                <span>To: {detail.toEmail || 'Not available'}</span>
                <span>{formattedDate(detail.receivedAt)}</span>
              </div>
            </div>
            <div className="mmp-reading-actions">
              <button
                type="button"
                className="mmp-reply-action"
                disabled={!props.connected || replyBusy}
                onClick={() => void reply(detail.id, false)}
              >
                Reply
              </button>
              <button
                type="button"
                className="mmp-reply-action"
                disabled={!props.connected || replyBusy}
                onClick={() => void reply(detail.id, true)}
              >
                Reply all
              </button>
            </div>
          </div>
          <div className="mmp-reading-body">
            <button
              className="mmp-summary"
              type="button"
              disabled={summaryBusy || !props.connected}
              onClick={() => void summarize()}
            >
              <Sparkles size={14} />
              {summaryBusy ? 'Summarizing…' : 'Summarize'}
            </button>
            {summary && (
              <div className="mmp-live-summary">
                <strong>AI summary</strong>
                <p>{summary}</p>
                <small>Review against the original message.</small>
              </div>
            )}
            <MailMessageContent
              detail={detail}
              banner={props.mode === 'split' ? mailBanner : undefined}
              attachments={inboundAttachments.data}
              onDownload={(id, filename) =>
                void downloadAttachment(id, filename)
              }
            />
            {conversation.data && conversation.data.length > 1 && (
              <section
                className="mmp-conversation"
                aria-label="Conversation history"
              >
                <h3>Conversation</h3>
                {conversation.data.map((message) => (
                  <div key={message.id}>
                    <strong>{message.fromAddress || 'Unknown sender'}</strong>
                    <small>
                      {new Date(message.at).toLocaleString()} ·{' '}
                      {message.direction}
                    </small>
                    <p>{message.bodyText}</p>
                  </div>
                ))}
              </section>
            )}
            {detail.threadId && (
              <details className="mmp-live-link">
                <summary>Conversation customer</summary>
                {thread.data?.customerId ? (
                  <>
                    <strong>{linkedCustomerName}</strong>
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
                    <SearchableSelect
                      ariaLabel="Choose customer to link"
                      value={linkCustomerId}
                      onChange={setLinkCustomerId}
                      placeholder="Choose customer"
                      options={(props.customerOptions || []).map(
                        (customer) => ({
                          value: customer.id,
                          label: customer.companyName,
                        }),
                      )}
                    />
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
              </details>
            )}
          </div>
        </>
      )}
    </MailReadingPane>
  );
  return (
    <div
      ref={workspaceRef}
      className={`mmp-app mmp-live ${props.mode === 'mail' ? 'full' : 'split'}${props.selectedId ? ' has-selection' : ''}`}
      data-preview="live-mail"
      data-folder-placement={
        props.mode === 'mail'
          ? folderPlacement
          : foldersOpen
            ? 'overlay'
            : 'collapsed'
      }
    >
      <section
        className="mmp-mail"
        aria-label="Mail workspace"
        data-measure="mail"
      >
        {props.mode === 'mail' && (
          <div className="mmp-page-heading">
            <div>
              <h1>Mail</h1>
              <p>
                Manage your freight emails, enquiries and customer
                communication.
              </p>
            </div>
            <div className="mmp-heading-actions">
              <button
                type="button"
                disabled={!props.connected || props.syncing || props.loading}
                onClick={props.onSync}
              >
                <RefreshCw size={16} /> {props.syncing ? 'Syncing…' : 'Sync'}
              </button>
              <button
                type="button"
                className="primary"
                onClick={() => openMailComposer()}
              >
                <Plus size={17} /> New mail
              </button>
            </div>
          </div>
        )}
        <MailRibbon
          tab={ribbonTab}
          onTabChange={setRibbonTab}
          search={search}
          onSearchChange={setSearch}
          onFolderToggle={toggleFolders}
          onIntelligence={() => setInsightsOpen((value) => !value)}
          folderExpanded={
            props.mode === 'split' ? foldersOpen : !folderCollapsed
          }
          triggerRef={folderTrigger}
        >
          {ribbonTab === 'home' ? (
            <>
              <button
                type="button"
                className="mmp-tool primary"
                onClick={() => openMailComposer()}
              >
                <Plus size={16} /> New mail
              </button>
              <span className="mmp-sep" />
              <button
                type="button"
                className="mmp-tool mmp-reply-action"
                disabled={!props.selectedId || !props.connected || replyBusy}
                onClick={() => void reply(props.selectedId, false)}
              >
                Reply
              </button>
              <button
                type="button"
                className="mmp-tool mmp-reply-action"
                disabled={!props.selectedId || !props.connected || replyBusy}
                onClick={() => void reply(props.selectedId, true)}
              >
                Reply all
              </button>
              <span className="mmp-sep" />
              <button
                type="button"
                className="mmp-tool"
                disabled={!props.connected || props.syncing || props.loading}
                onClick={props.onSync}
              >
                <RefreshCw size={16} />
                {props.syncing ? 'Syncing…' : 'Sync'}
              </button>
            </>
          ) : ribbonTab === 'view' ? (
            <>
              <span className="mmp-cmdlabel">Layout</span>
              <button
                type="button"
                className="mmp-tool"
                onClick={toggleFolders}
              >
                {folderCollapsed ? 'Show folders' : 'Hide folders'}
              </button>
              {props.mode === 'mail' && (
                <button
                  type="button"
                  className="mmp-tool"
                  onClick={() => setReadingOpen((value) => !value)}
                >
                  {readingOpen ? 'Hide reading pane' : 'Show reading pane'}
                </button>
              )}
            </>
          ) : (
            <>
              <span className="mmp-cmdlabel">Assistant</span>
              <button
                type="button"
                className="mmp-tool"
                disabled={!detail || summaryBusy || !props.connected}
                onClick={() => void summarize()}
              >
                <Sparkles size={16} />
                {summaryBusy ? 'Summarizing…' : 'Summarize'}
              </button>
              {detail && (
                <Link className="mmp-tool" to={enquiryPath}>
                  Extract shipment details
                </Link>
              )}
              {detail && props.likelyCustomer === 'none' && (
                <button
                  type="button"
                  className="mmp-tool"
                  disabled={props.onboardingBusy}
                  onClick={props.onOnboard}
                >
                  Onboard Customer with AI
                </button>
              )}
            </>
          )}
        </MailRibbon>
        <div className="mmp-mail-body">
          {props.mode === 'mail' && <MailAppRail unread={unread} />}
          {props.mode === 'mail' &&
            folderPlacement === 'inline' &&
            renderFolders()}
          {props.mode === 'mail' &&
            folderPlacement === 'overlay' &&
            renderFolders(true)}
          {props.mode === 'split' && foldersOpen && renderFolders(true)}
          <MailMessageList>
            <MailInboxHeader
              items={items}
              checked={checked}
              onCheckedChange={setChecked}
              filter={filter}
              onFilterChange={setFilter}
              sortNewest={sortNewest}
              onSortChange={() => setSortNewest((value) => !value)}
              onFolderToggle={toggleFolders}
            />
            {props.loading && (
              <p className="mmp-live-state">
                {props.loadingMailbox
                  ? `Loading messages for ${props.loadingMailbox}…`
                  : 'Loading inbox…'}
              </p>
            )}
            {props.notConnected && (
              <div className="mmp-live-state">
                <strong>No mailbox is connected.</strong>
                <p>Connect your account to see real messages.</p>
                <button type="button" onClick={props.onConnect}>
                  Open mailbox settings
                </button>
              </div>
            )}
            {props.inboxError && !props.notConnected && (
              <div className="mmp-live-error" role="alert">
                <p>{props.inboxError}</p>
                <button type="button" onClick={props.onRetryInbox}>
                  Retry
                </button>
              </div>
            )}
            {props.syncError && (
              <div className="mmp-live-error" role="alert">
                {props.syncError}
              </div>
            )}
            {props.syncSummary && (
              <div className="mmp-live-state" role="status">
                {props.syncSummary}
              </div>
            )}
            {props.connected &&
              !props.loading &&
              !props.inboxError &&
              props.items.length === 0 && (
                <p className="mmp-live-state">
                  No stored inbox messages yet. Sync mail to fetch the latest
                  messages.
                </p>
              )}
            {props.connected &&
              !props.loading &&
              !props.inboxError &&
              props.items.length > 0 &&
              items.length === 0 && (
                <p className="mmp-live-state">
                  No messages match this search or filter.
                </p>
              )}
            {!props.loading &&
              !props.inboxError &&
              items.map((item, index) => (
                <Fragment key={item.id}>
                  {(index === 0 ||
                    messageGroup(items[index - 1].receivedAt) !==
                      messageGroup(item.receivedAt)) && (
                    <div className="mmp-day">
                      {messageGroup(item.receivedAt)}
                    </div>
                  )}
                  <MailMessageRow
                    item={item}
                    selected={props.selectedId === item.id}
                    expanded={expandedMessageId === item.id}
                    checked={checked.includes(item.id)}
                    mode={props.mode}
                    preview={
                      item.preview ||
                      (detail?.id === item.id ? detail.bodyText : undefined)
                    }
                    hasAttachments={
                      props.selectedId === item.id &&
                      !!inboundAttachments.data?.length
                    }
                    onSelect={(id) => {
                      if (props.mode === 'split')
                        setExpandedMessageId((current) =>
                          current === id ? null : id,
                        );
                      if (props.selectedId !== id) props.onSelect(id);
                    }}
                    onToggle={toggleCheck}
                  />
                  {props.mode === 'split' && expandedMessageId === item.id && (
                    <MailInlineReader>{renderReader(true)}</MailInlineReader>
                  )}
                </Fragment>
              ))}
          </MailMessageList>
          {props.mode === 'mail' && readingOpen && renderReader(false)}
          {insightsOpen && detail && (
            <MailIntelligencePanel onClose={() => setInsightsOpen(false)}>
              <div className="mmp-context-card">
                <strong>Related to</strong>
                {linkedCustomerId ? (
                  <Link to={`/customers/${linkedCustomerId}`}>
                    {linkedCustomerName}
                  </Link>
                ) : (
                  <p>No customer linked to this conversation.</p>
                )}
              </div>
              <div className="mmp-context-card">
                <strong>AI summary</strong>
                {!summary && (
                  <p>Summarize this message when you need a quick review.</p>
                )}
                <button
                  type="button"
                  className="mmp-summary"
                  disabled={!detail || summaryBusy || !props.connected}
                  onClick={() => void summarize()}
                >
                  {summaryBusy ? 'Summarizing…' : 'Summarize message'}
                </button>
                {summary && <p>{summary}</p>}
              </div>
              {thread.data?.resolutionStatus &&
                thread.data.resolutionStatus !== 'NORMAL' && (
                  <p role="status">
                    Review this conversation:{' '}
                    {thread.data.resolutionStatus
                      .replaceAll('_', ' ')
                      .toLowerCase()}
                    .
                  </p>
                )}
              {detail?.threadId && (
                <div className="mmp-live-link">
                  <strong>Conversation customer</strong>
                  {thread.data?.customerId ? (
                    <>
                      <p>{linkedCustomerName}</p>
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
                      <SearchableSelect
                        ariaLabel="Choose customer to link"
                        value={linkCustomerId}
                        onChange={setLinkCustomerId}
                        placeholder="Choose customer"
                        options={(props.customerOptions || []).map(
                          (customer) => ({
                            value: customer.id,
                            label: customer.companyName,
                          }),
                        )}
                      />
                      <button
                        type="button"
                        disabled={
                          linkBusy ||
                          (!linkCustomerId && !props.matchingCustomer)
                        }
                        onClick={() => void changeCustomerLink(false)}
                      >
                        Link customer
                      </button>
                    </>
                  )}
                </div>
              )}
              <div className="mmp-context-card">
                <strong>Suggested actions</strong>
                <Link to={enquiryPath}>Create enquiry</Link>
                {linkedCustomerId && (
                  <Link to={`/customers/${linkedCustomerId}`}>
                    View customer
                  </Link>
                )}
              </div>
              {mailBanner}
            </MailIntelligencePanel>
          )}
        </div>
      </section>
    </div>
  );
}
