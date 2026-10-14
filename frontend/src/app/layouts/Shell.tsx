import { useQuery } from '@tanstack/react-query';
import {
  Anchor,
  ArrowRight,
  ChartColumn,
  FileText,
  FolderOpen,
  LayoutDashboard,
  Mail,
  MessagesSquare,
  Settings,
  Ship,
  Users,
  Wallet,
} from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import CustomerWorkspace from '../../features/customers/CustomerWorkspace';
import EmailComposer from '../../features/mail/EmailComposer';
import {
  getCurrentMailConnection,
  getMailCounts,
  mailConnectionKey,
  mailCountsKey,
} from '../../features/mail/mailConnection';
import type { ComposerRequest } from '../../features/mail/openComposer';
import { PageContainer } from '../../shared/components/PageElements';
import { useData } from '../../shared/lib/workspace';
import { AppHeader } from './AppHeader';
import { WorkspaceLayoutContext, type WorkspaceMode } from './WorkspaceLayout';
const workspaceNav = [
  ['Dashboard', '/dashboard', LayoutDashboard],
  ['Mail', '/mail', Mail],
  ['Customers', '/customers', Users],
  ['Sales Tracker', '/enquiries', MessagesSquare],
  ['Quotations', '/quotations', FileText],
  ['Shipments', '/shipments', Ship],
] as const;
const deskNav = [
  ['Rate Desk', '/rates', Wallet],
  ['Documents', '/documents', FolderOpen],
  ['Reports', '/reports', ChartColumn],
] as const;
export function AppLayout() {
  const location = useLocation();
  const me = useData('me', '/auth/me');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const [floatingComposer, setFloatingComposer] = useState<
    (ComposerRequest & { key: number }) | null
  >(null);
  const floatingComposerRef = useRef(false);
  useEffect(() => {
    const open = (event: Event) => {
      const request = (event as CustomEvent<ComposerRequest>).detail || {};
      if (floatingComposerRef.current) {
        setNotice(
          'Close the current draft before starting another message. Your draft will be saved.',
        );
        setTimeout(() => setNotice(''), 4000);
        window.dispatchEvent(new Event('ff-restore-mail-composer'));
        return;
      }
      floatingComposerRef.current = true;
      setFloatingComposer({ ...request, key: Date.now() });
    };
    window.addEventListener('ff-open-mail-composer', open);
    return () => window.removeEventListener('ff-open-mail-composer', open);
  }, []);
  const [mode, setModeState] = useState<WorkspaceMode>(() => {
    const saved = sessionStorage.getItem('ff-workspace-mode');
    return window.location.pathname === '/mail'
      ? 'mail'
      : saved === 'mail' || saved === 'split' || saved === 'platform'
        ? saved
        : 'platform';
  });
  const [compactFocus, setCompactFocus] = useState<'mail' | 'platform'>('mail');
  const setMode = (next: WorkspaceMode) => {
    sessionStorage.setItem('ff-workspace-mode', next);
    setModeState(next);
  };
  const path = location.pathname;
  useLayoutEffect(() => {
    if (path === '/mail' || path.startsWith('/mail/')) {
      setModeState('mail');
      sessionStorage.setItem('ff-workspace-mode', 'mail');
    } else {
      setModeState((current) => {
        if (current !== 'mail') return current;
        sessionStorage.setItem('ff-workspace-mode', 'platform');
        return 'platform';
      });
    }
  }, [path]);
  const mailSubpage = path.startsWith('/mail/');
  const customerWorkspace =
    path === '/customers' || path === '/mail' || mailSubpage;
  const connectionQuery = useQuery({
    queryKey: mailConnectionKey,
    queryFn: getCurrentMailConnection,
  });
  const connectionId = connectionQuery.data?.active
    ? connectionQuery.data.id
    : null;
  const mailCount = useQuery({
    queryKey: mailCountsKey(connectionId),
    queryFn: getMailCounts,
    enabled: !!connectionId,
    refetchInterval: 60_000,
  });
  const unread = mailCount.data?.unread || 0;
  useEffect(() => {
    const handler = (e: Event) => {
      setNotice((e as CustomEvent).detail);
      setTimeout(() => setNotice(''), 4000);
    };
    window.addEventListener('ff-toast', handler);
    return () => window.removeEventListener('ff-toast', handler);
  }, []);
  if (me.isLoading)
    return (
      <div className="app-loading">
        <div className="skeleton" />
        <div className="skeleton" />
      </div>
    );
  if (me.isError) return <Navigate to="/login" />;
  const u = me.data;
  const navItem = ([label, target, Icon]: readonly [string, string, any]) => (
    <Link
      key={target}
      to={target}
      title={label}
      aria-label={label}
      onClick={() => {
        setSidebarOpen(false);
        if (target === '/mail') setMode('mail');
        else if (mode === 'mail') setMode('platform');
      }}
      aria-current={
        path === target ||
        (target !== '/dashboard' && path.startsWith(target + '/'))
          ? 'page'
          : undefined
      }
      className={
        'sidebar-link' +
        (path === target ||
        (target === '/mail' && path.startsWith('/mail/')) ||
        (target === '/customers' && path.startsWith('/customers')) ||
        (target === '/quotations' && path.startsWith('/quotations')) ||
        (target === '/enquiries' && path.startsWith('/enquiries'))
          ? ' active'
          : '')
      }
    >
      <Icon size={18} />
      <span>{label}</span>
      {target === '/mail' && unread > 0 && (
        <span className="sidebar-count">{unread}</span>
      )}
    </Link>
  );
  const mainContent = (
    <PageContainer
      variant={
        path === '/dashboard'
          ? 'dashboard'
          : customerWorkspace
            ? 'workspace'
            : 'standard'
      }
    >
      <Outlet />
    </PageContainer>
  );
  return (
    <WorkspaceLayoutContext.Provider value={{ mode, setMode }}>
      <div
        className={
          'app-shell' +
          (mode === 'mail' ? ' app-shell-mail-mode' : '') +
          (mode === 'mail' || mode === 'split' ? ' app-shell-mail-layout' : '')
        }
      >
        <aside className={'app-sidebar' + (sidebarOpen ? ' is-open' : '')}>
          <div className="sidebar-brand" aria-label="FreightFlow">
            <Anchor size={28} />
            <span>
              Freight<span>Flow</span>
            </span>
            <button
              className="sidebar-close"
              aria-label="Close navigation"
              onClick={() => setSidebarOpen(false)}
            >
              ×
            </button>
          </div>
          <div className="sidebar-profile">
            <span className="sidebar-avatar">
              {u.fullName
                ?.split(' ')
                .map((part: string) => part[0])
                .slice(0, 2)
                .join('')}
            </span>
            <span>
              <strong>{u.fullName}</strong>
              <small>{u.companyName}</small>
            </span>
          </div>
          <p className="sidebar-label">WORKSPACE</p>
          <nav aria-label="Workspace navigation">
            {workspaceNav.map(navItem)}
          </nav>
          <p className="sidebar-label sidebar-label-later">DESKS</p>
          <nav aria-label="Desk navigation">{deskNav.map(navItem)}</nav>
          <Link
            to="/settings"
            title="Settings"
            aria-label="Settings"
            aria-current={path === '/settings' ? 'page' : undefined}
            onClick={() => {
              setSidebarOpen(false);
              if (mode === 'mail') setMode('platform');
            }}
            className={
              'sidebar-link sidebar-settings' +
              (path === '/settings' ? ' active' : '')
            }
          >
            <Settings size={18} />
            <span>Settings</span>
          </Link>
          <Link className="dashboard-sidebar-promo" to="/enquiries/new">
            <strong>
              Moving Business
              <br />
              Forward Worldwide
            </strong>
            <span aria-hidden="true">
              <ArrowRight size={16} />
            </span>
          </Link>
        </aside>
        {sidebarOpen && (
          <button
            className="sidebar-scrim"
            aria-label="Close navigation"
            onClick={() => setSidebarOpen(false)}
          />
        )}
        <div className="app-content">
          <AppHeader
            mode={mode}
            setMode={setMode}
            fullName={u.fullName}
            companyName={u.companyName}
            notice={notice}
            onOpenNavigation={() => setSidebarOpen(true)}
          />
          <main
            className={
              'app-main app-main-' +
              mode +
              (mailSubpage ? ' app-main-mail-page' : '')
            }
            data-split-focus={compactFocus}
          >
            {notice && (
              <div role="status" className="app-toast">
                {notice}
              </div>
            )}
            {mode === 'split' && !mailSubpage && (
              <div
                className="split-focus-switch"
                role="group"
                aria-label="Side by side pane"
              >
                <button
                  className={compactFocus === 'mail' ? 'active' : ''}
                  aria-pressed={compactFocus === 'mail'}
                  onClick={() => setCompactFocus('mail')}
                >
                  <Mail size={15} /> Mail
                </button>
                <button
                  className={compactFocus === 'platform' ? 'active' : ''}
                  aria-pressed={compactFocus === 'platform'}
                  onClick={() => setCompactFocus('platform')}
                >
                  <Users size={15} /> Platform
                </button>
              </div>
            )}
            {path === '/mail/compose' ? (
              <div className="mail-composer-workspace">
                <CustomerWorkspace embeddedMail />
                {mainContent}
              </div>
            ) : customerWorkspace ? (
              mainContent
            ) : (
              <div
                className={
                  'app-panes app-panes-' +
                  mode +
                  (mode === 'split' ? ' ff-mail-split' : '')
                }
              >
                <section
                  className="app-mail-pane ff-mail-pane"
                  aria-label="Mail"
                  hidden={mode === 'platform'}
                >
                  <CustomerWorkspace embeddedMail />
                </section>
                <section
                  className="app-platform-pane ff-platform-pane"
                  aria-label="Platform page"
                  hidden={mode === 'mail'}
                >
                  {mainContent}
                </section>
                {mode === 'mail' && (
                  <button
                    className="app-compact-rail"
                    onClick={() => setMode('split')}
                    aria-label="Show platform side by side"
                  >
                    <Users size={19} />
                    <span>Platform</span>
                  </button>
                )}
              </div>
            )}
          </main>
        </div>
        {floatingComposer && (
          <div className="mail-floating-composer ff-page">
            <EmailComposer
              key={floatingComposer.key}
              context={floatingComposer}
              onClose={() => {
                floatingComposerRef.current = false;
                setFloatingComposer(null);
              }}
            />
          </div>
        )}
      </div>
    </WorkspaceLayoutContext.Provider>
  );
}
