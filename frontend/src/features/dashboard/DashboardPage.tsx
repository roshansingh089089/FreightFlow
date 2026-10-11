import {
  ArrowRight,
  ChevronRight,
  ClipboardCheck,
  FileText,
  MessagesSquare,
  MoreVertical,
  Plus,
  Truck,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import {
  State,
  StatusBadge,
  Table,
} from '../../shared/components/PageElements';
import { date, useData } from '../../shared/lib/workspace';

function KpiCard({
  label,
  value,
  helper,
  icon: Icon,
  tone,
  to,
}: {
  label: string;
  value: number | undefined;
  helper: string;
  icon: LucideIcon;
  tone: string;
  to: string;
}) {
  return (
    <Link to={to} className={`dashboard-kpi dashboard-kpi-${tone}`}>
      <div className="dashboard-kpi-top">
        <span className="dashboard-kpi-icon">
          <Icon size={23} strokeWidth={2} />
        </span>
        <span className="dashboard-kpi-label">{label}</span>
        <span className="dashboard-kpi-trend">Current</span>
      </div>
      <div className="dashboard-kpi-bottom">
        <div>
          <strong>{value ?? 0}</strong>
          <small>{helper}</small>
        </div>
        <span className="dashboard-kpi-link" aria-hidden="true">
          <ChevronRight size={18} />
        </span>
      </div>
      <svg
        className="dashboard-kpi-spark"
        viewBox="0 0 120 48"
        aria-hidden="true"
      >
        <path d="M0 39 C15 39 18 25 30 28 S46 40 58 29 S76 13 85 19 S99 7 120 6" />
      </svg>
    </Link>
  );
}

export function Dashboard() {
  const nav = useNavigate();
  const me = useData('me', '/auth/me');
  const q = useData('dashboard', '/dashboard/summary');
  const firstName = me.data?.fullName?.split(' ')[0] || 'there';
  const priorities =
    (q.data?.expiringQuotes?.length || 0) + (q.data?.newEnquiries ? 1 : 0);
  return (
    <State query={q}>
      <div className="dashboard-page dashboard-reference">
        <header className="dashboard-hero">
          <div className="dashboard-hero-copy">
            <span className="dashboard-eyebrow">DASHBOARD</span>
            <h1>Good Morning, {firstName} 👋</h1>
            <p>Here’s what’s happening with your freight business today.</p>
          </div>
          <Link className="dashboard-new-enquiry" to="/enquiries/new">
            <Plus size={20} /> New Enquiry
          </Link>
        </header>
        <div className="dashboard-master-grid">
          <KpiCard
            label="New Enquiries"
            value={q.data?.newEnquiries}
            helper="Awaiting a quotation"
            icon={MessagesSquare}
            tone="coral"
            to="/enquiries"
          />
          <KpiCard
            label="Quotes Pending"
            value={q.data?.quotesPending}
            helper="Draft quotations"
            icon={FileText}
            tone="green"
            to="/quotations"
          />
          <KpiCard
            label="Quotes Sent"
            value={q.data?.quotesSent}
            helper="With customers"
            icon={Truck}
            tone="orange"
            to="/quotations"
          />
          <KpiCard
            label="Customers"
            value={q.data?.customers}
            helper="In your workspace"
            icon={Users}
            tone="violet"
            to="/customers"
          />

          <section className="dashboard-panel dashboard-brief">
            <header className="dashboard-panel-head">
              <span className="dashboard-panel-icon dashboard-panel-icon-coral">
                <ClipboardCheck size={22} />
              </span>
              <div className="dashboard-panel-title">
                <h2>Tasks Requiring Attention</h2>
                <p>Stay on top of urgent items and follow-ups</p>
              </div>
              <span className="dashboard-priority-label">YOUR PRIORITIES</span>
              <span className="dashboard-priority-count">{priorities}</span>
              <Link
                to="/quotations"
                className="dashboard-round-link"
                aria-label="View priorities"
              >
                <ChevronRight size={18} />
              </Link>
            </header>
            <div className="dashboard-task-list">
              {q.data?.expiringQuotes?.map((x: any) => (
                <Link
                  key={x.id}
                  to={'/quotations/' + x.id}
                  className="task-row"
                >
                  <span className="task-icon urgent">
                    <FileText size={17} />
                  </span>
                  <span>
                    <strong>{x.quotationNumber} expires soon</strong>
                    <small>Review and send this draft quotation</small>
                  </span>
                  <time>{date(x.validUntil)}</time>
                </Link>
              ))}
              {q.data?.newEnquiries > 0 && (
                <Link to="/enquiries" className="task-row">
                  <span className="task-icon">
                    <MessagesSquare size={17} />
                  </span>
                  <span>
                    <strong>{q.data.newEnquiries} new enquiries</strong>
                    <small>Ready for your pricing team</small>
                  </span>
                  <ChevronRight size={17} />
                </Link>
              )}
              {!priorities && (
                <div className="dashboard-tasks-empty">
                  <span className="dashboard-empty-art">
                    <ClipboardCheck size={72} strokeWidth={1.15} />
                  </span>
                  <strong>You’re all caught up</strong>
                  <p>No urgent tasks right now</p>
                  <small>
                    We’ll notify you here when there are tasks
                    <br />
                    that need your attention.
                  </small>
                </div>
              )}
            </div>
          </section>

          <section className="dashboard-panel dashboard-inbox">
            <header className="dashboard-panel-head">
              <span className="dashboard-panel-icon dashboard-panel-icon-green">
                <FileText size={22} />
              </span>
              <div className="dashboard-panel-title">
                <h2>Recent Enquiries</h2>
                <p>Latest customer enquiries across your workspace</p>
              </div>
              <Link to="/enquiries" className="dashboard-view-all">
                View all <ArrowRight size={16} />
              </Link>
            </header>
            <div className="dashboard-table-wrap">
              <Table className="dashboard-table">
                <thead>
                  <tr>
                    <th>Enquiry ID</th>
                    <th>Customer</th>
                    <th>Route</th>
                    <th>Status</th>
                    <th>Date</th>
                    <th aria-label="Actions" />
                  </tr>
                </thead>
                <tbody>
                  {q.data?.recentEnquiries?.map((e: any) => (
                    <tr
                      key={e.id}
                      className="clickable-row"
                      tabIndex={0}
                      onClick={() => nav('/enquiries/' + e.id)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') nav('/enquiries/' + e.id);
                      }}
                    >
                      <td>
                        <Link
                          to={'/enquiries/' + e.id}
                          className="dashboard-table-id"
                        >
                          {e.enquiryCode}
                        </Link>
                      </td>
                      <td>{e.customerName}</td>
                      <td>
                        <span className="dashboard-route">
                          {e.origin} <ArrowRight size={13} /> {e.destination}
                        </span>
                      </td>
                      <td>
                        <StatusBadge value={e.status} />
                      </td>
                      <td>{date(e.createdAt)}</td>
                      <td>
                        <MoreVertical size={17} aria-hidden="true" />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
              {!q.data?.recentEnquiries?.length && (
                <div className="dashboard-table-empty">
                  <FileText size={30} />
                  <strong>No enquiries yet</strong>
                  <p>Create your first freight enquiry to get started.</p>
                  <Link to="/enquiries/new">
                    Create enquiry <ArrowRight size={14} />
                  </Link>
                </div>
              )}
            </div>
          </section>
        </div>
      </div>
    </State>
  );
}
