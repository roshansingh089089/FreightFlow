import {
  ArrowDownUp,
  ChevronDown,
  FileText,
  Inbox,
  Paperclip,
  Search,
  Send,
  Sparkles,
  X,
} from 'lucide-react';
import type { ReactNode, Ref } from 'react';
import { Link } from 'react-router-dom';
import type { MailItem } from './CustomerMailWorkspace';

type RibbonTab = 'home' | 'view' | 'ai';
export function MailRibbon({
  tab,
  onTabChange,
  search,
  onSearchChange,
  onFolderToggle,
  onIntelligence,
  folderExpanded,
  triggerRef,
  children,
}: {
  tab: RibbonTab;
  onTabChange: (tab: RibbonTab) => void;
  search: string;
  onSearchChange: (value: string) => void;
  onFolderToggle: () => void;
  onIntelligence: () => void;
  folderExpanded: boolean;
  triggerRef?: Ref<HTMLButtonElement>;
  children: ReactNode;
}) {
  return (
    <header
      className="mmp-ribbon"
      aria-label="Mail actions"
      data-measure="ribbon"
    >
      <div className="mmp-ribbon-top">
        <button
          type="button"
          className="mmp-tool mmp-menu"
          ref={triggerRef}
          aria-expanded={folderExpanded}
          aria-label="Toggle folders"
          onClick={onFolderToggle}
        >
          <Inbox size={16} />
        </button>
        <nav className="mmp-tabs" aria-label="Mail ribbon tabs">
          {(['home', 'view', 'ai'] as const).map((value) => (
            <button
              key={value}
              type="button"
              className={tab === value ? 'active' : ''}
              onClick={() => onTabChange(value)}
            >
              {value === 'ai' && <Sparkles size={14} />}{' '}
              {value[0].toUpperCase() + value.slice(1)}
            </button>
          ))}
        </nav>
        <label className="mmp-search">
          <Search size={16} />
          <input
            aria-label="Search loaded mail"
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="Search mail – sender or subject"
          />
        </label>
        <button type="button" className="mmp-tool" onClick={onIntelligence}>
          <Sparkles size={16} />
          <span>AI insights</span>
        </button>
      </div>
      <div className="mmp-ribbon-cmds">{children}</div>
    </header>
  );
}
export function MailAppRail({ unread }: { unread: number }) {
  return (
    <nav
      className="mmp-rail"
      aria-label="Mail destinations"
      data-measure="rail"
    >
      <Link to="/mail" aria-label="Inbox" className="active">
        <Inbox size={20} />
        {unread > 0 && <span>{unread}</span>}
      </Link>
      <Link to="/mail/drafts" aria-label="Drafts">
        <FileText size={20} />
      </Link>
      <Link to="/mail/search" aria-label="Search mail">
        <Search size={20} />
      </Link>
    </nav>
  );
}
export function MailFolderPane({
  unread,
  connected,
  activeMailbox,
  overlay,
  onClose,
  onManual,
}: {
  unread: number;
  connected: boolean;
  activeMailbox?: string;
  overlay?: boolean;
  onClose?: () => void;
  onManual: () => void;
}) {
  return (
    <nav
      className={`mmp-folders${overlay ? ' overlay' : ''}`}
      aria-label="Mail folders"
      data-measure="folders"
    >
      <div className="mmp-folder-head">
        Folders{' '}
        {overlay && (
          <button type="button" onClick={onClose} aria-label="Close folders">
            <X size={15} />
          </button>
        )}
      </div>
      <Link to="/mail" className="mmp-folder active" aria-current="page">
        <Inbox size={16} />
        <span>Inbox</span>
        {connected && unread > 0 && <b>{unread}</b>}
      </Link>
      <Link to="/mail/stored?status=SENT" className="mmp-folder">
        <Send size={16} />
        <span>Sent Items</span>
      </Link>
      <Link to="/mail/drafts" className="mmp-folder">
        <FileText size={16} />
        <span>Drafts</span>
      </Link>
      {activeMailbox && (
        <div className="mmp-folder-head account" title={activeMailbox}>
          {activeMailbox}
        </div>
      )}
      <button type="button" className="mmp-folder" onClick={onManual}>
        <span>Paste email manually</span>
      </button>
    </nav>
  );
}
export function MailMessageList({ children }: { children: ReactNode }) {
  return (
    <section className="mmp-list" data-measure="list" aria-label="Mail inbox">
      {children}
    </section>
  );
}
export function MailInboxHeader({
  items,
  checked,
  onCheckedChange,
  filter,
  onFilterChange,
  sortNewest,
  onSortChange,
  onFolderToggle,
}: {
  items: MailItem[];
  checked: string[];
  onCheckedChange: (ids: string[]) => void;
  filter: 'all' | 'unread';
  onFilterChange: (value: 'all' | 'unread') => void;
  sortNewest: boolean;
  onSortChange: () => void;
  onFolderToggle: () => void;
}) {
  return (
    <div className="mmp-list-head" data-measure="inbox-header">
      <div className="mmp-list-title">
        <label className="mmp-check" title="Select all shown messages">
          <input
            type="checkbox"
            aria-label="Select all shown messages"
            checked={
              items.length > 0 &&
              items.every((item) => checked.includes(item.id))
            }
            onChange={(event) =>
              onCheckedChange(
                event.target.checked
                  ? Array.from(
                      new Set([...checked, ...items.map((item) => item.id)]),
                    )
                  : checked.filter(
                      (id) => !items.some((item) => item.id === id),
                    ),
              )
            }
          />
        </label>
        <h2>Inbox</h2>
        <button
          type="button"
          aria-label="Toggle folders"
          onClick={onFolderToggle}
        >
          <ChevronDown size={15} />
        </button>
        <button type="button" aria-label="Sort messages" onClick={onSortChange}>
          <ArrowDownUp size={15} />
        </button>
      </div>
      <div className="mmp-list-filters">
        <button
          type="button"
          className={filter === 'all' ? 'active' : ''}
          onClick={() => onFilterChange('all')}
        >
          All
        </button>
        <button
          type="button"
          className={filter === 'unread' ? 'active' : ''}
          onClick={() => onFilterChange('unread')}
        >
          Unread
        </button>
        <span>{sortNewest ? 'Newest first' : 'Oldest first'}</span>
      </div>
    </div>
  );
}
const initials = (value: string) =>
  value
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0] || '')
    .join('')
    .toUpperCase() || '?';
