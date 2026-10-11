import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import {
  Anchor,
  ArrowRight,
  CalendarDays,
  Check,
  ClipboardList,
  FileText,
  Mail,
  Package,
  Plus,
  Search,
  ShieldCheck,
  Ship,
  Sparkles,
  UserRound,
  X,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import { z } from 'zod';
import {
  Empty,
  Heading,
  Pager,
  SectionCard,
  State,
  StatusBadge,
  Table,
  titleCase,
} from '../../shared/components/PageElements';
import {
  ReferenceLocationPicker,
  type LocationOption,
} from '../../shared/components/ReferenceLocationPicker';
import {
  CONTAINER_TYPES,
  defaultScopeDescriptions,
  freightLabel,
  INCOTERMS,
  QUOTE_CURRENCIES,
  scopeOptions,
} from '../../shared/lib/quotationOptions';
import {
  api,
  date,
  post,
  put,
  qc,
  toast,
  useData,
} from '../../shared/lib/workspace';
import { AiActionCard } from '../ai/AiAssist';
type Extraction = Record<string, unknown> & {
  missingFields?: string[];
  warnings?: string[];
};
const additionalScopeServices = [
  'Fumigation',
  'Certificate of Origin',
  'Warehousing',
];
const enquirySchema = z
  .object({
    customerId: z.uuid('Select a customer'),
    mode: z.enum(['OCEAN', 'AIR', 'ROAD'], 'Select a mode'),
    origin: z.string().trim().min(1, 'Origin is required'),
    destination: z.string().trim().min(1, 'Destination is required'),
    commodity: z.string().trim().min(1, 'Commodity is required'),
    contactEmail: z.union([z.literal(''), z.email()]).optional(),
  })
  .passthrough();
const displayFormDate = (value: unknown) => {
  if (!value) return '';
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(parsed.getTime())
    ? String(value)
    : new Intl.DateTimeFormat('en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        timeZone: 'UTC',
      }).format(parsed);
};
export function Enquiries() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [mode, setMode] = useState('');
  const [from, setFrom] = useState('');
  const q = useData(
    'enquiries',
    `/enquiries?page=${page}&search=${encodeURIComponent(search)}&status=${status}&mode=${mode}&from=${from}`,
  );
  return (
    <>
      <Heading
        title="Enquiries"
        subtitle="Track new freight requests from your customers."
        action={
          <Link className="btn primary" to="/enquiries/new">
            <Plus size={17} /> Create Enquiry
          </Link>
        }
      />
      <SectionCard
        title="Freight enquiries"
        action={
          <span className="section-kicker">
            {q.data?.totalElements ?? 0} TOTAL
          </span>
        }
      >
        <div className="filter-bar">
          <label className="filter-search">
            <Search size={17} />
            <input
              aria-label="Search enquiries"
              placeholder="Search enquiry, customer or route"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </label>
          <select
            aria-label="Transport mode"
            className="field"
            value={mode}
            onChange={(e) => {
              setMode(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All modes</option>
            {['OCEAN', 'AIR', 'ROAD'].map((x) => (
              <option key={x} value={x}>
                {titleCase(x)}
              </option>
            ))}
          </select>
          <select
            aria-label="Enquiry status"
            className="field"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {['DRAFT', 'NEW', 'PRICING_PENDING', 'QUOTED', 'CANCELLED'].map(
              (x) => (
                <option key={x} value={x}>
                  {titleCase(x)}
                </option>
              ),
            )}
          </select>
          <input
            aria-label="Created from"
            title="Created from"
            type="date"
            className="field"
            value={from}
            onChange={(e) => {
              setFrom(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <State query={q}>
          {q.data?.content?.length ? (
            <>
              <div className="table-wrap">
                <Table className="table">
                  <thead>
                    <tr>
                      {[
                        'Enquiry ID',
                        'Customer',
                        'Mode',
                        'Route',
                        'Cargo',
                        'Status',
                        'Created',
                      ].map((x) => (
                        <th key={x}>{x}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {q.data.content.map((e: any) => (
                      <tr key={e.id}>
                        <td>
                          <Link
                            className="table-primary"
                            to={'/enquiries/' + e.id}
                          >
                            {e.enquiryCode}
                          </Link>
                        </td>
                        <td>{e.customerName}</td>
                        <td>
                          <span className="mode-cell">
                            <Ship size={15} />
                            {titleCase(e.mode)}
                          </span>
                        </td>
                        <td>
                          <span className="route-text">
                            {e.origin} <ArrowRight size={13} /> {e.destination}
                          </span>
                        </td>
                        <td>{e.commodity}</td>
                        <td>
                          <StatusBadge value={e.status} />
                        </td>
                        <td>{date(e.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </div>
              <Pager data={q.data} page={page} setPage={setPage} />
            </>
          ) : (
            <Empty
              text="No enquiries yet"
              description="Create your first freight enquiry to get started."
              action={
                <Link className="btn primary" to="/enquiries/new">
                  Create Enquiry
                </Link>
              }
            />
          )}
        </State>
      </SectionCard>
    </>
  );
}

function EnquiryField({
  form,
  name,
  label,
  type = 'text',
  required = false,
  className = '',
}: {
  form: any;
  name: string;
  label: string;
  type?: string;
  required?: boolean;
  className?: string;
}) {
  const error = form.formState.errors[name]?.message;
  const numeric = type === 'number';
  const integer = ['packageCount', 'containerQuantity'].includes(name);
  return (
    <label className={`block ${className}`.trim()}>
      <span className="label">
        {label}
        {required && ' *'}
      </span>
      <input
        className={'field' + (error ? ' enquiry-invalid' : '')}
        type={type}
        step={numeric ? (integer ? '1' : 'any') : undefined}
        aria-invalid={!!error}
        aria-describedby={error ? `enquiry-${name}-error` : undefined}
        {...form.register(name, {
          required: required ? `${label} is required` : false,
          validate: numeric
            ? (value: string) =>
                !value ||
                (Number(value) >= 0 &&
                  (!integer || Number.isInteger(Number(value)))) ||
                `${label} must be ${integer ? 'a non-negative whole number' : 'non-negative'}`
            : name === 'contactEmail'
              ? (value: string) =>
                  !value ||
                  /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) ||
                  'Enter a valid email address'
              : undefined,
        })}
      />
      {error && (
        <small
          id={`enquiry-${name}-error`}
          className="enquiry-field-error"
          role="alert"
        >
          {String(error)}
        </small>
      )}
    </label>
  );
}
function LocationField({
  form,
  name,
  codeName,
  label,
  mode,
  relatedName,
}: {
  form: any;
  name: string;
  codeName: string;
  label: string;
  mode: string;
  relatedName?: 'origin' | 'destination';
}) {
  return (
    <>
      <input type="hidden" {...form.register(name)} />
      <input type="hidden" {...form.register(codeName)} />
      <ReferenceLocationPicker
        id={`location-${name}`}
        label={label}
        mode={mode}
        value={String(form.watch(name) || '')}
        code={String(form.watch(codeName) || '')}
        onTextChange={(value) => {
          form.setValue(name, value, {
            shouldDirty: true,
            shouldValidate: true,
          });
          form.setValue(codeName, '', { shouldDirty: true });
        }}
        onSelect={(location) => {
          form.setValue(name, location.name, {
            shouldDirty: true,
            shouldValidate: true,
          });
          form.setValue(codeName, location.code, { shouldDirty: true });
          if (relatedName && !form.getValues(relatedName))
            form.setValue(relatedName, location.name, {
              shouldDirty: true,
              shouldValidate: true,
            });
        }}
      />
    </>
  );
}
export function EnquiryForm() {
  const nav = useNavigate();
  const { id } = useParams();
  const [params] = useSearchParams();
  const existing = useQuery({
    queryKey: ['enquiry', id],
    queryFn: () => api(`/enquiries/${id}`),
    enabled: !!id,
  });
  const customers = useData('customers', '/customers?size=100');
  const form = useForm<any>({
    resolver: zodResolver(enquirySchema),
    shouldUnregister: false,
    shouldFocusError: false,
    defaultValues: {
      customerId: params.get('customerId') || '',
      mode: '',
      cargoType: '',
      weightUnit: '',
      volumeUnit: '',
      sourceMessageId: params.get('sourceMessageId') || '',
      polCode: '',
      podCode: '',
      fpodCode: '',
      fpodName: '',
      quoteCurrency: 'USD',
      referenceDestinationCharges: false,
    },
  });
  const formRef = useRef<HTMLFormElement>(null);
  const [err, setErr] = useState('');
  const [extractError, setExtractError] = useState('');
  const [requirements, setRequirements] = useState('');
  const [extraction, setExtraction] = useState<Extraction | null>(null);
  const [extracting, setExtracting] = useState(false);
  const [requirementsOpen, setRequirementsOpen] = useState(
    !params.get('sourceMessageId'),
  );
  const [routeReviewFields, setRouteReviewFields] = useState<string[]>([]);
  const [scopeCustomized, setScopeCustomized] = useState(false);
  const scopeSeedKey = useRef('');
  const manuallyToggledServices = useRef(new Set<string>());
  const extractionVersion = useRef(0);
  const extractedSourceId = useRef<string | null>(null);
  const sourceMessageId = params.get('sourceMessageId');
  const source = useQuery({
    queryKey: ['enquiry-source-mail', sourceMessageId],
    queryFn: () => api(`/mail/messages/${sourceMessageId}`),
    enabled: !!sourceMessageId && !id,
  });
  useEffect(() => {
    if (id && existing.data) {
      const saved = existing.data.enquiry;
      const key = `${saved.incoterm || ''}|${saved.mode || ''}|${saved.fpodCode || ''}`;
      scopeSeedKey.current = key;
      setScopeCustomized(
        Array.isArray(saved.serviceScope) &&
          JSON.stringify(saved.serviceScope) !==
            JSON.stringify(
              defaultScopeDescriptions(
                saved.incoterm || '',
                saved.mode,
                !!saved.fpodCode,
              ),
            ),
      );
      form.reset({
        ...saved,
        serviceScope:
          saved.serviceScope ??
          defaultScopeDescriptions(
            saved.incoterm || '',
            saved.mode,
            !!saved.fpodCode,
          ),
        quoteCurrency: saved.quoteCurrency || 'USD',
        fpodName: saved.fpodCode || '',
      });
    }
  }, [id, existing.data]);
  const mode = form.watch('mode');
  const cargoType = form.watch('cargoType');
  const selectedCustomer = form.watch('customerId');
  const liveValues = form.watch();
  const selectedScope: string[] = Array.isArray(liveValues.serviceScope)
    ? liveValues.serviceScope
    : [];
  const availableScope = scopeOptions(mode, !!liveValues.fpodCode).map(
    ({ description }) => description,
  );
  const scopeKey = `${liveValues.incoterm || ''}|${mode || ''}|${liveValues.fpodCode || ''}`;
  useEffect(() => {
    if (!liveValues.incoterm || !mode || scopeSeedKey.current === scopeKey)
      return;
    scopeSeedKey.current = scopeKey;
    if (
      mode === 'ROAD' ||
      !['CIF', 'CFR', 'CPT', 'CIP'].includes(liveValues.incoterm)
    )
      form.setValue('referenceDestinationCharges', false);
    if (scopeCustomized) {
      const valid = new Set([
        ...scopeOptions(mode, !!liveValues.fpodCode).map(
          ({ description }) => description,
        ),
        ...additionalScopeServices,
      ]);
      const next = selectedScope.filter(
        (description) =>
          valid.has(description) && !/freight/i.test(description),
      );
      next.push(freightLabel(mode));
      form.setValue('serviceScope', next, { shouldDirty: true });
    } else {
      form.setValue(
        'serviceScope',
        defaultScopeDescriptions(
          liveValues.incoterm,
          mode,
          !!liveValues.fpodCode,
        ),
      );
    }
  }, [scopeKey, scopeCustomized]);
  const resetServiceScope = () => {
    manuallyToggledServices.current.clear();
    form.setValue(
      'serviceScope',
      defaultScopeDescriptions(
        liveValues.incoterm || '',
        mode,
        !!liveValues.fpodCode,
      ),
      { shouldDirty: true },
    );
    setScopeCustomized(false);
  };
  const toggleServiceScope = (description: string) => {
    if (description === freightLabel(mode)) return;
    if (
      description === 'Destination charges' &&
      ['CIF', 'CFR', 'CPT', 'CIP'].includes(liveValues.incoterm)
    ) {
      form.setValue(
        'referenceDestinationCharges',
        !form.getValues('referenceDestinationCharges'),
        { shouldDirty: true },
      );
      return;
    }
    manuallyToggledServices.current.add(description);
    if (description === 'Destination charges')
      form.setValue('referenceDestinationCharges', false, {
        shouldDirty: true,
      });
    form.setValue(
      'serviceScope',
      selectedScope.includes(description)
        ? selectedScope.filter((item) => item !== description)
        : [...selectedScope, description],
      { shouldDirty: true },
    );
    setScopeCustomized(true);
  };
  const selectedCustomerName = customers.data?.content?.find(
    (customer: any) => customer.id === selectedCustomer,
  )?.companyName;
  const customerDetail = useQuery({
    queryKey: ['customer-contacts', selectedCustomer],
    queryFn: () => api(`/customers/${selectedCustomer}`),
    enabled: !!selectedCustomer,
  });
  const selectedContact = customerDetail.data?.contacts?.find(
    (contact: any) => contact.id === liveValues.contactId,
  );
  useEffect(() => {
    if (!selectedCustomer || !customerDetail.data?.contacts?.length) return;
    if (form.getValues('contactId') || form.getFieldState('contactId').isDirty)
      return;
    const primary =
      customerDetail.data.contacts.find((contact: any) => contact.primary) ||
      customerDetail.data.contacts[0];
    form.setValue('contactId', primary.id);
    if (
      !form.getFieldState('contactEmail').isDirty &&
      !form.getValues('contactEmail')
    )
      form.setValue('contactEmail', primary.email || '', {
        shouldValidate: true,
      });
  }, [selectedCustomer, customerDetail.data]);
  const focusFirstInvalid = () =>
    requestAnimationFrame(() => {
      const first = formRef.current?.querySelector<HTMLElement>(
        '[aria-invalid="true"]',
      );
      first?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      first?.focus({ preventScroll: true });
    });
  const persist = (status: 'NEW' | 'DRAFT') =>
    form.handleSubmit(
      async (v) => {
        setErr('');
        if (routeReviewFields.length) {
          setErr(
            'Review the AI-suggested route and confirm the locations before creating the enquiry.',
          );
          return;
        }
        if (['OCEAN', 'AIR'].includes(v.mode) && (!v.polCode || !v.podCode)) {
          setErr(
            'Select a canonical loading and discharge location from the suggestions.',
          );
          return;
        }
        try {
          const data = {
            ...v,
            serviceScope: Array.isArray(v.serviceScope) ? v.serviceScope : null,
            pickupRequired:
              v.serviceScope?.includes('Pickup & trucking') || false,
            exportCustomsRequired:
              v.serviceScope?.includes('Export customs clearance') || false,
            insuranceRequired:
              v.serviceScope?.includes('Cargo insurance') || false,
            destinationDeliveryRequired:
              v.serviceScope?.includes('Door delivery') || false,
            status:
              status === 'NEW' && id && existing.data?.status !== 'DRAFT'
                ? existing.data?.status || 'NEW'
                : status,
            contactId: v.contactId || null,
            grossWeight: v.grossWeight ? Number(v.grossWeight) : null,
            volume: v.volume ? Number(v.volume) : null,
            packageCount: v.packageCount ? Number(v.packageCount) : null,
            containerQuantity: v.containerQuantity
              ? Number(v.containerQuantity)
              : null,
            readyDate: v.readyDate || null,
            sourceMessageId: v.sourceMessageId || null,
            fpodName: undefined,
          };
          const r = id
            ? await put('/enquiries/' + id, data)
            : await post('/enquiries', data);
          qc.invalidateQueries();
          toast(status === 'DRAFT' ? 'Draft saved' : 'Enquiry saved');
          nav(
            status === 'DRAFT'
              ? `/enquiries/${r.id}/edit`
              : `/enquiries/${r.id}`,
          );
        } catch (e: any) {
          setErr(e.message);
        }
      },
      () => {
        setErr('Please correct the highlighted fields');
        setTimeout(focusFirstInvalid, 0);
      },
    );
  const submit = persist('NEW');
  const saveDraft = persist('DRAFT');
  const applyExtraction = (extracted: Extraction) => {
    const fieldMap: Record<string, string> = { shipmentType: 'cargoType' };
    const suggestedRoute: string[] = [];
    const chosenMode =
      extracted.mode && !form.getFieldState('mode').isDirty
        ? extracted.mode
        : form.getValues('mode');
    const allowed = [
      'mode',
      'origin',
      'destination',
      'portOfLoading',
      'portOfDischarge',
      'airportOfOrigin',
      'airportOfDestination',
      'shipmentType',
      'contactEmail',
      'commodity',
      'grossWeight',
      'weightUnit',
      'volume',
      'volumeUnit',
      'packageCount',
      'containerType',
      'containerQuantity',
      'readyDate',
      'incoterm',
      'pickupRequired',
      'exportCustomsRequired',
      'insuranceRequired',
      'destinationDeliveryRequired',
      'notes',
    ];
    for (const [source, value] of Object.entries(extracted)) {
      const key = fieldMap[source] || source;
      if (
        !allowed.includes(source) ||
        value === null ||
        value === undefined ||
        value === ''
      )
        continue;
      if (
        ([
          'portOfLoading',
          'portOfDischarge',
          'containerType',
          'containerQuantity',
        ].includes(source) &&
          chosenMode !== 'OCEAN') ||
        (['airportOfOrigin', 'airportOfDestination'].includes(source) &&
          chosenMode !== 'AIR') ||
        (['containerType', 'containerQuantity'].includes(source) &&
          extracted.shipmentType === 'LCL')
      )
        continue;
      const current = form.getValues(key);
      if (
        (form.getFieldState(key).isDirty ||
          (id &&
            current !== null &&
            current !== undefined &&
            current !== '')) &&
        String(current) !== String(value)
      )
        continue;
      form.setValue(key, value, { shouldDirty: true, shouldValidate: true });
      if (
        [
          'origin',
          'destination',
          'portOfLoading',
          'portOfDischarge',
          'airportOfOrigin',
          'airportOfDestination',
        ].includes(key)
      )
        suggestedRoute.push(key);
    }
    const values = form.getValues();
    const defaults = defaultScopeDescriptions(
      values.incoterm || '',
      values.mode,
      !!values.fpodCode,
    );
    const suggested = scopeCustomized
      ? [...(values.serviceScope || []), freightLabel(values.mode)]
      : [...defaults];
    const requested: [string, string][] = [
      ['pickupRequired', 'Pickup & trucking'],
      ['exportCustomsRequired', 'Export customs clearance'],
      ['insuranceRequired', 'Cargo insurance'],
      ['destinationDeliveryRequired', 'Door delivery'],
    ];
    requested.forEach(([field, description]) => {
      if (
        values[field] === true &&
        !manuallyToggledServices.current.has(description) &&
        !suggested.includes(description)
      )
        suggested.push(description);
    });
    form.setValue('serviceScope', [...new Set(suggested)]);
    setScopeCustomized(
      scopeCustomized || JSON.stringify(suggested) !== JSON.stringify(defaults),
    );
    scopeSeedKey.current = `${values.incoterm || ''}|${values.mode || ''}|${values.fpodCode || ''}`;
    setRouteReviewFields(suggestedRoute);
    if (chosenMode !== 'OCEAN')
      form.unregister([
        'portOfLoading',
        'portOfDischarge',
        'containerType',
        'containerQuantity',
      ]);
    if (chosenMode === 'AIR') form.unregister('cargoType');
    if (chosenMode !== 'AIR')
      form.unregister(['airportOfOrigin', 'airportOfDestination']);
    if (extracted.customerName) {
      const match = customers.data?.content?.find(
        (c: any) =>
          c.companyName?.toLowerCase() ===
          String(extracted.customerName).toLowerCase(),
      );
      if (
        match &&
        !form.getValues('customerId') &&
        !form.getFieldState('customerId').isDirty
      )
        form.setValue('customerId', match.id, {
          shouldDirty: true,
          shouldValidate: true,
        });
    }
  };
  const extract = async (text = requirements) => {
    const version = ++extractionVersion.current;
    setExtracting(true);
    setExtractError('');
    try {
      const response = await post('/ai/shipment-extract', {
        text,
      });
      if (version !== extractionVersion.current) return;
      const extracted: Extraction = {
        ...response.extracted,
        missingFields: response.missingFields,
        warnings: response.warnings,
      };
      const manuallyEdited = new Set(
        [
          'portOfLoading',
          'portOfDischarge',
          'airportOfOrigin',
          'airportOfDestination',
        ].filter((field) => form.getFieldState(field).isDirty),
      );
      applyExtraction(extracted);
      const resolvedMode = String(form.getValues('mode') || '');
      if (['OCEAN', 'AIR'].includes(resolvedMode)) {
        const names =
          resolvedMode === 'OCEAN'
            ? (['portOfLoading', 'portOfDischarge'] as const)
            : (['airportOfOrigin', 'airportOfDestination'] as const);
        for (const [index, field] of names.entries()) {
          if (manuallyEdited.has(field)) continue;
          const name = String(form.getValues(field) || '');
          if (!name) continue;
          try {
            const matches: LocationOption[] = await api(
              `/reference/locations?q=${encodeURIComponent(name)}&mode=${resolvedMode}`,
            );
            if (version !== extractionVersion.current) return;
            const exact = matches.filter((location) =>
              location.name.toLowerCase().startsWith(name.toLowerCase()),
            );
            if (exact.length === 1)
              form.setValue(
                index === 0 ? 'polCode' : 'podCode',
                exact[0].code,
                {
                  shouldDirty: true,
                },
              );
          } catch {
            // The employee can resolve this location from the searchable selector.
          }
        }
      }
      setExtraction(extracted);
      setRequirementsOpen(false);
    } catch (error) {
      if (version === extractionVersion.current)
        setExtractError((error as Error).message);
    } finally {
      if (version === extractionVersion.current) setExtracting(false);
    }
  };
  useEffect(() => {
    if (
      !sourceMessageId ||
      !source.data ||
      extractedSourceId.current === sourceMessageId
    )
      return;
    extractedSourceId.current = sourceMessageId;
    const text = [
      `Subject: ${source.data.subject || ''}`,
      `Sender: ${source.data.fromEmail || ''}`,
      source.data.bodyText || '',
    ]
      .join('\n')
      .slice(0, 10000);
    void extract(text);
  }, [sourceMessageId, source.data]);
  const customerError = form.formState.errors.customerId?.message;
  const modeError = form.formState.errors.mode?.message;
  const hasExtractedShipment =
    extraction &&
    ['mode', 'origin', 'destination', 'commodity', 'portOfLoading'].some(
      (key) => extraction[key] != null && extraction[key] !== '',
    );
  return (
    <>
      <div className="enquiry-workspace-header">
        <div>
          <div className="enquiry-breadcrumb">
            <Link to="/enquiries">Sales Tracker</Link>
            <span aria-hidden="true">›</span>
            <strong>{id ? 'Edit Enquiry' : 'Create Enquiry'}</strong>
          </div>
          <h1>{id ? 'Edit Enquiry' : 'Create Enquiry'}</h1>
          <p>Record the shipment details your customer needs priced.</p>
        </div>
        <div className="enquiry-workspace-header-actions">
          <button
            className="btn secondary"
            type="button"
            onClick={() => nav('/mail')}
          >
            <Mail size={15} /> Import from Mail
          </button>
          <button
            className="btn secondary"
            type="button"
            onClick={() => {
              extractionVersion.current += 1;
              form.reset({
                customerId: '',
                mode: '',
                cargoType: '',
                weightUnit: '',
                volumeUnit: '',
                sourceMessageId: sourceMessageId || '',
                polCode: '',
                podCode: '',
                fpodCode: '',
                fpodName: '',
              });
              setRequirements('');
              setExtraction(null);
              setExtractError('');
              setRouteReviewFields([]);
              setErr('');
              setRequirementsOpen(true);
            }}
          >
            <X size={15} /> Clear
          </button>
        </div>
      </div>
      <div className="enquiry-workflow">
        <form
          ref={formRef}
          onSubmit={submit}
          noValidate
          className="card enquiry-create-form"
        >
          <div className="enquiry-ai-panel">
            <div className="enquiry-ai-input-column">
              <div className="enquiry-ai-heading">
                <span className="enquiry-ai-icon" aria-hidden="true">
                  <Sparkles size={19} />
                </span>
                <div>
                  <strong>AI shipment assistant</strong>
                  <p>
                    {extracting
                      ? 'Reading the shipment request…'
                      : hasExtractedShipment
                        ? 'Shipment details were added to the editable form below.'
                        : extraction
                          ? 'No shipment fields were identified. You can complete the form manually.'
                          : sourceMessageId
                            ? 'We’ll read the linked email and fill the fields we can identify.'
                            : 'Paste a shipment request to fill the form faster.'}
                  </p>
                </div>
              </div>
              {sourceMessageId ? (
                <div className="enquiry-ai-content">
                  <details
                    open={requirementsOpen}
                    onToggle={(event) =>
                      setRequirementsOpen(event.currentTarget.open)
                    }
                  >
                    <summary>View linked email</summary>
                    {source.isPending && <p>Loading source email…</p>}
                    {source.error && (
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
                  {source.data && (
                    <button
                      className="btn secondary"
                      type="button"
                      disabled={extracting}
                      onClick={() =>
                        void extract(
                          [
                            `Subject: ${source.data.subject || ''}`,
                            `Sender: ${source.data.fromEmail || ''}`,
                            source.data.bodyText || '',
                          ]
                            .join('\n')
                            .slice(0, 10000),
                        )
                      }
                    >
                      <Sparkles size={16} /> Extract from email
                    </button>
                  )}
                </div>
              ) : (
                <details
                  className="enquiry-ai-content intake-manual-extract"
                  open={requirementsOpen}
                  onToggle={(event) =>
                    setRequirementsOpen(event.currentTarget.open)
                  }
                >
                  <summary>Paste requirements and extract with AI</summary>
                  <div className="enquiry-ai-paste-row">
                    <textarea
                      className="field"
                      aria-label="Customer requirements"
                      rows={2}
                      maxLength={10000}
                      value={requirements}
                      onChange={(event) => {
                        extractionVersion.current += 1;
                        setExtracting(false);
                        setRequirements(event.target.value);
                      }}
                      placeholder="Paste the customer's shipment request"
                    />
                    <button
                      className="btn primary"
                      type="button"
                      onClick={() => void extract()}
                      disabled={!requirements.trim() || extracting}
                    >
                      <Sparkles size={16} />{' '}
                      {extracting ? 'Extracting…' : 'Extract with AI'}
                    </button>
                  </div>
                </details>
              )}
            </div>
            <div className="enquiry-ai-review-column">
              <div className="enquiry-ai-review-heading">
                <strong>
                  <ClipboardList size={16} /> Extracted details
                </strong>
                <span className="enquiry-ai-badge">
                  {extracting
                    ? 'Reading'
                    : hasExtractedShipment
                      ? 'Ready to review'
                      : 'Optional'}
                </span>
                <button
                  type="button"
                  className="enquiry-ai-review-link"
                  onClick={() =>
                    formRef.current
                      ?.querySelector('.enquiry-customer-card')
                      ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
                  }
                >
                  Edit fields
                </button>
              </div>
              <div
                className="enquiry-ai-review"
                aria-label="Extracted shipment review"
              >
                {[
                  [
                    'Mode',
                    liveValues.mode
                      ? `${liveValues.cargoType ? `${liveValues.cargoType} ` : ''}(${titleCase(liveValues.mode)})`
                      : '',
                    Ship,
                  ],
                  [
                    'POL',
                    `${liveValues.portOfLoading || liveValues.airportOfOrigin || liveValues.origin || ''}${liveValues.polCode ? ` (${liveValues.polCode})` : ''}`,
                    Anchor,
                  ],
                  [
                    'POD',
                    `${liveValues.portOfDischarge || liveValues.airportOfDestination || liveValues.destination || ''}${liveValues.podCode ? ` (${liveValues.podCode})` : ''}`,
                    Anchor,
                  ],
                  [
                    'Equipment',
                    liveValues.containerType
                      ? `${liveValues.containerQuantity || '—'} × ${liveValues.containerType}`
                      : liveValues.cargoType,
                    Package,
                  ],
                  ['Cargo', liveValues.commodity, Package],
                  [
                    'Cargo ready',
                    displayFormDate(liveValues.readyDate),
                    CalendarDays,
                  ],
                  ['Incoterm', liveValues.incoterm, FileText],
                ].map(([label, value, Icon]) => (
                  <div
                    key={label}
                    className={
                      extraction &&
                      (!value ||
                        (['OCEAN', 'AIR'].includes(liveValues.mode) &&
                          ((label === 'POL' && !liveValues.polCode) ||
                            (label === 'POD' && !liveValues.podCode))))
                        ? 'needs-review'
                        : undefined
                    }
                  >
                    <Icon size={16} aria-hidden="true" />
                    <span>
                      <small>{label}</small>
                      <strong>{value || 'Needs review'}</strong>
                    </span>
                  </div>
                ))}
              </div>
            </div>
            {!!(
              extraction?.missingFields?.length || extraction?.warnings?.length
            ) && (
              <div className="enquiry-ai-warnings" role="status">
                {!!extraction?.missingFields?.length && (
                  <p>Check manually: {extraction.missingFields.join(', ')}.</p>
                )}
                {extraction.warnings?.map((warning, index) => (
                  <p key={index}>{warning}</p>
                ))}
              </div>
            )}
            {extractError && (
              <p className="enquiry-ai-error" role="alert">
                {extractError}
              </p>
            )}
          </div>
          <div className="enquiry-form-main">
            <section className="freight-form-section enquiry-customer-card">
              <h2>
                <UserRound size={17} /> Customer &amp; Contact
              </h2>
              <div className="freight-field-grid freight-field-grid-customer">
                <label className="block">
                  <span className="label">Customer *</span>
                  <select
                    className={
                      'field' + (customerError ? ' enquiry-invalid' : '')
                    }
                    aria-invalid={!!customerError}
                    aria-describedby={
                      customerError ? 'enquiry-customerId-error' : undefined
                    }
                    {...form.register('customerId', {
                      required: 'Select a customer',
                      onChange: () => {
                        form.setValue('contactId', '');
                        if (!form.getFieldState('contactEmail').isDirty)
                          form.setValue('contactEmail', '');
                      },
                    })}
                  >
                    <option value="">Select customer</option>
                    {customers.data?.content?.map((c: any) => (
                      <option value={c.id} key={c.id}>
                        {c.companyName}
                      </option>
                    ))}
                  </select>
                  {customerError && (
                    <small
                      id="enquiry-customerId-error"
                      className="enquiry-field-error"
                      role="alert"
                    >
                      {String(customerError)}
                    </small>
                  )}
                </label>
                <label className="block">
                  <span className="label">Contact person</span>
                  <select
                    className="field"
                    disabled={!selectedCustomer}
                    {...form.register('contactId', {
                      onChange: (
                        event: React.ChangeEvent<HTMLSelectElement>,
                      ) => {
                        const contact = customerDetail.data?.contacts?.find(
                          (item: any) => item.id === event.target.value,
                        );
                        if (
                          contact &&
                          !form.getFieldState('contactEmail').isDirty
                        )
                          form.setValue('contactEmail', contact.email || '', {
                            shouldValidate: true,
                          });
                      },
                    })}
                  >
                    <option value="">Select contact</option>
                    {customerDetail.data?.contacts?.map((c: any) => (
                      <option value={c.id} key={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </label>
                <EnquiryField
                  form={form}
                  name="contactEmail"
                  label="Email"
                  type="email"
                />
                <label className="block">
                  <span className="label">Phone</span>
                  <input
                    className="field"
                    value={selectedContact?.phone || ''}
                    placeholder="From customer contact"
                    readOnly
                  />
                </label>
              </div>
            </section>
            <section className="freight-form-section enquiry-route-card">
              <h2>
                <Ship size={17} /> Route &amp; Schedule
              </h2>
              <div className="freight-field-grid freight-field-grid-uniform">
                <label>
                  <span className="label">Mode *</span>
                  <select
                    className={'field' + (modeError ? ' enquiry-invalid' : '')}
                    aria-invalid={!!modeError}
                    aria-describedby={
                      modeError ? 'enquiry-mode-error' : undefined
                    }
                    {...form.register('mode', {
                      required: 'Select a mode',
                      onChange: (event: any) => {
                        const nextMode = event.target.value;
                        form.setValue('cargoType', '');
                        form.setValue('polCode', '');
                        form.setValue('podCode', '');
                        if (nextMode !== 'OCEAN')
                          form.unregister([
                            'portOfLoading',
                            'portOfDischarge',
                            'containerType',
                            'containerQuantity',
                          ]);
                        if (nextMode === 'AIR') form.unregister('cargoType');
                        if (nextMode !== 'AIR')
                          form.unregister([
                            'airportOfOrigin',
                            'airportOfDestination',
                          ]);
                      },
                    })}
                  >
                    <option value="">Select mode</option>
                    {['OCEAN', 'AIR', 'ROAD'].map((x) => (
                      <option key={x}>{x}</option>
                    ))}
                  </select>
                  {modeError && (
                    <small
                      id="enquiry-mode-error"
                      className="enquiry-field-error"
                      role="alert"
                    >
                      {String(modeError)}
                    </small>
                  )}
                </label>
                {mode === 'OCEAN' ? (
                  <>
                    <LocationField
                      form={form}
                      name="portOfLoading"
                      codeName="polCode"
                      label="Port of loading (POL)"
                      mode="OCEAN"
                      relatedName="origin"
                    />
                    <LocationField
                      form={form}
                      name="portOfDischarge"
                      codeName="podCode"
                      label="Port of discharge (POD)"
                      mode="OCEAN"
                      relatedName="destination"
                    />
                    <LocationField
                      form={form}
                      name="fpodName"
                      codeName="fpodCode"
                      label="Final place (FPOD)"
                      mode="ALL"
                    />
                  </>
                ) : mode === 'AIR' ? (
                  <>
                    <LocationField
                      form={form}
                      name="airportOfOrigin"
                      codeName="polCode"
                      label="Airport of Origin"
                      mode="AIR"
                      relatedName="origin"
                    />
                    <LocationField
                      form={form}
                      name="airportOfDestination"
                      codeName="podCode"
                      label="Airport of Destination"
                      mode="AIR"
                      relatedName="destination"
                    />
                  </>
                ) : null}
                <EnquiryField
                  form={form}
                  name="origin"
                  label="Origin"
                  required
                  className="freight-span-2"
                />
                <EnquiryField
                  form={form}
                  name="destination"
                  label="Destination"
                  required
                  className="freight-span-2"
                />
                <EnquiryField
                  form={form}
                  name="readyDate"
                  label="Cargo-ready date"
                  type="date"
                />
                <label>
                  <span className="label">Incoterm</span>
                  <select className="field" {...form.register('incoterm')}>
                    <option value="">Select Incoterm</option>
                    {INCOTERMS.map((term) => (
                      <option key={term} value={term}>
                        {term}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              {!!routeReviewFields.length && (
                <div className="freight-route-review" role="status">
                  <span>
                    AI suggested route text. Confirm the locations against the
                    customer request.
                  </span>
                  <button
                    type="button"
                    className="btn secondary"
                    onClick={() => {
                      setRouteReviewFields([]);
                      setErr('');
                    }}
                  >
                    Confirm route
                  </button>
                </div>
              )}
            </section>
            <section className="freight-form-section enquiry-cargo-card">
              <h2>
                <Package size={17} /> Cargo Details
              </h2>
              <div className="freight-field-grid freight-field-grid-uniform">
                {mode === 'OCEAN' && (
                  <label>
                    <span className="label">Shipment type</span>
                    <select
                      className="field"
                      {...form.register('cargoType', {
                        onChange: (
                          event: React.ChangeEvent<HTMLSelectElement>,
                        ) => {
                          if (event.target.value !== 'FCL')
                            form.unregister([
                              'containerType',
                              'containerQuantity',
                            ]);
                        },
                      })}
                    >
                      <option value="">Not specified</option>
                      <option>FCL</option>
                      <option>LCL</option>
                    </select>
                  </label>
                )}
                {mode === 'ROAD' && (
                  <label>
                    <span className="label">Truck load</span>
                    <select className="field" {...form.register('cargoType')}>
                      <option value="">Not specified</option>
                      <option value="FTL">FTL</option>
                      <option value="LTL">LTL</option>
                    </select>
                  </label>
                )}
                <EnquiryField
                  form={form}
                  name="commodity"
                  label="Commodity"
                  required
                  className="freight-span-2"
                />
                <EnquiryField
                  form={form}
                  name="grossWeight"
                  label="Gross Weight"
                  type="number"
                />
                <label>
                  <span className="label">Weight unit</span>
                  <select className="field" {...form.register('weightUnit')}>
                    <option value="">Not specified</option>
                    <option>KG</option>
                    <option>LB</option>
                    <option>TON</option>
                  </select>
                </label>
                <EnquiryField
                  form={form}
                  name="volume"
                  label="Volume"
                  type="number"
                />
                <label>
                  <span className="label">Volume unit</span>
                  <select className="field" {...form.register('volumeUnit')}>
                    <option value="">Not specified</option>
                    <option>CBM</option>
                    <option>CFT</option>
                  </select>
                </label>
                <EnquiryField
                  form={form}
                  name="packageCount"
                  label="Packages"
                  type="number"
                />
                {mode === 'OCEAN' && cargoType === 'FCL' && (
                  <>
                    <label>
                      <span className="label">Container Type</span>
                      <select
                        className="field"
                        {...form.register('containerType')}
                      >
                        <option value="">Select container</option>
                        {CONTAINER_TYPES.map((container) => (
                          <option key={container} value={container}>
                            {container}
                          </option>
                        ))}
                      </select>
                    </label>
                    <EnquiryField
                      form={form}
                      name="containerQuantity"
                      label="Container Quantity"
                      type="number"
                    />
                  </>
                )}
              </div>
            </section>
            <section className="freight-form-section enquiry-services-card freight-scope-card">
              <h2>Scope &amp; currency</h2>
              <div className="freight-scope-heading">
                <strong>What this quotation covers</strong>
                {liveValues.incoterm && (
                  <span className="freight-incoterm">
                    {liveValues.incoterm}
                  </span>
                )}
                <span>
                  {liveValues.incoterm
                    ? `Set by the Incoterm — your customer is the ${['EXW', 'FCA', 'FOB'].includes(liveValues.incoterm) ? 'buyer' : 'seller'}. Click to add or remove.`
                    : 'Select an Incoterm above to suggest a scope. Click to add or remove.'}
                </span>
                {scopeCustomized && (
                  <button
                    type="button"
                    className="freight-scope-reset"
                    onClick={resetServiceScope}
                  >
                    Reset to Incoterm
                  </button>
                )}
              </div>
              <div className="freight-scope-chips" aria-label="Quotation scope">
                {availableScope.map((description) => {
                  const selected = selectedScope.includes(description);
                  return (
                    <button
                      key={description}
                      type="button"
                      className={`freight-scope-chip${selected ? ' selected' : ''}`}
                      aria-pressed={selected}
                      disabled={description === freightLabel(mode) && selected}
                      onClick={() => toggleServiceScope(description)}
                      title={
                        description === 'Destination charges' &&
                        ['CIF', 'CFR', 'CPT', 'CIP'].includes(
                          liveValues.incoterm,
                        )
                          ? 'For this Incoterm, destination charges are for reference only'
                          : undefined
                      }
                    >
                      <span aria-hidden="true">{selected ? '✓' : '+'}</span>
                      {description}
                    </button>
                  );
                })}
              </div>
              <div className="freight-scope-heading freight-scope-subheading">
                <strong>Additional services</strong>
                <span>Optional</span>
              </div>
              <div
                className="freight-scope-chips"
                aria-label="Additional services"
              >
                {additionalScopeServices.map((description) => {
                  const selected = selectedScope.includes(description);
                  return (
                    <button
                      key={description}
                      type="button"
                      className={`freight-scope-chip${selected ? ' selected' : ''}`}
                      aria-pressed={selected}
                      onClick={() => toggleServiceScope(description)}
                    >
                      <span aria-hidden="true">{selected ? '✓' : '+'}</span>
                      {description}
                    </button>
                  );
                })}
              </div>
              {mode !== 'ROAD' &&
                ['CIF', 'CFR', 'CPT', 'CIP'].includes(liveValues.incoterm) && (
                  <label className="freight-reference-destination">
                    <input
                      type="checkbox"
                      {...form.register('referenceDestinationCharges', {
                        onChange: (event: any) => {
                          if (event.target.checked)
                            form.setValue(
                              'serviceScope',
                              selectedScope.filter(
                                (service) => service !== 'Destination charges',
                              ),
                              { shouldDirty: true },
                            );
                        },
                      })}
                    />
                    <span>
                      Show destination charges <strong>for reference</strong> in
                      local currency (not in the total — payable by the
                      consignee)
                    </span>
                  </label>
                )}
              <div className="freight-currency-row">
                <label className="freight-currency-field">
                  <span className="label">Quote currency</span>
                  <select className="field" {...form.register('quoteCurrency')}>
                    {QUOTE_CURRENCIES.map((currency) => (
                      <option key={currency} value={currency}>
                        {currency}
                      </option>
                    ))}
                  </select>
                </label>
                <p className="freight-currency-note">
                  When you create a quotation, included charges are converted
                  into {liveValues.quoteCurrency || 'USD'} using the available
                  reference or authorized manual rate. The saved rate appears on
                  the PDF.
                </p>
              </div>
            </section>
            <section className="freight-form-section enquiry-additional-card">
              <h2>
                <FileText size={17} /> Additional Information
              </h2>
              <label className="block">
                <span className="label">Special instructions / notes</span>
                <textarea
                  className="field"
                  rows={2}
                  {...form.register('notes')}
                />
              </label>
            </section>
          </div>
          <aside className="enquiry-summary" aria-label="Enquiry Summary">
            <div className="enquiry-summary-heading">
              <span className="enquiry-summary-icon">
                <ClipboardList size={16} />
              </span>
              <h2>Enquiry Summary</h2>
            </div>
            <dl>
              <div>
                <dt>Customer</dt>
                <dd>{selectedCustomerName || 'Select a customer'}</dd>
              </div>
              <div>
                <dt>Mode</dt>
                <dd>
                  {liveValues.mode
                    ? `${liveValues.cargoType ? `${liveValues.cargoType} ` : ''}(${titleCase(liveValues.mode)})`
                    : 'Select a mode'}
                </dd>
              </div>
              <div>
                <dt>Route</dt>
                <dd>
                  {`${liveValues.portOfLoading || liveValues.airportOfOrigin || liveValues.origin || 'Origin'}${liveValues.polCode ? ` (${liveValues.polCode})` : ''} → ${liveValues.portOfDischarge || liveValues.airportOfDestination || liveValues.destination || 'Destination'}${liveValues.podCode ? ` (${liveValues.podCode})` : ''}`}
                </dd>
              </div>
              <div>
                <dt>Equipment</dt>
                <dd>
                  {liveValues.containerType
                    ? `${liveValues.containerQuantity || '—'} × ${liveValues.containerType}`
                    : 'Not specified'}
                </dd>
              </div>
              <div>
                <dt>Cargo</dt>
                <dd>
                  {liveValues.commodity || 'Add commodity'}
                  {liveValues.grossWeight
                    ? ` (${liveValues.grossWeight} ${liveValues.weightUnit || 'kg'})`
                    : ''}
                </dd>
              </div>
              <div>
                <dt>Cargo ready</dt>
                <dd>
                  {displayFormDate(liveValues.readyDate) || 'Not specified'}
                </dd>
              </div>
              <div>
                <dt>Incoterm</dt>
                <dd>{liveValues.incoterm || 'Not specified'}</dd>
              </div>
            </dl>
            {hasExtractedShipment && (
              <p className="enquiry-summary-source">
                <Sparkles size={16} />{' '}
                <span>
                  <strong>Fields prefilled by AI</strong>
                  <br />
                  Review and edit before saving.
                </span>
              </p>
            )}
            {sourceMessageId && (
              <p className="enquiry-summary-mail">
                <Mail size={14} /> Linked to source email
              </p>
            )}
          </aside>
          {err && (
            <p className="text-red-600" role="alert">
              {err}
            </p>
          )}
          <div className="workflow-actions">
            <div>
              {(!id || existing.data?.status === 'DRAFT') && (
                <button
                  className="btn secondary"
                  type="button"
                  disabled={form.formState.isSubmitting}
                  onClick={() => void saveDraft()}
                >
                  <ClipboardList size={15} /> Save Draft
                </button>
              )}
              <button
                className="btn primary"
                type="submit"
                disabled={form.formState.isSubmitting}
              >
                {!form.formState.isSubmitting && <Check size={15} />}
                {form.formState.isSubmitting
                  ? 'Saving...'
                  : id
                    ? 'Save Enquiry'
                    : 'Create Enquiry'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </>
  );
}
export function EnquiryDetail() {
  const { id } = useParams();
  const q = useData('enquiry', `/enquiries/${id}`);
  const [tab, setTab] = useState('Overview');
  const e = q.data?.enquiry;
  const missing = [
    ...(!e?.origin ? ['Origin'] : []),
    ...(!e?.destination ? ['Destination'] : []),
    ...(!e?.commodity ? ['Commodity'] : []),
    ...(e?.mode === 'OCEAN' && e?.cargoType === 'FCL' && !e?.containerType
      ? ['Container type']
      : []),
    ...(e?.mode === 'OCEAN' &&
    e?.cargoType === 'FCL' &&
    e?.containerQuantity == null
      ? ['Container quantity']
      : []),
    ...(e?.mode === 'AIR' && e?.grossWeight == null ? ['Gross weight'] : []),
  ];
  return (
    <State query={q}>
      <div className="detail-identity">
        <div>
          <span className="detail-eyebrow">FREIGHT ENQUIRY</span>
          <div className="detail-title">
            <h1>{q.data?.enquiryCode || ''}</h1>
            <StatusBadge value={q.data?.status} />
          </div>
          <p>
            {q.data?.customerName} · Created {date(q.data?.createdAt)}
          </p>
          {e?.sourceThreadId && (
            <Link to={`/mail/threads/${e.sourceThreadId}`}>
              View source conversation
            </Link>
          )}
        </div>
        <div className="page-heading-action">
          <Link className="btn secondary" to={`/enquiries/${id}/edit`}>
            Edit
          </Link>
          <Link className="btn primary" to={`/enquiries/${id}/quotations/new`}>
            <Plus size={16} /> Create Quotation
          </Link>
        </div>
      </div>
      <div className="detail-tabs" role="tablist" aria-label="Enquiry details">
        {['Overview', 'Cargo Details', 'Services', 'Activity', 'Documents'].map(
          (x) => (
            <button
              key={x}
              role="tab"
              aria-selected={tab === x}
              onClick={() => setTab(x)}
              className={tab === x ? 'active' : ''}
            >
              {x}
            </button>
          ),
        )}
      </div>
      {tab === 'Overview' ? (
        <div className="detail-grid">
          <SectionCard title="Shipment Information">
            <dl className="detail-list">
              <div>
                <dt>Mode</dt>
                <dd>{titleCase(e?.mode)}</dd>
              </div>
              <div>
                <dt>Route</dt>
                <dd>
                  {e?.origin} → {e?.destination}
                </dd>
              </div>
              <div>
                <dt>Ready date</dt>
                <dd>{date(e?.readyDate)}</dd>
              </div>
              <div>
                <dt>Port / airport</dt>
                <dd>
                  {e?.portOfLoading || e?.airportOfOrigin || '—'} →{' '}
                  {e?.portOfDischarge || e?.airportOfDestination || '—'}
                </dd>
              </div>
            </dl>
          </SectionCard>
          <SectionCard title="Cargo Details">
            <dl className="detail-list">
              <div>
                <dt>Commodity</dt>
                <dd>{e?.commodity || '—'}</dd>
              </div>
              <div>
                <dt>Container</dt>
                <dd>
                  {e?.containerQuantity || '—'} {e?.containerType || ''}
                </dd>
              </div>
              <div>
                <dt>Gross weight</dt>
                <dd>
                  {e?.grossWeight || '—'} {e?.weightUnit || ''}
                </dd>
              </div>
              <div>
                <dt>Packages</dt>
                <dd>{e?.packageCount || '—'}</dd>
              </div>
            </dl>
          </SectionCard>
        </div>
      ) : tab === 'Cargo Details' ? (
        <SectionCard title="Cargo Details">
          <dl className="detail-list">
            <div>
              <dt>Commodity</dt>
              <dd>{e?.commodity || '—'}</dd>
            </div>
            <div>
              <dt>Cargo type</dt>
              <dd>{e?.cargoType || '—'}</dd>
            </div>
            <div>
              <dt>Container</dt>
              <dd>
                {e?.containerQuantity || '—'} {e?.containerType || ''}
              </dd>
            </div>
            <div>
              <dt>Gross weight</dt>
              <dd>
                {e?.grossWeight || '—'} {e?.weightUnit || ''}
              </dd>
            </div>
            <div>
              <dt>Volume</dt>
              <dd>
                {e?.volume || '—'} {e?.volumeUnit || ''}
              </dd>
            </div>
            <div>
              <dt>Packages</dt>
              <dd>{e?.packageCount || '—'}</dd>
            </div>
          </dl>
        </SectionCard>
      ) : tab === 'Services' ? (
        <SectionCard title="Quotation scope">
          <div className="service-list">
            {(
              e?.serviceScope ||
              [
                e?.pickupRequired && 'Pickup & trucking',
                e?.exportCustomsRequired && 'Export customs clearance',
                e?.insuranceRequired && 'Cargo insurance',
                e?.destinationDeliveryRequired && 'Door delivery',
              ].filter(Boolean)
            ).map((service: string) => (
              <span className="service-pill" key={service}>
                <ShieldCheck size={15} />
                {service}
              </span>
            ))}
            {!e?.serviceScope?.length &&
              ![
                'pickupRequired',
                'exportCustomsRequired',
                'insuranceRequired',
                'destinationDeliveryRequired',
              ].some((key) => e?.[key]) && <p>No services selected.</p>}
          </div>
          {e?.quoteCurrency && (
            <p>Preferred quote currency: {e.quoteCurrency}</p>
          )}
          {e?.notes && <p className="detail-notes">{e.notes}</p>}
        </SectionCard>
      ) : (
        <SectionCard title={tab}>
          <Empty
            text={
              tab === 'Activity' ? 'No activity to show' : 'No documents yet'
            }
            description={
              tab === 'Activity'
                ? 'Activity history is not available for this enquiry.'
                : 'No documents are attached to this enquiry.'
            }
          />
        </SectionCard>
      )}
      {missing.length > 0 && (
        <AiActionCard
          title="AI Suggestions"
          description={`Some shipment information is missing: ${missing.join(', ')}.`}
          actions={[
            {
              label: 'Draft Missing-Info Message',
              path: '/draft-clarification',
              body: { enquiryId: id },
            },
          ]}
        />
      )}{' '}
    </State>
  );
}
