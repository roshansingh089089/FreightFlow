import { useQuery } from '@tanstack/react-query';
import { Download, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link } from 'react-router-dom';
import { apiFetch } from '../../api';
import { StatusBadge } from '../../shared/components/PageElements';
import { api } from '../../shared/lib/workspace';
import { quotationActions } from './quotationActions';

export type QuotationCharge = {
  description: string;
  scope: string;
  quantity: number;
  basis: string;
  originalCurrency: string;
  originalUnitPrice: number;
  convertedUnitPrice: number;
  convertedAmount: number;
  fxRate: number | null;
  fxReferenceRate: number | null;
  fxSource: string | null;
  fxObservationDate: string | null;
  fxBufferPercent: number | null;
};

export type QuotationDocumentModel = {
  number: string;
  status: string;
  companyName: string;
  companyCountry: string | null;
  customerName: string;
  contactName: string | null;
  contactPosition: string | null;
  contactEmail: string | null;
  customerAddress: string | null;
  preparedBy: string | null;
  preparedByEmail: string | null;
  issueDate: string | null;
  validUntil: string | null;
  customerReference: string | null;
  mode: string | null;
  origin: string | null;
  destination: string | null;
  finalDestination?: string | null;
  cargo: string | null;
  incoterm: string | null;
  transitTime: string | null;
  currency: string;
  charges: QuotationCharge[];
  total: number;
  included: string[];
  excluded: string[];
  terms: string | null;
};

const money = (value: number | null | undefined) =>
  new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value || 0));

const displayDate = (value: string | null) =>
  value
    ? new Intl.DateTimeFormat('en-GB', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        timeZone: 'UTC',
      }).format(new Date(`${value}T12:00:00Z`))
    : '—';

export function QuotationDocument({
  model,
}: {
  model: QuotationDocumentModel;
}) {
  const grouped = model.charges.reduce<Record<string, QuotationCharge[]>>(
    (groups, charge) => {
      (groups[charge.scope] ||= []).push(charge);
      return groups;
    },
    {},
  );
  const fx = model.charges.filter(
    (charge) => charge.fxRate && charge.originalCurrency !== model.currency,
  );
  return (
    <article
      className="quote-document"
      aria-label={`Quotation ${model.number}`}
    >
      <div className="quote-document-accent" />
      <header className="quote-document-head">
        <div className="quote-document-brand">
          <span className="quote-document-mark" aria-hidden="true">
            {model.companyName?.charAt(0) || 'F'}
          </span>
          <div>
            <strong>{model.companyName}</strong>
            {model.companyCountry && <small>{model.companyCountry}</small>}
          </div>
        </div>
        <div className="quote-document-title">
          <h2>Quotation</h2>
          <strong>{model.number}</strong>
        </div>
      </header>
      <div className="quote-document-parties">
        <div>
          <span className="quote-document-kicker">Prepared for</span>
          <strong>{model.customerName}</strong>
          {model.contactName && (
            <span>
              Attn. {model.contactName}
              {model.contactPosition ? `, ${model.contactPosition}` : ''}
            </span>
          )}
          {model.customerAddress && <span>{model.customerAddress}</span>}
          {model.contactEmail && <span>{model.contactEmail}</span>}
        </div>
        <dl>
          <div>
            <dt>Issue date</dt>
            <dd>{displayDate(model.issueDate)}</dd>
          </div>
          <div>
            <dt>Valid until</dt>
            <dd>{displayDate(model.validUntil)}</dd>
          </div>
          {model.customerReference && (
            <div>
              <dt>Your reference</dt>
              <dd>{model.customerReference}</dd>
            </div>
          )}
          {model.preparedBy && (
            <div>
              <dt>Prepared by</dt>
              <dd>
                {model.preparedBy}
                {model.preparedByEmail && (
                  <small>{model.preparedByEmail}</small>
                )}
              </dd>
            </div>
          )}
        </dl>
      </div>
      <section
        className="quote-document-shipment"
        aria-label="Shipment summary"
      >
        {[
          ['Mode', model.mode],
          ['POL / origin', model.origin],
          ['POD / discharge', model.destination],
          ...(model.finalDestination
            ? ([['Final place of delivery', model.finalDestination]] as const)
            : []),
          ['Cargo', model.cargo],
          ['Incoterm', model.incoterm],
        ].map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <strong>{value || '—'}</strong>
          </div>
        ))}
      </section>
      {model.transitTime && (
        <div className="quote-document-transit">
          Transit time <strong>{model.transitTime}</strong>
        </div>
      )}
      <div className="quote-document-table-wrap">
        <table className="quote-document-table">
          <thead>
            <tr>
              <th>Description</th>
              <th>Basis</th>
              <th className="number">Qty</th>
              <th className="number">Original rate</th>
              <th className="number">Amount {model.currency}</th>
            </tr>
          </thead>
          {Object.entries(grouped).map(([scope, charges]) => (
            <tbody key={scope}>
              <tr className="quote-document-group">
                <th colSpan={5}>{scope}</th>
              </tr>
              {charges.map((charge, index) => (
                <tr key={`${scope}-${index}`}>
                  <td>{charge.description}</td>
                  <td>{charge.basis}</td>
                  <td className="number">{charge.quantity}</td>
                  <td className="number">
                    {charge.originalCurrency} {money(charge.originalUnitPrice)}
                  </td>
                  <td className="number">{money(charge.convertedAmount)}</td>
                </tr>
              ))}
            </tbody>
          ))}
          {!model.charges.length && (
            <tbody>
              <tr>
                <td colSpan={5}>No charges saved yet.</td>
              </tr>
            </tbody>
          )}
        </table>
      </div>
      <div className="quote-document-financials">
        <div className="quote-document-fx">
          {fx.length > 0 && (
            <>
              <span className="quote-document-kicker">FX applied</span>
              {fx.map((charge, index) => (
                <p key={index}>
                  {charge.originalCurrency} → {model.currency}: {charge.fxRate}
                  {charge.fxSource ? ` · ${charge.fxSource}` : ''}
                  {charge.fxReferenceRate
                    ? ` · reference ${charge.fxReferenceRate}`
                    : ''}
                  {charge.fxObservationDate
                    ? ` · ${displayDate(charge.fxObservationDate)}`
                    : ''}
                  {charge.fxBufferPercent
                    ? ` · ${charge.fxBufferPercent}% buffer`
                    : ''}
                </p>
              ))}
            </>
          )}
        </div>
        <div className="quote-document-total">
          <span>Total</span>
          <strong>
            {model.currency} {money(model.total)}
          </strong>
        </div>
      </div>
      <div className="quote-document-inclusions">
        <section>
          <h3>Included</h3>
          <ul>
            {model.included.length ? (
              model.included.map((item) => <li key={item}>{item}</li>)
            ) : (
              <li>No priced services saved</li>
            )}
          </ul>
        </section>
        <section>
          <h3>Not included</h3>
          <ul>
            {model.excluded.length ? (
              model.excluded.map((item) => <li key={item}>{item}</li>)
            ) : (
              <li>No exclusions specified in saved quotation</li>
            )}
          </ul>
        </section>
      </div>
      {model.terms && (
        <section className="quote-document-terms">
          <h3>Terms</h3>
          <p>{model.terms}</p>
        </section>
      )}
      <footer className="quote-document-foot">
        <span>{model.companyName}</span>
        <span>{model.number}</span>
      </footer>
    </article>
  );
}

