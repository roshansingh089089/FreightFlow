import type { LucideIcon } from 'lucide-react';
import { Sparkles, X } from 'lucide-react';
import type { ReactNode } from 'react';
import { useEffect, useRef } from 'react';

export function desktopDetailInitiallyOpen() {
  return (
    typeof window === 'undefined' ||
    !window.matchMedia ||
    window.matchMedia('(min-width: 1051px)').matches
  );
}

export function CrmAskAiButton() {
  return (
    <button
      type="button"
      className="btn secondary"
      onClick={() => window.dispatchEvent(new Event('ff-open-global-ai'))}
    >
      <Sparkles size={15} /> Ask AI
    </button>
  );
}

export function CrmMetric({
  label,
  value,
  icon: Icon,
  tone = 'blue',
}: {
  label: string;
  value: number | string | undefined;
  icon: LucideIcon;
  tone?: 'blue' | 'green' | 'amber' | 'violet' | 'red';
}) {
  return (
    <div className="crm-metric">
      <span className={`crm-metric-icon is-${tone}`}>
        <Icon size={18} />
      </span>
      <span>
        <strong>{value ?? '—'}</strong>
        <small>{label}</small>
      </span>
    </div>
  );
}

export function CrmDetailPanel({
  title,
  subtitle,
  badge,
  tabs,
  activeTab,
  onTab,
  onClose,
  children,
  actions,
}: {
  title: string;
  subtitle?: string;
  badge?: ReactNode;
  tabs: readonly string[];
  activeTab: string;
  onTab: (tab: string) => void;
  onClose: () => void;
  children: ReactNode;
  actions?: ReactNode;
}) {
  const closeButton = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  useEffect(() => {
    if (
      (panel.current && getComputedStyle(panel.current).position === 'fixed') ||
      window.matchMedia?.('(max-width: 1050px)').matches
    )
      closeButton.current?.focus();
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCloseRef.current();
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, []);
  return (
    <aside ref={panel} className="crm-detail-panel" aria-label={`${title} details`}>
      <header className="crm-detail-head">
        <div className="crm-detail-identity">
          <strong title={title}>{title}</strong>
          {subtitle && <small>{subtitle}</small>}
        </div>
        {badge}
        <button
          ref={closeButton}
          type="button"
          className="crm-panel-close"
          aria-label="Close details"
          onClick={onClose}
        >
          <X size={17} />
        </button>
      </header>
      <div
        className="crm-detail-tabs"
        role="tablist"
        aria-label={`${title} sections`}
      >
        {tabs.map((tab) => (
          <button
            key={tab}
            type="button"
            role="tab"
            aria-selected={activeTab === tab}
            className={activeTab === tab ? 'active' : ''}
            onClick={() => onTab(tab)}
          >
            {tab}
          </button>
        ))}
      </div>
      <div className="crm-detail-body">{children}</div>
      {actions && <footer className="crm-detail-actions">{actions}</footer>}
    </aside>
  );
}

export function CrmFacts({
  items,
}: {
  items: readonly { label: string; value: ReactNode }[];
}) {
  return (
    <dl className="crm-facts">
      {items.map(({ label, value }) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value || '—'}</dd>
        </div>
      ))}
    </dl>
  );
}
