import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Anchor,
  ArrowRight,
  Bell,
  ChartColumn,
  ChevronDown,
  FileText,
  FolderOpen,
  LayoutDashboard,
  LogOut,
  Mail,
  MessagesSquare,
  PanelLeft,
  Search,
  Settings,
  Ship,
  Sparkles,
  Users,
  Wallet,
} from 'lucide-react';
import { useEffect, useLayoutEffect, useState } from 'react';
import {
  Link,
  Navigate,
  Outlet,
  useLocation,
  useNavigate,
} from 'react-router-dom';
import { GlobalAiDrawer } from '../../features/ai/AiAssist';
import CustomerWorkspace from '../../features/customers/CustomerWorkspace';
import {
  getCurrentMailConnection,
  getMailCounts,
  mailConnectionKey,
  mailCountsKey,
} from '../../features/mail/mailConnection';
import { PageContainer } from '../../shared/components/PageElements';
import { api, post, useData } from '../../shared/lib/workspace';
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
  const nav = useNavigate();
  const location = useLocation();
  const me = useData('me', '/auth/me');
  const queryClient = useQueryClient();
  const [menu, setMenu] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [debounced, setDebounced] = useState('');
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
  useEffect(() => {
    const t = setTimeout(() => setDebounced(search), 300);
    return () => clearTimeout(t);
  }, [search]);
  const results = useQuery({
    queryKey: ['search', debounced],
    queryFn: () => api('/search?q=' + encodeURIComponent(debounced)),
    enabled: debounced.length >= 2,
  });
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
      <div className="app-shell">
        <aside className={'app-sidebar' + (sidebarOpen ? ' is-open' : '')}>
          <div className="sidebar-brand">
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
            Settings
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
          <header className="app-topbar">
            <button
              className="menu-toggle"
              aria-label="Open navigation"
              onClick={() => setSidebarOpen(true)}
            >
              ☰
            </button>
            <button
              className="topbar-back"
              onClick={() => nav(-1)}
              aria-label="Go back"
            >
              <ArrowRight size={17} />
              <span>Back</span>
            </button>
            <div className="global-search">
              <Search size={18} />
              <input
                aria-label="Search workspace"
                placeholder="Search mail, customers, quotations, shipments..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
              {search.length >= 2 && (
                <div className="search-results">
                  {results.isLoading
                    ? 'Searching...'
                    : results.data &&
                      Object.entries(results.data).map(
                        ([group, items]: any) => (
                          <div key={group}>
                            <p>{group}</p>
                            {items.map((item: any) => (
                              <Link
                                key={item.id}
                                to={`/${group}/${item.id}`}
                                onClick={() => setSearch('')}
                              >
                                {item.companyName ||
                                  item.enquiryCode ||
                                  item.quotationNumber}
                              </Link>
                            ))}
                          </div>
                        ),
                      )}
                </div>
              )}
            </div>
            <button className="topbar-ai" onClick={() => setAiOpen(true)}>
              <Sparkles size={15} /> AI Assist
            </button>
            <div
              className="cw-mode-switch"
              role="group"
              aria-label="Screen layout"
            >
              {(
                [
                  ['mail', 'Mail', Mail],
                  ['split', 'Side by side', PanelLeft],
                  ['platform', 'Platform', Users],
                ] as const
              ).map(([value, label, Icon]) => (
                <button
                  key={value}
                  className={mode === value ? 'active' : ''}
                  aria-pressed={mode === value}
                  onClick={() => setMode(value)}
                >
                  <Icon size={14} />
                  {label}
                </button>
              ))}
            </div>
            <div className="topbar-actions">
              <Bell size={18} aria-label="Notifications" />
              <div className="user-menu">
                <button
                  onClick={() => setMenu(!menu)}
                  aria-expanded={menu}
                  aria-label="Account menu"
                >
                  <span className="user-avatar">{u.fullName?.[0]}</span>
                  <span className="user-copy">
                    <strong>{u.fullName}</strong>
                    <small>{u.companyName}</small>
                  </span>
                  <ChevronDown size={15} />
                </button>
                {menu && (
                  <div className="user-dropdown">
                    <Link to="/settings" onClick={() => setMenu(false)}>
                      Profile & settings
                    </Link>
                    <button
                      onClick={async () => {
                        await post('/auth/logout', {});
                        queryClient.clear();
                        nav('/login');
                      }}
                    >
                      <LogOut size={16} />
                      Logout
                    </button>
                  </div>
                )}
              </div>
            </div>
          </header>
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
              <div className={'app-panes app-panes-' + mode}>
                <section
                  className="app-mail-pane"
                  aria-label="Mail"
                  hidden={mode === 'platform'}
                >
                  <CustomerWorkspace embeddedMail />
                </section>
                <section
                  className="app-platform-pane"
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
        {aiOpen && <GlobalAiDrawer onClose={() => setAiOpen(false)} />}
      </div>
    </WorkspaceLayoutContext.Provider>
  );
}
