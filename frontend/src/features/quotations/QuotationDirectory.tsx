import { Plus, Search } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Empty,
  Heading,
  Pager,
  State,
  StatusBadge,
} from '../../shared/components/PageElements';
import { date, fmt, useData } from '../../shared/lib/workspace';
import { QuotationPreview } from './QuotationDocument';
import { quotationActions } from './quotationActions';

type QuotationRow = {
  id: string;
  quotationNumber: string;
  customerName: string;
  currency: string;
  totalAmount: number;
  status: string;
  createdAt: string;
  shipmentSnapshot: string | null;
};

const tabs = [
  ['', 'All'],
  ['DRAFT', 'Drafts'],
  ['READY_TO_SEND', 'Ready to send'],
  ['SENT', 'Sent'],
] as const;

function shipment(snapshot: string | null) {
  if (!snapshot) return {} as Record<string, any>;
  try {
    return JSON.parse(snapshot) as Record<string, any>;
  } catch {
    return {} as Record<string, any>;
  }
}

export default function QuotationDirectory() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [previewId, setPreviewId] = useState<string | null>(null);
  const q = useData(
    'quotes',
    `/quotations?page=${page}&search=${encodeURIComponent(search)}&status=${status}`,
  );
  const me = useData('me', '/auth/me');
  const canCreate = ['ADMIN', 'SALES', 'SALES_MANAGER', 'PRICING'].includes(
    me.data?.role || '',
  );
  return (
    <div className="quotation-directory">
      <Heading
        title="Quotations"
        subtitle={`${q.data?.totalElements ?? '—'} quotations in this view`}
        action={
          canCreate && (
            <Link to="/enquiries" className="btn primary">
              <Plus size={16} /> New quotation
            </Link>
          )
        }
      />
      <div className="quote-directory-toolbar">
        <div className="quote-tabs" role="group" aria-label="Quotation status">
          {tabs.map(([value, label]) => (
            <button
              key={value}
              type="button"
              aria-pressed={status === value}
              className={status === value ? 'active' : ''}
              onClick={() => {
                setStatus(value);
                setPage(0);
              }}
            >
              {label}
            </button>
          ))}
        </div>
        <label className="filter-search quote-directory-search">
          <Search size={16} />
          <input
            aria-label="Search quotations"
            placeholder="Search quotation number"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
          />
        </label>
      </div>
      <State query={q}>
        {q.data?.content?.length ? (
          <>
            <div className="card quote-directory-table-wrap">
              <table className="quote-directory-table">
                <thead>
                  <tr>
                    <th>Quotation</th>
                    <th>Customer</th>
                    <th>Route</th>
                    <th>Cargo</th>
                    <th className="number">Total</th>
                    <th>Status</th>
                    <th>Shipment</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {q.data.content.map((row: QuotationRow) => {
                    const details = shipment(row.shipmentSnapshot);
                    const actions = quotationActions(row.status, me.data?.role);
                    const cargo = [
                      details.containerQuantity && details.containerType
                        ? `${details.containerQuantity} × ${details.containerType}`
                        : null,
                      details.commodity,
                    ]
                      .filter(Boolean)
                      .join(' · ');
                    return (
                      <tr key={row.id}>
                        <td>
                          <button
                            type="button"
                            className="quote-directory-number"
                            onClick={() => setPreviewId(row.id)}
                          >
                            {row.quotationNumber}
                          </button>
                          <small>{date(row.createdAt)}</small>
                        </td>
                        <td className="quote-directory-customer">
                          {row.customerName}
                        </td>
                        <td>
                          {details.origin && details.destination
                            ? `${details.origin} → ${details.destination}`
                            : '—'}
                        </td>
                        <td>{cargo || '—'}</td>
                        <td className="number quote-directory-total">
                          {row.currency} {fmt(row.totalAmount)}
                        </td>
                        <td>
                          <StatusBadge value={row.status} />
                        </td>
                        <td className="quote-directory-muted">—</td>
                        <td>
                          <div className="quote-directory-actions">
                            <button
                              type="button"
                              className="quote-directory-view"
                              onClick={() => setPreviewId(row.id)}
                              aria-label={`Preview ${row.quotationNumber}`}
                            >
                              Preview
                            </button>
                            {actions.continueDraft && (
                              <Link to={`/quotations/${row.id}/edit`}>
                                Continue draft
                              </Link>
                            )}
                            {actions.prepareEmail && (
                              <Link to={`/quotations/${row.id}`}>
                                Prepare email
                              </Link>
                            )}
                            {actions.viewInMail && (
                              <Link to={`/mail/stored?quotationId=${row.id}`}>
                                View in Mail
                              </Link>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Pager data={q.data} page={page} setPage={setPage} />
          </>
        ) : (
          <Empty
            text="No quotations in this view"
            description="Create a quotation from an enquiry."
          />
        )}
      </State>
      {previewId && (
        <QuotationPreview
          id={previewId}
          onClose={() => setPreviewId(null)}
          role={me.data?.role}
        />
      )}
    </div>
  );
}
