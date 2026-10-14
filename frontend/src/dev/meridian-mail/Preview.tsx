import {
  Archive,
  ArrowLeft,
  ChevronDown,
  FileText,
  Inbox,
  Mail,
  Menu,
  PanelLeft,
  Paperclip,
  Plus,
  RefreshCw,
  Reply,
  ReplyAll,
  Search,
  Send,
  Sparkles,
  Users,
  X,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import {
  folders,
  messages,
  type FolderId,
  type PreviewFolder,
  type PreviewMessage,
} from './fixtures';
import './preview.css';

type Mode = 'full' | 'split';
type Tab = 'home' | 'view' | 'ai';
type BusinessAction =
  | 'onboard'
  | 'customer'
  | 'enquiry'
  | 'quotation'
  | 'reply'
  | 'replyAll'
  | 'compose'
  | 'summary'
  | 'draftQuotation';
interface Actions {
  onAction: (action: BusinessAction, message?: PreviewMessage) => void;
}
const icon = 16;
const initials = (name: string) =>
  name
    .split(' ')
    .slice(0, 2)
    .map((word) => word[0])
    .join('')
    .toUpperCase();

export function MailRibbon({
  tab,
  onTab,
  query,
  onQuery,
  onFolderToggle,
  onMode,
  mode,
  onAction,
  selected,
  onIntelligence,
}: {
  tab: Tab;
  onTab: (tab: Tab) => void;
  query: string;
  onQuery: (value: string) => void;
  onFolderToggle: () => void;
  onMode: (mode: Mode) => void;
  mode: Mode;
  selected?: PreviewMessage;
  onIntelligence: () => void;
} & Actions) {
  return (
    <header className="mmp-ribbon" data-measure="ribbon">
      <div className="mmp-ribbon-top">
        <button
          className="mmp-tool mmp-menu"
          onClick={onFolderToggle}
          aria-label="Toggle folders"
        >
          <Menu size={icon} />
        </button>
        <nav className="mmp-tabs" aria-label="Mail ribbon tabs">
          {(['home', 'view', 'ai'] as Tab[]).map((value) => (
            <button
              key={value}
              className={tab === value ? 'active' : ''}
              onClick={() => onTab(value)}
            >
              {value === 'ai' && <Sparkles size={14} />}{' '}
              {value[0].toUpperCase() + value.slice(1)}
            </button>
          ))}
        </nav>
        <label className="mmp-search">
          <Search size={16} />
          <input
            aria-label="Search preview mail"
            value={query}
            onChange={(event) => onQuery(event.target.value)}
            placeholder="Search mail – sender or subject"
          />
        </label>
        <button className="mmp-tool" onClick={onIntelligence}>
          <Sparkles size={icon} />
          <span>AI insights</span>
        </button>
        <button
          className="mmp-tool"
          onClick={() => onMode(mode === 'full' ? 'split' : 'full')}
        >
          <PanelLeft size={icon} />
          <span>{mode === 'full' ? 'Side by side' : 'Full Mail'}</span>
        </button>
      </div>
      <div className="mmp-ribbon-cmds">
        {tab === 'home' ? (
          <>
            <button
              className="mmp-tool primary"
              onClick={() => onAction('compose')}
            >
              <Plus size={icon} /> New mail
            </button>
            <span className="mmp-sep" />
            <button
              className="mmp-tool"
              disabled={!selected}
              onClick={() => onAction('reply', selected)}
            >
              <Reply size={icon} /> Reply
            </button>
            <button
              className="mmp-tool"
              disabled={!selected}
              onClick={() => onAction('replyAll', selected)}
            >
              <ReplyAll size={icon} /> Reply all
            </button>
            <span className="mmp-sep" />
            <button
              className="mmp-tool"
              title="Preview fixture is local; no provider sync"
              disabled
            >
              <RefreshCw size={icon} /> Sync
            </button>
          </>
        ) : tab === 'view' ? (
          <>
            <span className="mmp-cmdlabel">Layout</span>
            <button className="mmp-tool" onClick={onFolderToggle}>
              Folder pane
            </button>
            <button
              className="mmp-tool"
              onClick={() => onMode(mode === 'full' ? 'split' : 'full')}
            >
              {mode === 'full' ? 'Side by side' : 'Full Mail'}
            </button>
          </>
        ) : (
          <>
            <span className="mmp-cmdlabel">Assistant</span>
            <button className="mmp-tool" onClick={onIntelligence}>
              <Sparkles size={icon} /> Toggle insights
            </button>
          </>
        )}
      </div>
    </header>
  );
}
export function MailAppRail({ unread }: { unread: number }) {
  return (
    <nav
      className="mmp-rail"
      data-measure="rail"
      aria-label="Mail applications"
    >
      <button className="active" aria-label="Mail">
        <Mail size={20} />
        {unread > 0 && <span>{unread}</span>}
      </button>
      <button
        disabled
        title="Calendar is outside this preview"
        aria-label="Calendar unavailable"
      >
        <FileText size={20} />
      </button>
      <button
        disabled
        title="People is outside this preview"
        aria-label="People unavailable"
      >
        <Users size={20} />
      </button>
    </nav>
  );
}
export function MailFolderPane({
  items,
  active,
  onSelect,
  overlay,
  onClose,
}: {
  items: PreviewFolder[];
  active: FolderId;
  onSelect: (id: FolderId) => void;
  overlay: boolean;
  onClose: () => void;
}) {
  return (
    <nav
      className={`mmp-folders ${overlay ? 'overlay' : ''}`}
      data-measure="folders"
      aria-label="Mail folders"
    >
      <div className="mmp-folder-head">
        Favourites{' '}
        {overlay && (
          <button onClick={onClose} aria-label="Close folders">
            <X size={15} />
          </button>
        )}
      </div>
      {items.map((folder) => (
        <button
          key={`fav-${folder.id}`}
          className={`mmp-folder ${active === folder.id ? 'active' : ''}`}
          onClick={() => {
            onSelect(folder.id);
            if (overlay) onClose();
          }}
        >
          {folder.id === 'inbox' ? (
            <Inbox size={16} />
          ) : folder.id === 'sent' ? (
            <Send size={16} />
          ) : (
            <FileText size={16} />
          )}
          <span>{folder.label}</span>
          {folder.unread > 0 && <b>{folder.unread}</b>}
        </button>
      ))}
      <div className="mmp-folder-head account">
        operations@freightflow.example
      </div>
      {items.map((folder) => (
        <button
          key={`account-${folder.id}`}
          className={`mmp-folder ${active === folder.id ? 'active' : ''}`}
          onClick={() => {
            onSelect(folder.id);
            if (overlay) onClose();
          }}
        >
          {folder.id === 'inbox' ? (
            <Inbox size={16} />
          ) : folder.id === 'sent' ? (
            <Send size={16} />
          ) : (
            <FileText size={16} />
          )}
          <span>{folder.label}</span>
          {folder.unread > 0 && <b>{folder.unread}</b>}
        </button>
      ))}
    </nav>
  );
}
export function MailMessageRow({
  message,
  selected,
  expanded,
  onOpen,
}: {
  message: PreviewMessage;
  selected: boolean;
  expanded: boolean;
  onOpen: () => void;
}) {
  return (
    <div
      className={`mmp-row ${selected ? 'selected' : ''} ${message.unread ? 'unread' : ''}`}
      data-measure="row"
    >
      <label className="mmp-check">
        <input
          type="checkbox"
          aria-label={`Select ${message.sender}`}
          onClick={(event) => event.stopPropagation()}
        />
      </label>
      <button className="mmp-row-hit" onClick={onOpen} aria-expanded={expanded}>
        <span className="mmp-avatar">{initials(message.sender)}</span>
        <span className="mmp-row-copy">
          <span className="mmp-row-first">
            <strong>{message.sender}</strong>
            {message.attachments && <Paperclip size={13} />}
            <time>{message.time}</time>
          </span>
          <span className="mmp-subject">{message.subject}</span>
          <span className="mmp-preview">{message.preview}</span>
          {message.ai && (
            <span className="mmp-tags">
              <em>{message.customer ? 'Customer' : 'New customer'}</em>
              {message.ai.title.includes('Rate') && <em>Rate request</em>}
            </span>
          )}
        </span>
      </button>
    </div>
  );
}
export function MailMessageList({
  items,
  folder,
  selectedId,
  expandedId,
  mode,
  onSelect,
  unreadOnly,
  onUnreadOnly,
  sortNewest,
  onSort,
  onFolders,
  onAction,
}: {
  items: PreviewMessage[];
  folder: PreviewFolder;
  selectedId?: string;
  expandedId?: string;
  mode: Mode;
  onSelect: (id: string) => void;
  unreadOnly: boolean;
  onUnreadOnly: (v: boolean) => void;
  sortNewest: boolean;
  onSort: () => void;
  onFolders: () => void;
  onAction: Actions['onAction'];
}) {
  return (
    <section className="mmp-list" data-measure="list" aria-label="Message list">
      <div className="mmp-list-head" data-measure="inbox-header">
        <div className="mmp-list-title">
          <label className="mmp-check">
            <input type="checkbox" aria-label="Select all preview messages" />
          </label>
          <h2>{folder.label}</h2>
          <button
            onClick={onFolders}
            aria-label="Toggle folders"
            title="Folders"
          >
            <ChevronDown size={15} />
          </button>
          <button onClick={onSort} aria-label="Sort messages">
            <span className="mmp-sort-symbol">↕</span>
          </button>
        </div>
        <div className="mmp-list-filters">
          <button
            className={!unreadOnly ? 'active' : ''}
            onClick={() => onUnreadOnly(false)}
          >
            All
          </button>
          <button
            className={unreadOnly ? 'active' : ''}
            onClick={() => onUnreadOnly(true)}
          >
            Unread
          </button>
          <span>{sortNewest ? 'Newest first' : 'Oldest first'}</span>
        </div>
      </div>
      <div className="mmp-day">
        TODAY <span>{items.length} messages</span>
      </div>
      {items.map((message) => (
        <div key={message.id}>
          <MailMessageRow
            message={message}
            selected={selectedId === message.id}
            expanded={expandedId === message.id}
            onOpen={() => onSelect(message.id)}
          />
          {mode === 'split' && expandedId === message.id && (
            <MailInlineReader message={message} onAction={onAction} />
          )}
        </div>
      ))}
      {items.length === 0 && (
        <div className="mmp-empty">No messages in this preview view.</div>
      )}
    </section>
  );
}
export function MailBusinessActions({
  message,
  onAction,
}: { message: PreviewMessage } & Actions) {
  if (!message.ai && !message.customer) return null;
  return (
    <div className="mmp-business">
      <Sparkles size={21} />
      <div>
        <strong>{message.ai?.title ?? message.customer}</strong>
        <p>{message.ai?.detail ?? 'Customer linked to this conversation.'}</p>
        <div className="mmp-business-buttons">
          {!message.customer && (
            <button
              className="accent"
              onClick={() => onAction('onboard', message)}
            >
              Onboard customer with AI
            </button>
          )}
          {message.customer && (
            <button onClick={() => onAction('customer', message)}>
              View customer
            </button>
          )}
          {message.customer && (
            <button onClick={() => onAction('enquiry', message)}>
              Create enquiry
            </button>
          )}
          {message.ai?.title === 'Rate request detected' && (
            <button onClick={() => onAction('draftQuotation', message)}>
              Draft quotation
            </button>
          )}
          {message.quotationId && (
            <button onClick={() => onAction('quotation', message)}>
              View quotation {message.quotationId}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
export function MailIntelligencePanel({
  message,
  onClose,
  onAction,
  onboardingOpen,
}: {
  message?: PreviewMessage;
  onClose: () => void;
  onboardingOpen: boolean;
} & Actions) {
  const [draft, setDraft] = useState<Record<string, string>>(
    message?.ai?.extracted ?? {},
  );
  return (
    <aside className="mmp-intelligence" data-measure="intelligence">
      <div className="mmp-panel-head">
        <Sparkles size={17} />
        <strong>FreightFlow intelligence</strong>
        <button onClick={onClose} aria-label="Close intelligence">
          <X size={16} />
        </button>
      </div>
      {message ? (
        <>
          <p>
            {message.ai?.detail ??
              'Select a message to see customer context and suggested actions.'}
          </p>
          {onboardingOpen && message.ai?.extracted ? (
            <section className="mmp-onboard">
              <h3>Onboard customer with AI</h3>
              <p>
                Source email: {message.subject} · {message.email}
              </p>
              {Object.entries(draft).map(([field, value]) => (
                <label key={field}>
                  {field}
                  <input
                    value={value}
                    onChange={(event) =>
                      setDraft((current) => ({
                        ...current,
                        [field]: event.target.value,
                      }))
                    }
                  />
                </label>
              ))}
              <small>Preview only. No customer is saved.</small>
            </section>
          ) : (
            <MailBusinessActions message={message} onAction={onAction} />
          )}
        </>
      ) : (
        <p>Select a message to see customer context.</p>
      )}
    </aside>
  );
}
export function MailReadingPane({
  message,
  onAction,
}: { message?: PreviewMessage } & Actions) {
  if (!message)
    return (
      <section className="mmp-reader mmp-reader-empty" data-measure="reader">
        <Mail size={30} />
        <h2>Select an item to read</h2>
        <p>Choose a message from the list.</p>
      </section>
    );
  return (
    <article className="mmp-reader" data-measure="reader">
      <div className="mmp-reading-head">
        <h2>{message.subject}</h2>
        <div className="mmp-sender">
          <span className="mmp-avatar large">{initials(message.sender)}</span>
          <div>
            <strong>{message.sender}</strong>
            <span>&lt;{message.email}&gt;</span>
            <span>
              To: {message.to}
              {message.cc && ` · Cc: ${message.cc}`}
            </span>
            <span>
              {message.date} {message.time}
            </span>
          </div>
        </div>
        <div className="mmp-reading-actions">
          <button onClick={() => onAction('reply', message)}>
            <Reply size={15} /> Reply
          </button>
          <button onClick={() => onAction('replyAll', message)}>
            <ReplyAll size={15} /> Reply all
          </button>
        </div>
      </div>
      <div className="mmp-reading-body">
        <button
          className="mmp-summary"
          onClick={() => onAction('summary', message)}
        >
          <Sparkles size={14} /> Preview AI context
        </button>
        <MailBusinessActions message={message} onAction={onAction} />
        <div className="mmp-body-text">
          {message.body.map((paragraph, i) => (
            <p key={i}>{paragraph}</p>
          ))}
        </div>
        {message.attachments && (
          <div className="mmp-attachments">
            {message.attachments.map((attachment) => (
              <div key={attachment.name}>
                <b>{attachment.kind}</b>
                <span>
                  <strong>{attachment.name}</strong>
                  <small>{attachment.size} · preview only</small>
                </span>
              </div>
            ))}
          </div>
        )}
        {message.replies && (
          <section className="mmp-conversation">
            <h3>Conversation</h3>
            {message.replies.map((reply, i) => (
              <div key={i}>
                <strong>{reply.author}</strong>
                <small>{reply.date}</small>
                <p>{reply.body}</p>
              </div>
            ))}
          </section>
        )}
      </div>
    </article>
  );
}
export function MailInlineReader({
  message,
  onAction,
}: { message: PreviewMessage } & Actions) {
  return (
    <div className="mmp-inline" data-measure="inline-reader">
      <MailReadingPane message={message} onAction={onAction} />
    </div>
  );
}
export default function Preview() {
  const [mode, setMode] = useState<Mode>(
      new URLSearchParams(location.search).get('mode') === 'split'
        ? 'split'
        : 'full',
    ),
    [folder, setFolder] = useState<FolderId>('inbox'),
    [selectedId, setSelectedId] = useState<string>(
      new URLSearchParams(location.search).get('message') || 'unknown',
    ),
    [expandedId, setExpandedId] = useState<string | undefined>(
      new URLSearchParams(location.search).get('expanded') || undefined,
    ),
    [tab, setTab] = useState<Tab>('home'),
    [query, setQuery] = useState(''),
    [unreadOnly, setUnreadOnly] = useState(false),
    [sortNewest, setSortNewest] = useState(true),
    [foldersOpen, setFoldersOpen] = useState(true),
    [overlayOpen, setOverlayOpen] = useState(false),
    [insightsOpen, setInsightsOpen] = useState(false),
    [onboardingOpen, setOnboardingOpen] = useState(false),
    [notice, setNotice] = useState(''),
    [platformTab, setPlatformTab] = useState<'customers' | 'enquiries'>(
      'customers',
    );
  const selected = messages.find((m) => m.id === selectedId);
  const visible = useMemo(
    () =>
      messages
        .filter(
          (m) =>
            m.folder === folder &&
            (!unreadOnly || m.unread) &&
            `${m.sender} ${m.subject} ${m.preview}`
              .toLowerCase()
              .includes(query.toLowerCase()),
        )
        .sort((a, b) =>
          sortNewest
            ? messages.indexOf(a) - messages.indexOf(b)
            : messages.indexOf(b) - messages.indexOf(a),
        ),
    [folder, unreadOnly, query, sortNewest],
  );
  const onAction = (action: BusinessAction, message?: PreviewMessage) => {
    setNotice(
      `${action === 'onboard' ? 'Editable AI onboarding' : action} preview for ${message?.sender ?? 'new mail'} — fixture only; nothing was saved or sent.`,
    );
    if (action === 'onboard') {
      setInsightsOpen(true);
      setOnboardingOpen(true);
    }
  };
  const onSelect = (id: string) => {
    setSelectedId(id);
    if (mode === 'split')
      setExpandedId((current) => (current === id ? undefined : id));
  };
  const changeMode = (next: Mode) => {
    setMode(next);
    setExpandedId(undefined);
    setOverlayOpen(false);
  };
  const toggleFolders = () => {
    if (
      mode === 'split' ||
      (document.querySelector('[data-measure=mail]')?.clientWidth ?? 0) <= 1050
    )
      setOverlayOpen((v) => !v);
    else setFoldersOpen((v) => !v);
  };
  return (
    <div className={`mmp-app ${mode}`} data-preview="meridian-mail">
      <aside className="mmp-sidebar" data-measure="sidebar">
        <div className="mmp-brand">
          <span className="mmp-brand-icon">⚓</span>
          <strong>
            Freight<span>Flow</span>
          </strong>
        </div>
        <div className="mmp-user">
          <span>FF</span>
          <div>
            <strong>Preview workspace</strong>
            <small>Design fixture · local only</small>
          </div>
        </div>
        <small className="mmp-navlabel">WORKSPACE</small>
        <div className="mmp-nav">
          <span>
            <Archive size={18} /> Dashboard
          </span>
          <span className="active">
            <Mail size={18} /> Mail <b>{folders[0].unread}</b>
          </span>
          <span>
            <Users size={18} /> Customers
          </span>
          <span>
            <FileText size={18} /> Quotations
          </span>
        </div>
        <small className="mmp-navlabel">DESKS</small>
        <div className="mmp-nav">
          <span>
            <Archive size={18} /> Rate Desk
          </span>
          <span>
            <FileText size={18} /> Documents
          </span>
        </div>
        <div className="mmp-spacer" />
        <div className="mmp-preview-label">ISOLATED DESIGN PREVIEW</div>
      </aside>
      <div className="mmp-main">
        <div className="mmp-topbar">
          <span className="mmp-back">
            <ArrowLeft size={16} /> FreightFlow Mail preview
          </span>
          <div className="mmp-mode">
            <button
              className={mode === 'full' ? 'active' : ''}
              onClick={() => changeMode('full')}
            >
              <Mail size={16} /> Mail
            </button>
            <button
              className={mode === 'split' ? 'active' : ''}
              onClick={() => changeMode('split')}
            >
              <PanelLeft size={16} /> Side by side
            </button>
          </div>
          <span className="mmp-fixture-badge">Fixture data</span>
        </div>
        {notice && (
          <div className="mmp-notice" role="status">
            {notice}
            <button onClick={() => setNotice('')} aria-label="Dismiss">
              <X size={14} />
            </button>
          </div>
        )}
        <div className="mmp-panes">
          <section className="mmp-mail" data-measure="mail">
            <MailRibbon
              tab={tab}
              onTab={setTab}
              query={query}
              onQuery={setQuery}
              onFolderToggle={toggleFolders}
              onMode={changeMode}
              mode={mode}
              onAction={onAction}
              selected={selected}
              onIntelligence={() => setInsightsOpen((v) => !v)}
            />
            <div className="mmp-mail-body">
              {mode === 'full' && <MailAppRail unread={folders[0].unread} />}
              {mode === 'full' && foldersOpen && (
                <MailFolderPane
                  items={folders}
                  active={folder}
                  onSelect={setFolder}
                  overlay={false}
                  onClose={() => setFoldersOpen(false)}
                />
              )}
              {overlayOpen && (
                <MailFolderPane
                  items={folders}
                  active={folder}
                  onSelect={setFolder}
                  overlay
                  onClose={() => setOverlayOpen(false)}
                />
              )}
              <MailMessageList
                items={visible}
                folder={folders.find((f) => f.id === folder)!}
                selectedId={selectedId}
                expandedId={expandedId}
                mode={mode}
                onSelect={onSelect}
                unreadOnly={unreadOnly}
                onUnreadOnly={setUnreadOnly}
                sortNewest={sortNewest}
                onSort={() => setSortNewest((v) => !v)}
                onFolders={toggleFolders}
                onAction={onAction}
              />
              {mode === 'full' && (
                <MailReadingPane message={selected} onAction={onAction} />
              )}
              {insightsOpen && (
                <MailIntelligencePanel
                  key={selectedId}
                  message={selected}
                  onClose={() => {
                    setInsightsOpen(false);
                    setOnboardingOpen(false);
                  }}
                  onAction={onAction}
                  onboardingOpen={onboardingOpen}
                />
              )}
            </div>
          </section>
          {mode === 'split' && (
            <section className="mmp-platform" data-measure="platform">
              <div className="mmp-platform-head">
                <div>
                  <small>PLATFORM</small>
                  <h2>
                    {platformTab === 'customers'
                      ? 'Customer directory'
                      : 'Enquiry desk'}
                  </h2>
                </div>
                <nav>
                  <button
                    className={platformTab === 'customers' ? 'active' : ''}
                    onClick={() => setPlatformTab('customers')}
                  >
                    Customers
                  </button>
                  <button
                    className={platformTab === 'enquiries' ? 'active' : ''}
                    onClick={() => setPlatformTab('enquiries')}
                  >
                    Enquiries
                  </button>
                </nav>
              </div>
              <div className="mmp-platform-search">
                <Search size={16} />{' '}
                {platformTab === 'customers'
                  ? 'Search company, email or phone'
                  : 'Search enquiries'}
              </div>
              <div className="mmp-platform-table">
                <div className="mmp-platform-thead">
                  <b>{platformTab === 'customers' ? 'COMPANY' : 'ENQUIRY'}</b>
                  <b>PRIMARY CONTACT</b>
                </div>
                {Array.from({ length: 18 }, (_, i) => (
                  <div className="mmp-platform-item" key={i}>
                    <span className="mmp-company-avatar">
                      {['GO', 'PC', 'VF', 'NS'][i % 4]}
                    </span>
                    <span>
                      <strong>
                        {platformTab === 'customers'
                          ? [
                              'Golden Orchard Co., Ltd.',
                              'Pacific Cargo',
                              'Viet Freight',
                              'Northstar Logistics',
                            ][i % 4]
                          : `Rate enquiry ${String(i + 1).padStart(3, '0')}`}
                      </strong>
                      <small>PREVIEW-{String(i + 1).padStart(4, '0')}</small>
                    </span>
                    <span>
                      {
                        [
                          'Pimchanok Srisuk',
                          'Somchai Phongsri',
                          'Nguyen Thi Lan',
                          'Rahul Verma',
                        ][i % 4]
                      }
                    </span>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      </div>
    </div>
  );
}