export function QuotationPreview({
  id,
  onClose,
  role,
}: {
  id: string;
  onClose: () => void;
  role?: string;
}) {
  const dialog = useRef<HTMLDivElement>(null);
  const [downloadError, setDownloadError] = useState('');
  const result = useQuery<QuotationDocumentModel>({
    queryKey: ['quotation-document', id],
    queryFn: () => api(`/quotations/${id}/document`),
  });
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialog.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab' || !dialog.current) return;
      const focusable = Array.from(
        dialog.current.querySelectorAll<HTMLElement>(
          'button:not(:disabled), a[href], [tabindex]:not([tabindex="-1"])',
        ),
      );
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
      previous?.focus();
    };
  }, [onClose]);
  const download = async () => {
    setDownloadError('');
    const response = await apiFetch(`/quotations/${id}/pdf`);
    if (!response.ok) {
      setDownloadError('Could not download the PDF.');
      return;
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = `${result.data?.number || 'quotation'}.pdf`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  };
  const actions = quotationActions(result.data?.status || '', role);
  return createPortal(
    <div
      className="quote-modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        className="quote-modal"
        role="dialog"
        aria-modal="true"
        aria-label={`Quotation preview`}
        tabIndex={-1}
        ref={dialog}
      >
        <header className="quote-modal-header">
          <strong>Quotation {result.data?.number || ''}</strong>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close quotation preview"
          >
            <X size={18} />
          </button>
        </header>
        <div className="quote-modal-scroll">
          {result.isLoading && (
            <p className="quote-modal-state">Loading quotation…</p>
          )}
          {result.isError && (
            <p role="alert" className="quote-modal-state">
              Could not load quotation.
            </p>
          )}
          {result.data && <QuotationDocument model={result.data} />}
        </div>
        <footer className="quote-modal-actions">
          {result.data && <StatusBadge value={result.data.status} />}
          {downloadError && <span role="alert">{downloadError}</span>}
          <div>
            <button
              className="btn secondary"
              type="button"
              onClick={() => void download()}
              disabled={!result.data}
            >
              <Download size={15} /> Download PDF
            </button>
            {actions.continueDraft && (
              <Link className="btn primary" to={`/quotations/${id}/edit`}>
                Continue draft
              </Link>
            )}
            {actions.prepareEmail && (
              <Link className="btn primary" to={`/quotations/${id}`}>
                Prepare email
              </Link>
            )}
            {actions.viewInMail && (
              <Link
                className="btn secondary"
                to={`/mail/stored?quotationId=${id}`}
              >
                View in Mail
              </Link>
            )}
          </div>
        </footer>
      </div>
    </div>,
    document.body,
  );
}
