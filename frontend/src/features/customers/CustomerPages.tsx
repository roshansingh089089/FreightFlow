import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { Sparkles } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import {
  Empty,
  Field,
  Heading,
  State,
} from '../../shared/components/PageElements';
import { api, post, put, qc, toast, useData } from '../../shared/lib/workspace';
import { AiActionCard } from '../ai/AiAssist';
import {
  CustomerActivityPanel,
  CustomerAddressesPanel,
  CustomerMailPanel,
  CustomerUsersPanel,
} from './CustomerProfileExtras';
import { customerPayload, customerSchema } from './customerForm';
export function CustomerForm() {
  const nav = useNavigate();
  const { id } = useParams();
  const [params] = useSearchParams();
  const sourceMessageId = !id ? params.get('sourceMessageId') : null;
  const source = useQuery({
    queryKey: ['customer-source-mail', sourceMessageId],
    queryFn: () => api(`/mail/messages/${sourceMessageId}`),
    enabled: !!sourceMessageId,
  });
  const existing = useQuery({
    queryKey: ['customer', id],
    queryFn: () => api(`/customers/${id}`),
    enabled: !!id,
  });
  const owners = useQuery({
    queryKey: ['tenant-users'],
    queryFn: () => api('/tenant/users'),
  });
  const form = useForm<any>({
    resolver: zodResolver(customerSchema),
    defaultValues: { customerType: '', status: 'ACTIVE' },
  });
  useEffect(() => {
    if (id && existing.data)
      form.reset({
        companyName: existing.data.companyName,
        customerType: existing.data.customerType,
        country: existing.data.country,
        contactName: existing.data.primaryContact?.name,
        contactEmail: existing.data.primaryContact?.email,
        contactPhone: existing.data.primaryContact?.phone,
        contactPosition: existing.data.primaryContact?.designation || '',
        addressLine1: existing.data.addresses?.[0]?.line1 || '',
        addressCity: existing.data.addresses?.[0]?.city || '',
        gstin: existing.data.gstin,
        iec: existing.data.iec,
        status: existing.data.status,
        creditTermsDays: existing.data.creditTermsDays?.toString() || '',
        creditLimit: existing.data.creditLimit?.toString() || '',
        salesOwnerUserId: existing.data.salesOwnerUserId || '',
      });
  }, [existing.data]);
  const [err, setErr] = useState('');
  const [extracting, setExtracting] = useState(false);
  const [prefilled, setPrefilled] = useState<string[]>([]);
  const [sourceWarnings, setSourceWarnings] = useState<string[]>([]);
  const [aiText, setAiText] = useState('');
  const [aiExpanded, setAiExpanded] = useState(false);
  const companyName = form.watch('companyName');
  const contactEmail = form.watch('contactEmail');
  const duplicates = useQuery({
    queryKey: ['customer-duplicates', companyName, contactEmail],
    queryFn: async () => {
      const terms = [companyName, contactEmail].filter(
        (value: string) => value?.trim().length >= 2,
      );
      const pages = await Promise.all(
        terms.map((term: string) =>
          api(`/customers?search=${encodeURIComponent(term.trim())}&size=8`),
        ),
      );
      return [
        ...new Map(
          pages
            .flatMap((page: any) => page.content || [])
            .map((customer: any) => [customer.id, customer]),
        ).values(),
      ] as any[];
    },
    enabled:
      !id &&
      !!(companyName?.trim().length >= 2 || contactEmail?.trim().length >= 2),
  });
  const normalizeCompany = (value: string) =>
    value.trim().replace(/\s+/g, ' ').toLocaleLowerCase();
  const exactDuplicate = duplicates.data?.find(
    (customer: any) =>
      companyName &&
      normalizeCompany(customer.companyName) === normalizeCompany(companyName),
  );
  const applyExtraction = (result: any) => {
    const values: Record<string, string> = {
      companyName: result.extracted?.customerName,
      country: result.extracted?.customerCountry,
      contactName: result.extracted?.contactName,
      contactEmail: result.extracted?.contactEmail,
      contactPhone: result.extracted?.contactPhone,
      contactPosition: result.extracted?.contactPosition,
      addressLine1: result.extracted?.customerAddress,
      addressCity: result.extracted?.customerCity,
    };
    const applied: string[] = [];
    for (const [name, value] of Object.entries(values)) {
      if (
        !value?.trim() ||
        form.getFieldState(name).isDirty ||
        form.getValues(name)
      )
        continue;
      form.setValue(name, value.trim(), { shouldValidate: true });
      applied.push(name);
    }
    setPrefilled(applied);
    setSourceWarnings(result.warnings || []);
    return applied;
  };
  const extractPastedCustomer = async () => {
    if (extracting || !aiText.trim()) return;
    setErr('');
    setExtracting(true);
    try {
      const result = await post('/ai/customers/extract', {
        text: aiText.trim(),
      });
      const applied = applyExtraction(result);
      if (applied.length) setAiExpanded(false);
      else if (
        !result.extracted?.customerName &&
        !result.extracted?.contactName
      )
        setErr(
          'No customer details were found. Check the pasted text or fill the form manually.',
        );
    } catch (error) {
      setErr(
        error instanceof Error
          ? error.message
          : 'AI extraction failed. Enter details manually.',
      );
    } finally {
      setExtracting(false);
    }
  };
  useEffect(() => {
    if (!source.data || !sourceMessageId) return;
    let cancelled = false;
    const extract = async () => {
      setExtracting(true);
      try {
        const text = [
          `Subject: ${source.data.subject || ''}`,
          `Sender: ${source.data.fromEmail || ''}`,
          source.data.bodyText || '',
        ]
          .join('\n')
          .slice(0, 10000);
        const result = await post('/ai/customers/extract', { text });
        if (cancelled) return;
        applyExtraction(result);
      } catch (error) {
        if (!cancelled)
          setErr(
            error instanceof Error
              ? error.message
              : 'AI extraction failed. Enter details manually.',
          );
      } finally {
        if (!cancelled) setExtracting(false);
      }
    };
    void extract();
    return () => {
      cancelled = true;
    };
  }, [source.data, sourceMessageId]);
  const submit = form.handleSubmit(async (v) => {
    try {
      if (!id && duplicates.isFetching) {
        setErr('Wait for the customer lookup before creating this record.');
        return;
      }
      if (!id && duplicates.isError) {
        setErr(
          'Customer lookup failed. Try again before creating this record.',
        );
        return;
      }
      if (!id && exactDuplicate) {
        setErr(
          `A customer named ${exactDuplicate.companyName} already exists. Review that record before creating another.`,
        );
        return;
      }
      const data = customerPayload(v);
      const r = id
        ? await put('/customers/' + id, data)
        : await post('/customers', data);
      if (sourceMessageId && !id) {
        try {
          await post(`/customers/${r.id}/emails/${sourceMessageId}`, {});
        } catch (linkError) {
          toast(
            `Customer created, but the source email could not be linked: ${linkError instanceof Error ? linkError.message : 'unknown error'}`,
          );
          nav('/customers/' + r.id);
          return;
        }
      }
      qc.invalidateQueries();
      toast(
        id ? 'Customer updated successfully' : 'Customer created successfully',
      );
      nav('/customers/' + r.id);
    } catch (e: any) {
      setErr(e.message);
    }
  });
  return (
    <div className="customer-form-page">
      <Heading title={id ? 'Edit Customer' : 'Add Customer'} />
      {sourceMessageId && (
        <div className="intake-source-note">
          <span>
            <Sparkles size={15} />{' '}
            {extracting
              ? 'AI extracting…'
              : prefilled.length
                ? 'AI Prefilled'
                : 'Source Email'}
          </span>
          <details>
            <summary>View Source Email</summary>
            {source.isPending && <p>Loading source email…</p>}
            {source.isError && (
              <p role="alert">
                Could not load the source email. Check mailbox access.
              </p>
            )}
            {source.data && (
              <div className="intake-source-body">
                <strong>{source.data.subject || '(no subject)'}</strong>
                <p>From: {source.data.fromEmail}</p>
                <pre>{source.data.bodyText || '(No text body)'}</pre>
              </div>
            )}
          </details>
        </div>
      )}
      {!id && !sourceMessageId && (
        <section
          className="customer-ai-intake"
          aria-label="AI customer prefill"
        >
          <div className="customer-ai-intake-head">
            <div>
              <Sparkles size={18} />
              <span>
                <strong>Fill customer details with AI</strong>
                <small>
                  Paste a customer email, signature or company details. Review
                  the form before creating.
                </small>
              </span>
            </div>
            <button
              type="button"
              className="btn secondary"
              aria-expanded={aiExpanded}
              onClick={() => setAiExpanded((value) => !value)}
            >
              {aiExpanded ? 'Hide input' : 'Paste details'}
            </button>
          </div>
          {aiExpanded && (
            <div className="customer-ai-intake-body">
              <label htmlFor="customer-ai-text">
                Customer email or signature
              </label>
              <textarea
                id="customer-ai-text"
                value={aiText}
                maxLength={10000}
                onChange={(event) => setAiText(event.target.value)}
                placeholder="Paste the customer’s email or signature…"
                rows={4}
              />
              <button
                type="button"
                className="btn primary"
                disabled={extracting || !aiText.trim()}
                onClick={() => void extractPastedCustomer()}
              >
                <Sparkles size={15} />{' '}
                {extracting ? 'Extracting…' : 'Fill with AI'}
              </button>
            </div>
          )}
          {prefilled.length > 0 && (
            <p role="status" className="customer-ai-intake-status">
              AI filled {prefilled.length} fields. Review and edit them before
              creating the customer.
            </p>
          )}
        </section>
      )}
      {sourceWarnings.map((warning, index) => (
        <p key={index} role="status" className="text-sm">
          {warning}
        </p>
      ))}
      <form onSubmit={submit} className="card customer-onboarding-form">
        <section
          className="customer-form-section"
          aria-labelledby="customer-company-heading"
        >
          <h2
            id="customer-company-heading"
            className="customer-form-section-title"
          >
            Company Details
          </h2>
          <div className="customer-form-fields">
            <Field form={form} name="companyName" label="Company Name *" />
            <label>
              <span className="label">Customer Type *</span>
              <select className="field" {...form.register('customerType')}>
                <option value="">Select type</option>
                {['EXPORTER', 'IMPORTER', 'FREIGHT_PARTNER', 'OTHER'].map(
                  (x) => (
                    <option key={x}>{x}</option>
                  ),
                )}
              </select>
            </label>
            <Field form={form} name="country" label="Country *" />
            <label>
              <span className="label">Status</span>
              <select className="field" {...form.register('status')}>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
            </label>
          </div>
        </section>
        <section
          className="customer-form-section"
          aria-labelledby="customer-contact-heading"
        >
          <h2
            id="customer-contact-heading"
            className="customer-form-section-title"
          >
            Primary Contact
          </h2>
          <div className="customer-form-fields">
            <Field form={form} name="contactName" label="Contact Name *" />
            <Field
              form={form}
              name="contactEmail"
              label="Email *"
              type="email"
            />
            <Field form={form} name="contactPhone" label="Phone *" />
            <Field form={form} name="contactPosition" label="Position" />
          </div>
        </section>
        <section
          className="customer-form-section"
          aria-labelledby="customer-additional-heading"
        >
          <h2
            id="customer-additional-heading"
            className="customer-form-section-title"
          >
            Additional Details
          </h2>
          <div className="customer-form-fields customer-form-fields-additional">
            <Field form={form} name="gstin" label="GSTIN" />
            <Field form={form} name="iec" label="IEC" />
            <div className="customer-form-field-wide">
              <Field form={form} name="addressLine1" label="Business address" />
            </div>
            <Field form={form} name="addressCity" label="Address city" />
            <Field
              form={form}
              name="creditTermsDays"
              label="Credit terms (days)"
              type="number"
            />
            <Field
              form={form}
              name="creditLimit"
              label="Credit limit"
              type="number"
            />
            <label>
              <span className="label">Account owner</span>
              <select className="field" {...form.register('salesOwnerUserId')}>
                <option value="">Assign to me</option>
                {owners.data?.map((owner: any) => (
                  <option key={owner.id} value={owner.id}>
                    {owner.fullName}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </section>
        {!id &&
          (duplicates.isFetching ||
            (duplicates.data?.length ?? 0) > 0 ||
            duplicates.isError) && (
            <div className="customer-duplicate-check" aria-live="polite">
              <strong>Possible existing customers</strong>
              {duplicates.isFetching && <p>Checking directory…</p>}
              {duplicates.isError && <p>Could not check the directory.</p>}
              {duplicates.data?.map((customer: any) => (
                <p key={customer.id}>
                  <Link
                    to={`/customers/${customer.id}`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {customer.companyName}
                  </Link>{' '}
                  · {customer.primaryContact?.email || 'No contact email'}
                </p>
              ))}
            </div>
          )}
        {err && (
          <p className="text-red-600" role="alert">
            {err}
          </p>
        )}
        <div className="customer-form-actions">
          <button
            type="button"
            className="btn secondary"
            onClick={() => nav(-1)}
          >
            Cancel
          </button>
          <button
            disabled={form.formState.isSubmitting}
            className="btn primary"
          >
            {form.formState.isSubmitting
              ? 'Saving...'
              : id
                ? 'Save Customer'
                : 'Create Customer'}
          </button>
        </div>
      </form>
    </div>
  );
}
export function CustomerDetail() {
  const { id } = useParams();
  const q = useData('customer', `/customers/${id}`);
  const me = useData('me', '/auth/me');
  const [archiveError, setArchiveError] = useState('');
  const [tab, setTab] = useState('Overview');
  return (
    <State query={q}>
      <Heading
        title={q.data?.companyName || ''}
        subtitle={`${q.data?.customerCode} · ${q.data?.customerType} · ${q.data?.country}`}
        action={
          <div className="flex gap-2">
            <Link className="btn secondary" to={'/customers/' + id + '/edit'}>
              Edit Customer
            </Link>
            {['ADMIN', 'SALES_MANAGER'].includes(me.data?.role) && (
              <button
                type="button"
                className="btn secondary"
                onClick={async () => {
                  if (
                    !window.confirm(
                      q.data?.archivedAt
                        ? 'Restore this customer?'
                        : 'Archive this customer?',
                    )
                  )
                    return;
                  try {
                    await post(
                      `/customers/${id}/${q.data?.archivedAt ? 'restore' : 'archive'}`,
                      {},
                    );
                    await qc.invalidateQueries({
                      queryKey: ['customer', `/customers/${id}`],
                    });
                    setArchiveError('');
                  } catch (error) {
                    setArchiveError((error as Error).message);
                  }
                }}
              >
                {q.data?.archivedAt ? 'Restore' : 'Archive'}
              </button>
            )}
            <Link
              className="btn primary"
              to={'/enquiries/new?customerId=' + id}
            >
              Create Enquiry
            </Link>
          </div>
        }
      />
      {archiveError && (
        <p role="alert" className="text-red-600">
          {archiveError}
        </p>
      )}
      <div className="card customer-profile-card">
        <div
          className="customer-profile-tabs"
          role="tablist"
          aria-label="Customer details"
        >
          {[
            'Overview',
            'My Users',
            'Addresses',
            'Enquiries',
            'Quotations',
            'Email',
            'Activity',
          ].map((x) => (
            <button
              key={x}
              onClick={() => setTab(x)}
              role="tab"
              aria-selected={tab === x}
              className={tab === x ? 'active' : ''}
            >
              {x}
            </button>
          ))}
        </div>
        <div className="customer-profile-body">
          {tab === 'Overview' ? (
            <div className="customer-profile-overview">
              <div>
                <h3>Company details</h3>
                <p>Type: {q.data?.customerType}</p>
                <p>Country: {q.data?.country}</p>
                <p>GSTIN: {q.data?.gstin || '—'}</p>
                <p>IEC: {q.data?.iec || '—'}</p>
              </div>
              <div>
                <h3>Primary contact</h3>
                <p>{q.data?.primaryContact?.name}</p>
                <p>{q.data?.primaryContact?.email}</p>
                <p>{q.data?.primaryContact?.phone}</p>
              </div>
            </div>
          ) : tab === 'My Users' ? (
            <CustomerUsersPanel id={id!} customer={q.data} />
          ) : tab === 'Email' ? (
            <CustomerMailPanel id={id!} />
          ) : tab === 'Activity' ? (
            <CustomerActivityPanel id={id!} />
          ) : tab === 'Addresses' ? (
            <CustomerAddressesPanel
              id={id!}
              addresses={q.data?.addresses || []}
              country={q.data?.country || ''}
            />
          ) : tab === 'Enquiries' ? (
            q.data?.recentEnquiries?.length ? (
              q.data.recentEnquiries.map((e: any) => (
                <Link
                  className="customer-profile-record"
                  key={e.id}
                  to={'/enquiries/' + e.id}
                >
                  {e.enquiryCode} · {e.origin} → {e.destination}
                </Link>
              ))
            ) : (
              <Empty text="No enquiries yet" />
            )
          ) : q.data?.recentQuotations?.length ? (
            q.data.recentQuotations.map((x: any) => (
              <Link
                className="customer-profile-record"
                key={x.id}
                to={'/quotations/' + x.id}
              >
                {x.quotationNumber}
              </Link>
            ))
          ) : (
            <Empty text="No quotations yet" />
          )}
        </div>
      </div>
      <AiActionCard
        title="Customer AI Summary"
        description="Generate a summary from this customer’s recent FreightFlow records."
        actions={[
          { label: 'Generate Summary', path: `/customers/${id}/summary` },
        ]}
      />
    </State>
  );
}
