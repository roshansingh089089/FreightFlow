import { useQuery } from '@tanstack/react-query';
import {
  AlertTriangle,
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  Clock3,
  FileText,
  MessageSquareText,
  Plus,
  Send,
  Sparkles,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { StatusBadge } from '../../shared/components/PageElements';
import { api, date, fmt, useData } from '../../shared/lib/workspace';
import { quotationRouteFromShipment } from '../quotations/quotationRoute';

type Enquiry = {
  id: string;
  enquiryCode: string;
  customerName: string;
  origin: string;
  destination: string;
  mode?: string;
  status: string;
  createdAt?: string;
};
type Quote = {
  id: string;
  quotationNumber: string;
  customerName: string;
  currency?: string;
  totalAmount?: number;
  status: string;
  createdAt?: string;
  validUntil?: string;
  shipmentSnapshot?: string | null;
};
type Customer = {
  id: string;
  companyName: string;
  status: string;
  createdAt?: string;
};
type Page<T> = { content: T[]; totalElements: number };
type Summary = {
  newEnquiries: number;
  quotesPending: number;
  quotesSent: number;
  customers: number;
  recentEnquiries: Enquiry[];
  expiringQuotes: Quote[];
};

function Kpi({
  label,
  value,
  icon: Icon,
  tone,
  to,
  hint,
}: {
  label: string;
  value?: number;
  icon: LucideIcon;
  tone: string;
  to: string;
  hint: string;
}) {
  return (
    <Link className={`ops-kpi ops-${tone}`} to={to}>
      <span className="ops-kpi-icon">
        <Icon size={25} />
      </span>
      <span className="ops-kpi-copy">
        <strong>{value ?? '—'}</strong>
        <span>{label}</span>
        <small>{hint}</small>
      </span>
    </Link>
  );
}

function PanelHeading({
  icon: Icon,
  title,
  to,
}: {
  icon: LucideIcon;
  title: string;
  to?: string;
}) {
  return (
    <div className="ops-panel-heading">
      <span className="ops-panel-heading-icon">
        <Icon size={19} />
      </span>
      <h2>{title}</h2>
      {to && (
        <Link to={to}>
          View all <ArrowRight size={14} />
        </Link>
      )}
    </div>
  );
}

function route(origin?: string, destination?: string) {
  return origin && destination
    ? `${origin} → ${destination}`
    : origin || destination || 'Route pending';
}

function quoteRoute(snapshot?: string | null) {
  if (!snapshot) return 'Route pending';
  try {
    const shipment = JSON.parse(snapshot) as Record<string, string>;
    if (
      !shipment.origin &&
      !shipment.polCode &&
      !shipment.portOfLoading &&
      !shipment.airportOfOrigin
    )
      return 'Route pending';
    return quotationRouteFromShipment(shipment).label;
  } catch {
    return 'Route pending';
  }
}

function activityDate(value?: string) {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? '—' : date(value);
}

export function Dashboard() {
  const navigate = useNavigate();
  const me = useData('me', '/auth/me');
  const summary = useQuery<Summary>({
    queryKey: ['dashboard', 'summary'],
    queryFn: () => api('/dashboard/summary'),
  });
  const quotes = useQuery<Page<Quote>>({
    queryKey: ['dashboard', 'recent-quotes'],
    queryFn: () => api('/quotations?page=0&size=5'),
  });
  const drafts = useQuery<Page<Quote>>({
    queryKey: ['dashboard', 'draft-quotes'],
    queryFn: () => api('/quotations?status=DRAFT&page=0&size=5'),
  });
  const customers = useQuery<Page<Customer>>({
    queryKey: ['dashboard', 'recent-customers'],
    queryFn: () =>
      api('/customers?page=0&size=5&sort=createdAt&direction=desc'),
  });
  const activeCustomers = useQuery<Page<Customer>>({
    queryKey: ['dashboard', 'active-customers'],
    queryFn: () => api('/customers?status=ACTIVE&size=1'),
  });
  const data = summary.data;
  const recentEnquiries = data?.recentEnquiries || [];
  const recentQuotes = quotes.data?.content || [];
  const pendingEnquiries = recentEnquiries.filter((enquiry) =>
    ['NEW', 'PRICING_PENDING'].includes(enquiry.status),
  );
  const attention = [
    ...pendingEnquiries.map((item) => ({
      key: `enquiry-${item.id}`,
      label: item.enquiryCode,
      detail: `${item.customerName} · ${route(item.origin, item.destination)}`,
      status: item.status,
      to: `/enquiries/${item.id}`,
      icon: MessageSquareText,
    })),
    ...(drafts.data?.content || []).map((item) => ({
      key: `quote-${item.id}`,
      label: item.quotationNumber,
      detail: `${item.customerName} · Draft quotation`,
      status: item.status,
      to: `/quotations/${item.id}`,
      icon: FileText,
    })),
  ].slice(0, 4);
  const activity = [
    ...recentEnquiries.map((item) => ({
      key: `enquiry-${item.id}`,
      label: 'Enquiry created',
      detail: `${item.enquiryCode} · ${item.customerName}`,
      at: item.createdAt,
      to: `/enquiries/${item.id}`,
      icon: MessageSquareText,
    })),
    ...recentQuotes.map((item) => ({
      key: `quote-${item.id}`,
      label: 'Quotation created',
      detail: `${item.quotationNumber} · ${item.customerName}`,
      at: item.createdAt,
      to: `/quotations/${item.id}`,
      icon: FileText,
    })),
    ...(customers.data?.content || []).map((item) => ({
      key: `customer-${item.id}`,
      label: 'Customer added',
      detail: item.companyName,
      at: item.createdAt,
      to: `/customers/${item.id}`,
      icon: Users,
    })),
  ]
    .filter((item) => item.at && !Number.isNaN(new Date(item.at).getTime()))
    .sort((a, b) => new Date(b.at!).getTime() - new Date(a.at!).getTime())
    .slice(0, 4);
  const firstName = me.data?.fullName?.split(' ')[0] || 'there';
  const hour = new Date().getHours();
  const greeting =
    hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const openAi = () => window.dispatchEvent(new Event('ff-open-global-ai'));

  return (
    <div className="dashboard-page dashboard-reference ops-dashboard">
      <header className="ops-hero">
        <div>
          <h1>
            {greeting}, {firstName} 👋
          </h1>
          <p>Here’s your freight operations overview.</p>
        </div>
        <Link to="/enquiries/new" className="ops-hero-action">
          <Plus size={17} /> New Enquiry
        </Link>
      </header>
      <div className="ops-content">
        {summary.isError && (
          <p className="ops-error" role="alert">
            Dashboard summary is unavailable. Recent records may still appear
            below.
          </p>
        )}
        <section className="ops-kpis" aria-label="Key metrics">
          <Kpi
            label="New Enquiries"
            value={data?.newEnquiries}
            icon={MessageSquareText}
            tone="green"
            to="/enquiries"
            hint="Awaiting quotation"
          />
          <Kpi
            label="Quotes Pending"
            value={data?.quotesPending}
            icon={FileText}
            tone="amber"
            to="/quotations"
            hint="Draft quotations"
          />
          <Kpi
            label="Quotes Sent"
            value={data?.quotesSent}
            icon={Send}
            tone="blue"
            to="/quotations"
            hint="Sent to customers"
          />
          <Kpi
            label="Active Customers"
            value={activeCustomers.data?.totalElements}
            icon={Users}
            tone="purple"
            to="/customers"
            hint="Current active records"
          />
        </section>

        <div className="ops-top-row">
          <section className="ops-ai-brief" aria-labelledby="ops-ai-title">
            <div className="ops-ai-main">
              <div className="ops-ai-title">
                <Sparkles size={22} />
                <h2 id="ops-ai-title">FreightFlow AI – Operations Brief</h2>
                <span>Live records</span>
              </div>
              <p className="ops-brief-note">
                Deterministic summary based on current records; no AI response
                generated.
              </p>
              <ul>
                {data?.newEnquiries ? (
                  <li>
                    <span />
                    {data.newEnquiries} new{' '}
                    {data.newEnquiries === 1
                      ? 'enquiry requires'
                      : 'enquiries require'}{' '}
                    quotation review.
                  </li>
                ) : null}
                {data?.quotesPending ? (
                  <li>
                    <span />
                    {data.quotesPending} draft{' '}
                    {data.quotesPending === 1
                      ? 'quotation needs'
                      : 'quotations need'}{' '}
                    review.
                  </li>
                ) : null}
                {data?.expiringQuotes?.length ? (
                  <li>
                    <span />
                    {data.expiringQuotes.length} draft{' '}
                    {data.expiringQuotes.length === 1
                      ? 'quotation is'
                      : 'quotations are'}{' '}
                    nearing expiry.
                  </li>
                ) : null}
                {pendingEnquiries[0] && (
                  <li>
                    <span />
                    Prioritize {pendingEnquiries[0].enquiryCode} for{' '}
                    {pendingEnquiries[0].customerName}.
                  </li>
                )}
                {summary.isPending && (
                  <li>
                    <span />
                    Loading operational priorities…
                  </li>
                )}
                {summary.isError && (
                  <li>
                    <span />
                    Operational priorities are unavailable.
                  </li>
                )}
                {summary.isSuccess &&
                  !data?.newEnquiries &&
                  !data?.quotesPending &&
                  !data?.expiringQuotes?.length && (
                    <li>
                      <span />
                      No pending enquiries or draft quotations in the current
                      summary.
                    </li>
                  )}
              </ul>
              <Link to="/enquiries" className="ops-brief-review">
                Review pending work <ArrowRight size={15} />
              </Link>
            </div>
            <div className="ops-ai-help">
              <span className="ops-ai-avatar">
                <Sparkles size={23} />
              </span>
              <strong>Need help today?</strong>
              <p>Ask about your freight workflow, customers or quotations.</p>
              <button type="button" onClick={openAi}>
                Ask FreightFlow AI <ArrowRight size={15} />
              </button>
            </div>
          </section>
          <section className="ops-panel ops-quick">
            <PanelHeading icon={Plus} title="Quick Actions" />
            <div className="ops-action-grid">
              <Link to="/enquiries/new">
                <MessageSquareText size={19} />
                New Enquiry
              </Link>
              <Link
                to="/enquiries"
                title="Choose an enquiry to create a quotation"
              >
                <FileText size={19} />
                New Quotation
              </Link>
              <Link to="/customers/new">
                <Users size={19} />
                Add Customer
              </Link>
              <Link to="/quotations">
                <CheckCircle2 size={19} />
                View Quotations
              </Link>
            </div>
          </section>
        </div>

        <div className="ops-middle-row">
          <section className="ops-panel ops-attention">
            <PanelHeading
              icon={AlertTriangle}
              title="Needs Attention"
              to="/enquiries"
            />
            <div className="ops-list">
              {attention.length ? (
                attention.map((item) => (
                  <Link key={item.key} to={item.to} className="ops-list-row">
                    <span className="ops-row-icon">
                      <item.icon size={18} />
                    </span>
                    <span className="ops-row-main">
                      <strong>{item.label}</strong>
                      <small>{item.detail}</small>
                    </span>
                    <StatusBadge value={item.status} />
                    <ArrowRight size={15} />
                  </Link>
                ))
              ) : (
                <p className="ops-empty">
                  No pending records in the latest results.
                </p>
              )}
            </div>
          </section>
          <section className="ops-panel ops-activity">
            <PanelHeading icon={Clock3} title="Recent Activity" />
            <div className="ops-list">
              {activity.length ? (
                activity.map((item) => (
                  <Link key={item.key} to={item.to} className="ops-list-row">
                    <span className="ops-row-icon">
                      <item.icon size={17} />
                    </span>
                    <span className="ops-row-main">
                      <strong>{item.label}</strong>
                      <small>{item.detail}</small>
                    </span>
                    <time>{activityDate(item.at)}</time>
                  </Link>
                ))
              ) : (
                <p className="ops-empty">No dated activity is available yet.</p>
              )}
            </div>
          </section>
          <section className="ops-panel ops-overview">
            <PanelHeading icon={CalendarDays} title="Operations at a glance" />
            <div className="ops-overview-stats">
              <div>
                <strong>{data?.newEnquiries ?? '—'}</strong>
                <span>New enquiries</span>
              </div>
              <div>
                <strong>{data?.quotesPending ?? '—'}</strong>
                <span>Draft quotes</span>
              </div>
              <div>
                <strong>{data?.quotesSent ?? '—'}</strong>
                <span>Sent quotes</span>
              </div>
            </div>
            <p>
              Shipment tracking statistics will appear when shipment records are
              available.
            </p>
          </section>
        </div>

        <div className="ops-table-row">
          <section className="ops-panel ops-data-panel">
            <PanelHeading
              icon={MessageSquareText}
              title="Recent Enquiries"
              to="/enquiries"
            />
            <div className="ops-table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Enquiry ID</th>
                    <th>Customer</th>
                    <th>Route</th>
                    <th>Status</th>
                    <th>Date</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {recentEnquiries.map((item) => (
                    <tr
                      key={item.id}
                      onClick={() => navigate(`/enquiries/${item.id}`)}
                    >
                      <td>
                        <Link to={`/enquiries/${item.id}`}>
                          {item.enquiryCode}
                        </Link>
                      </td>
                      <td>{item.customerName}</td>
                      <td>{route(item.origin, item.destination)}</td>
                      <td>
                        <StatusBadge value={item.status} />
                      </td>
                      <td>{activityDate(item.createdAt)}</td>
                      <td>
                        <Link
                          to={`/enquiries/${item.id}`}
                          aria-label={`Open ${item.enquiryCode}`}
                        >
                          <ArrowRight size={15} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!recentEnquiries.length && (
                <p className="ops-empty">No enquiries yet.</p>
              )}
            </div>
          </section>
          <section className="ops-panel ops-data-panel">
            <PanelHeading
              icon={FileText}
              title="Recent Quotations"
              to="/quotations"
            />
            <div className="ops-table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Quotation ID</th>
                    <th>Customer</th>
                    <th>Route</th>
                    <th>Total</th>
                    <th>Status</th>
                    <th>Date</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {recentQuotes.map((item) => (
                    <tr
                      key={item.id}
                      onClick={() => navigate(`/quotations/${item.id}`)}
                    >
                      <td>
                        <Link to={`/quotations/${item.id}`}>
                          {item.quotationNumber}
                        </Link>
                      </td>
                      <td>{item.customerName}</td>
                      <td>{quoteRoute(item.shipmentSnapshot)}</td>
                      <td>
                        {item.totalAmount != null
                          ? `${item.currency || ''} ${fmt(item.totalAmount)}`
                          : '—'}
                      </td>
                      <td>
                        <StatusBadge value={item.status} />
                      </td>
                      <td>{activityDate(item.createdAt)}</td>
                      <td>
                        <Link
                          to={`/quotations/${item.id}`}
                          aria-label={`Open ${item.quotationNumber}`}
                        >
                          <ArrowRight size={15} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!recentQuotes.length && (
                <p className="ops-empty">No quotations yet.</p>
              )}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
