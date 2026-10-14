import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowRight,
  Bell,
  ChevronDown,
  LogOut,
  Mail,
  PanelLeft,
  Search,
  Sparkles,
  Users,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { GlobalAiDrawer } from '../../features/ai/AiAssist';
import { api, post } from '../../shared/lib/workspace';
import type { WorkspaceMode } from './WorkspaceLayout';

type AppHeaderProps = {
  mode: WorkspaceMode;
  setMode: (mode: WorkspaceMode) => void;
  fullName: string;
  companyName: string;
  notice: string;
  onOpenNavigation: () => void;
};

export function AppHeader({
  mode,
  setMode,
  fullName,
  companyName,
  notice,
  onOpenNavigation,
}: AppHeaderProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [accountOpen, setAccountOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  useEffect(() => {
    const open = () => setAiOpen(true);
    window.addEventListener('ff-open-global-ai', open);
    return () => window.removeEventListener('ff-open-global-ai', open);
  }, []);
  const [search, setSearch] = useState('');
  const [debounced, setDebounced] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(search), 300);
    return () => clearTimeout(timer);
  }, [search]);
  const results = useQuery({
    queryKey: ['search', debounced],
    queryFn: () => api('/search?q=' + encodeURIComponent(debounced)),
    enabled: accountOpen && debounced.length >= 2,
  });
  const modes = [
    ['mail', 'Mail', Mail],
    ['split', 'Side by side', PanelLeft],
    ['platform', 'Platform', Users],
  ] as const;
  return (
    <>
      <header className="app-topbar app-global-header">
        <div className="app-header-left">
          <button
            className="menu-toggle"
            aria-label="Open navigation"
            onClick={onOpenNavigation}
          >
            ☰
          </button>
          <button
            className="topbar-back"
            onClick={() => navigate(-1)}
            aria-label="Go back"
          >
            <ArrowRight size={17} />
            <span>Back</span>
          </button>
        </div>
        <div className="app-header-centre">
          <button className="topbar-ai" onClick={() => setAiOpen(true)}>
            <Sparkles size={15} /> <span>AI Assist</span>
          </button>
          <div
            className="cw-mode-switch"
            role="group"
            aria-label="Screen layout"
          >
            {modes.map(([value, label, Icon]) => (
              <button
                key={value}
                className={mode === value ? 'active' : ''}
                aria-label={label}
                aria-pressed={mode === value}
                onClick={() => setMode(value)}
              >
                <Icon size={15} />
                <span>{label}</span>
              </button>
            ))}
          </div>
        </div>
        <div className="app-header-right topbar-actions">
          <div className="app-header-notifications">
            <button
              className="app-header-icon"
              aria-label="Notifications"
              aria-expanded={notificationsOpen}
              onClick={() => {
                setNotificationsOpen((current) => !current);
                setAccountOpen(false);
              }}
            >
              <Bell size={19} />
            </button>
            {notificationsOpen && (
              <div className="app-header-popover" role="status">
                <strong>Notifications</strong>
                <p>{notice || 'No recent notifications in this session.'}</p>
              </div>
            )}
          </div>
          <div className="user-menu">
            <button
              onClick={() => {
                setAccountOpen((current) => !current);
                setNotificationsOpen(false);
              }}
              aria-expanded={accountOpen}
              aria-label="Account menu"
            >
              <span className="user-avatar">{fullName?.[0]}</span>
              <span className="user-copy">
                <strong>{fullName}</strong>
                <small>{companyName}</small>
              </span>
              <ChevronDown size={15} />
            </button>
            {accountOpen && (
              <div className="user-dropdown app-header-account">
                <label
                  className="app-header-search-label"
                  htmlFor="app-header-search"
                >
                  Search workspace
                </label>
                <div className="global-search">
                  <Search size={17} />
                  <input
                    id="app-header-search"
                    placeholder="Search mail, customers, quotations, shipments..."
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                  />
                </div>
                {search.length >= 2 && (
                  <div className="app-header-search-results">
                    {results.isLoading ? (
                      <p>Searching...</p>
                    ) : results.data ? (
                      Object.entries(results.data).map(
                        ([group, items]: any) => (
                          <div key={group}>
                            <p>{group}</p>
                            {items.map((item: any) => (
                              <Link
                                key={item.id}
                                to={`/${group}/${item.id}`}
                                onClick={() => {
                                  setSearch('');
                                  setAccountOpen(false);
                                }}
                              >
                                {item.companyName ||
                                  item.enquiryCode ||
                                  item.quotationNumber}
                              </Link>
                            ))}
                          </div>
                        ),
                      )
                    ) : null}
                  </div>
                )}
                <Link to="/settings" onClick={() => setAccountOpen(false)}>
                  Profile & settings
                </Link>
                <button
                  onClick={async () => {
                    await post('/auth/logout', {});
                    queryClient.clear();
                    navigate('/login');
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
      {aiOpen && <GlobalAiDrawer onClose={() => setAiOpen(false)} />}
    </>
  );
}