const time = (value: string) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(
    undefined,
    date.toDateString() === new Date().toDateString()
      ? { hour: 'numeric', minute: '2-digit' }
      : { month: 'short', day: 'numeric' },
  ).format(date);
};
export function MailMessageRow({
  item,
  selected,
  expanded,
  checked,
  mode,
  preview,
  hasAttachments,
  onSelect,
  onToggle,
}: {
  item: MailItem;
  selected: boolean;
  expanded: boolean;
  checked: boolean;
  mode: 'mail' | 'split';
  preview?: string;
  hasAttachments: boolean;
  onSelect: (id: string) => void;
  onToggle: (id: string) => void;
}) {
  return (
    <div
      className={`mmp-row${selected ? ' selected' : ''}${item.isRead ? '' : ' unread'}`}
      data-measure="row"
    >
      <label className="mmp-check">
        <input
          type="checkbox"
          aria-label={`Select ${item.subject || 'message'}`}
          checked={checked}
          onChange={() => onToggle(item.id)}
        />
      </label>
      <button
        type="button"
        className="mmp-row-hit"
        aria-current={selected ? 'true' : undefined}
        aria-expanded={mode === 'split' ? expanded : undefined}
        onClick={() => onSelect(item.id)}
      >
        <span className="mmp-avatar">
          {initials(item.fromName || item.fromEmail)}
        </span>
        <span className="mmp-row-copy">
          <span className="mmp-row-first">
            <strong>
              {item.fromName || item.fromEmail || 'Unknown sender'}
            </strong>
            {hasAttachments && (
              <Paperclip size={13} aria-label="Has attachments" />
            )}
            <time dateTime={item.receivedAt}>{time(item.receivedAt)}</time>
          </span>
          <span className="mmp-subject">{item.subject || '(no subject)'}</span>
          {preview && (
            <span className="mmp-preview">
              {preview.replace(/\s+/g, ' ').slice(0, 120)}
            </span>
          )}
        </span>
      </button>
    </div>
  );
}
export function MailReadingPane({
  children,
  inline = false,
}: {
  children: ReactNode;
  inline?: boolean;
}) {
  return (
    <article
      className="mmp-reader"
      data-measure={inline ? 'inline-reader' : 'reader'}
      aria-label={inline ? 'Expanded message' : 'Selected message'}
    >
      {children}
    </article>
  );
}
export function MailInlineReader({ children }: { children: ReactNode }) {
  return <div className="mmp-inline">{children}</div>;
}
export function MailBusinessActions({ children }: { children: ReactNode }) {
  return (
    <div className="mmp-business" aria-label="Mail business actions">
      <Sparkles size={21} />
      {children}
    </div>
  );
}
export function MailIntelligencePanel({
  children,
  onClose,
}: {
  children: ReactNode;
  onClose: () => void;
}) {
  return (
    <aside
      className="mmp-intelligence"
      aria-label="FreightFlow intelligence"
      data-measure="intelligence"
    >
      <div className="mmp-panel-head">
        <Sparkles size={17} />
        <strong>FreightFlow intelligence</strong>
        <button type="button" onClick={onClose} aria-label="Close intelligence">
          <X size={16} />
        </button>
      </div>
      {children}
    </aside>
  );
}
