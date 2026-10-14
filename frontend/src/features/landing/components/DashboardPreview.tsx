import {
  ArrowRight,
  BarChart3,
  Bell,
  ChevronRight,
  FileText,
  Inbox,
  ListFilter,
  Search,
  ShieldCheck,
  Sparkles,
  Users,
} from 'lucide-react';
import { useState } from 'react';

export function DashboardPreview({ dense = false }: { dense?: boolean }) {
  const [selected, setSelected] = useState('Inbox');
  const [mail, setMail] = useState(0);
  const nav = [
    { icon: Inbox, name: 'Inbox' },
    { icon: Users, name: 'Customers' },
    { icon: FileText, name: 'Quotations' },
    { icon: BarChart3, name: 'Rate desk' },
  ];
  const items = [
    {
      from: 'Northstar Trading',
      sub: 'RFQ · Shanghai to Hamburg',
      time: '10:42',
      initial: 'NT',
      type: 'Ocean freight',
      content:
        'Hi team, please quote your best ocean freight rate for one 40HC container from Shanghai to Hamburg. The cargo will be ready next week. Please advise.',
    },
    {
      from: 'Coastal Imports',
      sub: 'Request · Bangkok to Dubai',
      time: '09:18',
      initial: 'CI',
      type: 'Freight enquiry',
      content:
        'Could you please share rates for our upcoming Bangkok to Dubai movement? Please let us know the available options and transit.',
    },
    {
      from: 'Atlas Distribution',
      sub: 'Re: Quotation clarification',
      time: 'Yesterday',
      initial: 'AD',
      type: 'Follow-up',
      content:
        'Thank you for the quotation. Could you clarify the destination charges and rate validity?',
    },
  ];
  return (
    <div className={'dashboard ' + (dense ? 'dashboard-dense' : '')}>
      <aside className="dash-sidebar">
        <div className="dash-sidebar-brand">
          <span className="small-mark">F</span>
          <span>freightflow</span>
        </div>
        <div className="dash-nav-label">WORKSPACE</div>
        {nav.map((n) => (
          <button
            key={n.name}
            className={'dash-nav ' + (selected === n.name ? 'active' : '')}
            onClick={() => setSelected(n.name)}
          >
            <n.icon size={15} />
            {n.name}
            {n.name === 'Inbox' && <span className="nav-count">3</span>}
          </button>
        ))}
        <div className="dash-sidebar-bottom">
          <ShieldCheck size={14} /> Your workspace
        </div>
      </aside>
      <div className="dash-main">
        <div className="dash-header">
          <div className="dash-breadcrumb">
            <span>Workspace</span>
            <ChevronRight size={12} />
            <b>{selected}</b>
          </div>
          <div className="dash-header-icons">
            <Search size={15} />
            <Bell size={15} />
            <span className="avatar">JS</span>
          </div>
        </div>
        {selected === 'Inbox' ? (
          <div className="dash-content">
            <div className="dash-list">
              <div className="dash-list-title">
                <div>
                  <h4>Inbox</h4>
                  <span>Customer conversations</span>
                </div>
                <ListFilter size={15} />
              </div>
              <div className="dash-tabs">
                <b>All mail</b>
                <span>Enquiries</span>
                <span>Follow ups</span>
              </div>
              {items.map((item, i) => (
                <button
                  className={'dash-mail ' + (mail === i ? 'selected' : '')}
                  key={item.from}
                  onClick={() => setMail(i)}
                >
                  <span className="mail-avatar">{item.initial}</span>
                  <span className="mail-info">
                    <b>{item.from}</b>
                    <small>{item.sub}</small>
                    <em>{item.type}</em>
                  </span>
                  <span className="mail-time">{item.time}</span>
                </button>
              ))}
            </div>
            <div className="dash-reader">
              <div className="dash-reader-top">
                <span className="status-pip" /> Enquiry detected{' '}
                <span className="reader-dots">···</span>
              </div>
              <span className="reader-subject">{items[mail].sub}</span>
              <div className="reader-from">
                <span className="mail-avatar">{items[mail].initial}</span>
                <div>
                  <b>{items[mail].from}</b>
                  <span>sales@customer.example</span>
                </div>
              </div>
              <p className="reader-copy">{items[mail].content}</p>
              <div className="dash-extract">
                <div className="extract-title">
                  <Sparkles size={15} /> AI shipment review <span>Draft</span>
                </div>
                <div>
                  <span>Route</span>
                  <b>
                    {mail === 0
                      ? 'Shanghai → Hamburg'
                      : mail === 1
                        ? 'Bangkok → Dubai'
                        : 'Customer follow-up'}
                  </b>
                </div>
                <div>
                  <span>Mode</span>
                  <b>{mail === 2 ? 'Review required' : 'Ocean freight'}</b>
                </div>
                <div>
                  <span>Status</span>
                  <b>Requires confirmation</b>
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="dash-placeholder">
            <span className="dash-placeholder-icon">
              {selected === 'Customers' ? (
                <Users size={23} />
              ) : selected === 'Quotations' ? (
                <FileText size={23} />
              ) : (
                <BarChart3 size={23} />
              )}
            </span>
            <h4>{selected}</h4>
            <p>One connected workspace for freight forwarding teams.</p>
            <a
              href="#platform"
              onClick={(event) => {
                event.preventDefault();
                setSelected('Inbox');
              }}
            >
              Back to inbox <ArrowRight size={14} />
            </a>
          </div>
        )}
      </div>
    </div>
  );
}
