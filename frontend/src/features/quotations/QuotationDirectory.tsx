import { useQuery } from '@tanstack/react-query';
import { Check, FileText, Plus, Search, Send } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  CrmAskAiButton,
  CrmDetailPanel,
  CrmFacts,
  CrmMetric,
} from '../../shared/components/CrmListParts';
import {
  Empty,
  Heading,
  Pager,
  State,
  StatusBadge,
} from '../../shared/components/PageElements';
import { api, date, fmt, useData } from '../../shared/lib/workspace';
import { QuotationPreview } from './QuotationDocument';
import { quotationActions } from './quotationActions';
import { QuotationRouteLabel } from './quotationRoute';

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
  const [from, setFrom] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);
  const [detailTab, setDetailTab] = useState('Overview');
  const [previewId, setPreviewId] = useState<string | null>(null);
  const q = useData(
    'quotes',
    `/quotations?page=${page}&search=${encodeURIComponent(search)}&status=${status}&from=${from}`,
  );
  const total = useData('quote-count-total', '/quotations?size=1');
  const draftCount = useData(
    'quote-count-draft',
    '/quotations?status=DRAFT&size=1',
  );
  const readyCount = useData(
    'quote-count-ready',
    '/quotations?status=READY_TO_SEND&size=1',
  );
  const sentCount = useData(
    'quote-count-sent',
    '/quotations?status=SENT&size=1',
  );
  const selectQuotation = (id: string) => {
    setSelectedId(id);
    setPanelOpen(true);
    setDetailTab('Overview');
  };
  const detail = useQuery({
    queryKey: ['quote-list-detail', selectedId],
    queryFn: () => api(`/quotations/${selectedId}`),
    enabled: panelOpen && !!selectedId,
  });
  const me = useData('me', '/auth/me');
  const selectedActions = quotationActions(
    detail.data?.status || '',
    me.data?.role,
  );
  const canCreate = ['ADMIN', 'SALES', 'SALES_MANAGER', 'PRICING'].includes(
    me.data?.role || '',
  );
  return (
    <div className="quotation-directory crm-suite crm-quotations">
      <Heading
        title="Quotations"
        subtitle={`${q.data?.totalElements ?? '—'} quotations in this view`}
        action={
          <>
            <CrmAskAiButton />
            {canCreate && (
              <Link to="/enquiries" className="btn primary">
                <Plus size={16} /> New quotation
              </Link>
            )}
          </>
        }
      />
      <div className="crm-metrics">
        <CrmMetric
          label="Total quotations"
          value={total.data?.totalElements}
          icon={FileText}
        />
        <CrmMetric
          label="Drafts"
          value={draftCount.data?.totalElements}
          icon={FileText}
          tone="violet"
        />
        <CrmMetric
          label="Ready to send"
          value={readyCount.data?.totalElements}
          icon={Check}
          tone="amber"
        />
        <CrmMetric
          label="Sent"
          value={sentCount.data?.totalElements}
          icon={Send}
          tone="green"
        />
      </div>
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
        <input
          className="field quote-directory-date"
          type="date"
          aria-label="Created from"
          value={from}
          onChange={(event) => {
            setFrom(event.target.value);
            setPage(0);
          }}
        />
        {(search || status || from) && (
          <button
            type="button"
            className="crm-clear"
            onClick={() => {
              setSearch('');
              setStatus('');
              setFrom('');
              setPage(0);
            }}
          >
            Clear
          </button>
        )}
      </div>
      <div
        className={`crm-record-layout${panelOpen && selectedId ? '' : ' crm-panel-hidden'}`}
      >
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
                    </tr>
                  </thead>
                  <tbody>
                    {q.data.content.map((row: QuotationRow) => {
                      const details = shipment(row.shipmentSnapshot);
                      const cargo = [
                        details.containerQuantity && details.containerType
                          ? `${details.containerQuantity} × ${details.containerType}`
                          : null,
                        details.commodity,
                      ]
                        .filter(Boolean)
                        .join(' · ');
                      return (
                        <tr
                          key={row.id}
                          className={
                            panelOpen && selectedId === row.id
                              ? 'crm-selected'
                              : ''
                          }
                          aria-selected={panelOpen && selectedId === row.id}
                          tabIndex={0}
                          onClick={() => selectQuotation(row.id)}
                          onKeyDown={(event) => {
                            if (event.target !== event.currentTarget) return;
                            if (event.key === 'Enter' || event.key === ' ') {
                              event.preventDefault();
                              selectQuotation(row.id);
                            }
                          }}
                        >
                          <td data-label="Quotation">
                            <button
                              type="button"
                              className="quote-directory-number"
                              title={row.quotationNumber}
                              aria-label={`Show details for ${row.quotationNumber}`}
                              onClick={(event) => {
                                event.stopPropagation();
                                selectQuotation(row.id);
                              }}
                            >
                              {row.quotationNumber}
                            </button>
                            <small>{date(row.createdAt)}</small>
                          </td>
                          <td
                            data-label="Customer"
                            className="quote-directory-customer"
                          >
                            <span
                              className="list-cell-clamp"
                              title={row.customerName}
                              tabIndex={0}
                            >
                              {row.customerName}
                            </span>
                          </td>
                          <td data-label="Route">
                            {details.origin && details.destination ? (
                              <QuotationRouteLabel shipment={details} compact />
                            ) : (
                              '—'
                            )}
                          </td>
                          <td data-label="Cargo">
                            <span
                              className="list-cell-clamp two-lines"
                              title={cargo || undefined}
                              tabIndex={0}
                            >
                              {cargo || '—'}
                            </span>
                          </td>
                          <td
                            data-label="Total"
                            className="number quote-directory-total"
                          >
                            {row.currency} {fmt(row.totalAmount)}
                          </td>
                          <td data-label="Status">
                            <StatusBadge value={row.status} />
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
        {panelOpen && selectedId && (
          <CrmDetailPanel
            title={
              detail.data?.quotationNumber ||
              q.data?.content?.find(
                (item: QuotationRow) => item.id === selectedId,
              )?.quotationNumber ||
              'Quotation'
            }
            subtitle={detail.data?.customerName}
            badge={
              detail.data?.status && <StatusBadge value={detail.data.status} />
            }
            tabs={['Overview', 'Charges', 'Source']}
            activeTab={detailTab}
            onTab={setDetailTab}
            onClose={() => {
              if (!previewId) setPanelOpen(false);
            }}
            actions={
              <>
                <button
                  type="button"
                  className="btn primary"
                  onClick={() => setPreviewId(selectedId)}
                >
                  Preview / PDF
                </button>
                {selectedActions.continueDraft && (
                  <Link
                    className="btn secondary"
                    to={`/quotations/${selectedId}/edit`}
                  >
                    Edit draft
                  </Link>
                )}
                {selectedActions.prepareEmail && (
                  <Link
                    className="btn secondary"
                    to={`/quotations/${selectedId}`}
                  >
                    Prepare email
                  </Link>
                )}
                {selectedActions.viewInMail && (
                  <Link
                    className="btn secondary"
                    to={`/mail/stored?quotationId=${selectedId}`}
                  >
                    View in Mail
                  </Link>
                )}
              </>
            }
          >
            {detail.isLoading ? (
              <p>Loading quotation…</p>
            ) : detail.isError ? (
              <p role="alert">Could not load quotation details.</p>
            ) : detailTab === 'Overview' ? (
              <>
                <h3>Quotation details</h3>
                <CrmFacts
                  items={[
                    { label: 'Customer', value: detail.data?.customerName },
                    {
                      label: 'Route',
                      value: detail.data?.shipmentSnapshot
                        ? (() => {
                            const route = shipment(
                              detail.data.shipmentSnapshot,
                            );
                            return route.origin && route.destination
                              ? `${route.origin} → ${route.destination}`
                              : '—';
                          })()
                        : '—',
                    },
                    {
                      label: 'Cargo / equipment',
                      value: detail.data?.shipmentSnapshot
                        ? (() => {
                            const cargo = shipment(
                              detail.data.shipmentSnapshot,
                            );
                            return (
                              [
                                cargo.containerQuantity && cargo.containerType
                                  ? `${cargo.containerQuantity} × ${cargo.containerType}`
                                  : null,
                                cargo.commodity,
                              ]
                                .filter(Boolean)
                                .join(' · ') || '—'
                            );
                          })()
                        : '—',
                    },
                    {
                      label: 'Total',
                      value:
                        detail.data?.totalAmount != null
                          ? `${detail.data.currency} ${fmt(detail.data.totalAmount)}`
                          : '—',
                    },
                    { label: 'Status', value: detail.data?.status },
                    { label: 'Created', value: date(detail.data?.createdAt) },
                    {
                      label: 'Valid until',
                      value: detail.data?.validUntil
                        ? date(detail.data.validUntil)
                        : '—',
                    },
                  ]}
                />
              </>
            ) : detailTab === 'Charges' ? (
              <div className="crm-panel-list">
                {detail.data?.lines?.length
                  ? detail.data.lines.map((line: any) => (
                      <div key={line.id || line.sortOrder}>
                        <strong>{line.description}</strong>
                        <small>
                          {detail.data.currency} {fmt(line.amount)}
                        </small>
                      </div>
                    ))
                  : 'No charges available.'}
              </div>
            ) : detail.data?.enquiryId ? (
              <Link to={`/enquiries/${detail.data.enquiryId}`}>
                Open source enquiry
              </Link>
            ) : (
              <p>No source enquiry linked.</p>
            )}
          </CrmDetailPanel>
        )}
      </div>
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
