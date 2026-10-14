import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Download, Mail, Plus, Trash2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { apiFetch } from '../../api';
import {
  Heading,
  State,
  Stepper,
  Table,
} from '../../shared/components/PageElements';
import {
  ReferenceLocationPicker,
  type LocationOption,
} from '../../shared/components/ReferenceLocationPicker';
import { SearchableSelect } from '../../shared/components/SearchableSelect';
import {
  CHARGE_DESCRIPTIONS,
  CONTAINER_TYPES,
  defaultScopeDescriptions,
  INCOTERMS,
  scopeOptions as meridianScopeOptions,
  QUOTE_CURRENCIES,
} from '../../shared/lib/quotationOptions';
import {
  api,
  date,
  fmt,
  post,
  put,
  qc,
  toast,
  useData,
} from '../../shared/lib/workspace';
import { AiActionCard } from '../ai/AiAssist';
import QuotationDirectory from './QuotationDirectory';
import { formatQuotationRoute, useResolvedFinalPlace } from './quotationRoute';
function shipmentSnapshot(value?: string | null): Record<string, any> | null {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}
export function Quotations() {
  return <QuotationDirectory />;
}
export function QuoteForm() {
  const { id, enquiryId } = useParams();
  const nav = useNavigate();
  const enquiry = useQuery({
    queryKey: ['enquiry', enquiryId],
    queryFn: () => api(`/enquiries/${enquiryId}`),
    enabled: !!enquiryId,
  });
  const existing = useQuery({
    queryKey: ['quote', id],
    queryFn: () => api(`/quotations/${id}`),
    enabled: !!id,
  });
  const [form, setForm] = useState<any>({
    currency: 'USD',
    transitTimeText: '',
    validUntil: new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10),
    termsAndConditions:
      'Rates subject to availability, fuel surcharge and applicable charges.',
    notes: '',
    lines: [
      {
        description: 'Ocean Freight',
        quantity: 1,
        unitPrice: 0,
        currency: 'USD',
      },
    ],
  });
  const [err, setErr] = useState('');
  const [quoteIncoterm, setQuoteIncoterm] = useState('');
  const [shipment, setShipment] = useState<Record<string, any>>({});
  const [scopeCustomized, setScopeCustomized] = useState(false);
  const [saving, setSaving] = useState(false);
  const [step, setStep] = useState(0);
  const [route, setRoute] = useState({
    polCode: '',
    podCode: '',
    fpodCode: '',
    polName: '',
    podName: '',
    fpodName: '',
  });
  const seededRoute = useRef('');
  const resolvedFinalName = useResolvedFinalPlace(
    route.fpodCode,
    route.fpodName,
  );
  useEffect(() => {
    if (
      resolvedFinalName &&
      route.fpodName === route.fpodCode &&
      resolvedFinalName !== route.fpodCode
    )
      setRoute((current) =>
        current.fpodCode === route.fpodCode
          ? { ...current, fpodName: resolvedFinalName }
          : current,
      );
  }, [resolvedFinalName, route.fpodCode, route.fpodName]);
  const seededScope = useRef('');
  useEffect(() => {
    if (id && existing.data) {
      setForm({
        ...existing.data.quotation,
        lines: existing.data.lines.map((line: any) => ({
          ...line,
          unitPrice: line.originalUnitPrice ?? line.unitPrice,
          currency: line.originalCurrency ?? existing.data.quotation.currency,
        })),
      });
      const saved = shipmentSnapshot(existing.data.quotation?.shipmentSnapshot);
      setQuoteIncoterm(
        saved?.incoterm || existing.data.enquiry?.enquiry?.incoterm || '',
      );
      setShipment(saved || {});
      setScopeCustomized(saved?.scopeCustomized ?? true);
    }
  }, [existing.data]);
  const e = id
    ? shipmentSnapshot(existing.data?.quotation?.shipmentSnapshot) ||
      existing.data?.enquiry?.enquiry
    : enquiry.data?.enquiry;
  useEffect(() => {
    if (id || !e || seededScope.current === String(enquiryId)) return;
    seededScope.current = String(enquiryId);
    const term = e.incoterm || '';
    setQuoteIncoterm(term);
    setScopeCustomized(
      Array.isArray(e.serviceScope) &&
        JSON.stringify(e.serviceScope) !==
          JSON.stringify(defaultScopeDescriptions(term, e.mode, !!e.fpodCode)),
    );
    setShipment({
      ...e,
      shipmentMode:
        e.shipmentMode ||
        (e.mode === 'AIR'
          ? 'AIR'
          : e.mode === 'ROAD'
            ? e.cargoType === 'LTL'
              ? 'LTL'
              : 'FTL'
            : e.cargoType === 'LCL'
              ? 'LCL'
              : 'FCL'),
    });
    setForm((current: any) => ({
      ...current,
      currency: e.quoteCurrency || current.currency,
      lines: [
        ...(
          e.serviceScope ?? defaultScopeDescriptions(term, e.mode, !!e.fpodCode)
        ).map((description: string) => ({
          description,
          quantity: 1,
          unitPrice: 0,
          currency: e.quoteCurrency || current.currency,
        })),
        ...(e.referenceDestinationCharges
          ? [
              {
                description: 'Destination charges',
                quantity: 1,
                unitPrice: 0,
                currency: e.quoteCurrency || current.currency,
                pricingTreatment: 'FOR_REFERENCE',
              },
            ]
          : []),
      ],
    }));
  }, [e, enquiryId, id]);
  useEffect(() => {
    const key = String(e?.enquiryId || e?.id || enquiryId || id || '');
    if (!e || !key || seededRoute.current === key) return;
    seededRoute.current = key;
    setRoute({
      polCode: e.polCode || '',
      podCode: e.podCode || '',
      fpodCode: e.fpodCode || '',
      polName: e.portOfLoading || e.airportOfOrigin || e.origin || '',
      podName:
        e.portOfDischarge || e.airportOfDestination || e.destination || '',
      fpodName: e.fpodName || e.fpodCode || '',
    });
  }, [e, enquiryId, id]);
  const shipmentMode =
    shipment.shipmentMode ||
    (e?.mode === 'AIR' ? 'AIR' : e?.mode === 'ROAD' ? 'FTL' : 'FCL');
  const transportMode = ['FTL', 'LTL'].includes(shipmentMode)
    ? 'ROAD'
    : shipmentMode === 'AIR'
      ? 'AIR'
      : 'OCEAN';
  const shipmentChange = (field: string, value: any) =>
    setShipment((current) => ({ ...current, [field]: value }));
  const routeDisplay = formatQuotationRoute(
    { code: route.polCode, name: route.polName },
    { code: route.podCode, name: route.podName },
    { code: route.fpodCode, name: resolvedFinalName },
  );
  const shipmentFacts = [
    ['Mode', shipmentMode],
    ['POL', route.polCode ? routeDisplay.loading : null],
    ['POD', route.podCode ? routeDisplay.discharge : null],
    ['FPOD', routeDisplay.finalPlace],
    ['Incoterm', quoteIncoterm],
    ['Cargo ready', e?.readyDate ? date(e.readyDate) : null],
    ['Commodity', e?.commodity],
    ['Equipment', e?.containerType],
    ['Containers', e?.containerQuantity],
  ].filter(
    ([, value]) => value !== null && value !== undefined && value !== '',
  );
  const change = (k: string, v: any) => setForm((f: any) => ({ ...f, [k]: v }));
  const changeRouteLocation = (
    field: 'pol' | 'pod' | 'fpod',
    name: string,
    code: string,
  ) => {
    const codeKey = `${field}Code` as 'polCode' | 'podCode' | 'fpodCode';
    const nameKey = `${field}Name` as 'polName' | 'podName' | 'fpodName';
    const affectsHaulage =
      (field === 'pod' || field === 'fpod') && route[codeKey] !== code;
    const haulage = affectsHaulage
      ? form.lines.filter(
          (line: any) => line.description === 'Haulage to final place',
        )
      : [];
    if (
      haulage.some((line: any) => Number(line.unitPrice) > 0) &&
      !window.confirm(
        'Changing this location will remove the priced haulage to final place charge. Continue?',
      )
    )
      return;
    if (haulage.length) {
      setForm((current: any) => ({
        ...current,
        lines: current.lines.filter(
          (line: any) => line.description !== 'Haulage to final place',
        ),
      }));
      setScopeCustomized(true);
      toast('Haulage to final place removed; review pricing for the new route');
    }
    setRoute((current) => ({ ...current, [nameKey]: name, [codeKey]: code }));
  };
  const scopeOptions = meridianScopeOptions(
    transportMode,
    !!routeDisplay.finalPlace,
  ).map(({ description }) => description);
  const currencyOptions = [
    ...new Set(
      [
        ...QUOTE_CURRENCIES,
        form.currency,
        ...form.lines.map((line: any) => line.currency),
      ].filter(Boolean),
    ),
  ];
  const resetScope = (
    term = quoteIncoterm,
    mode = shipmentMode,
    hasFinalPlace = !!routeDisplay.finalPlace,
  ) => {
    const mappedMode = ['FTL', 'LTL'].includes(mode)
      ? 'ROAD'
      : mode === 'AIR'
        ? 'AIR'
        : 'OCEAN';
    const scopeNames = new Set(
      ['OCEAN', 'AIR', 'ROAD'].flatMap((candidate) =>
        meridianScopeOptions(candidate, true).map(
          ({ description }) => description,
        ),
      ),
    );
    const defaults = defaultScopeDescriptions(term, mappedMode, hasFinalPlace);
    setForm((current: any) => ({
      ...current,
      lines: [
        ...current.lines.filter(
          (line: any) => !scopeNames.has(line.description),
        ),
        ...defaults.map((description) => ({
          ...(current.lines.find(
            (line: any) => line.description === description,
          ) || {
            description,
            quantity: 1,
            unitPrice: 0,
            currency: current.currency,
          }),
          pricingTreatment: 'INCLUDED',
        })),
      ],
    }));
    setScopeCustomized(false);
  };
  const selectIncoterm = (term: string) => {
    if (term === quoteIncoterm) return;
    if (
      scopeCustomized &&
      !window.confirm(
        'Changing the Incoterm will reset your customized scope. Continue?',
      )
    )
      return;
    if (
      !scopeCustomized &&
      form.lines.some((line: any) => Number(line.unitPrice) > 0) &&
      !window.confirm(
        'Changing the Incoterm may remove priced scope charges. Continue?',
      )
    )
      return;
    setQuoteIncoterm(term);
    resetScope(term);
  };
  const selectMode = (mode: string) => {
    if (mode === shipmentMode) return;
    if (
      !window.confirm(
        'Changing shipment mode may clear mode-specific shipment details and scope charges. Continue?',
      )
    )
      return;
    setShipment((current) => ({
      ...current,
      shipmentMode: mode,
      containerType: '',
      containerQuantity: '',
      truckType: '',
      truckCount: '',
    }));
    setRoute((current) => ({
      ...current,
      polCode: '',
      podCode: '',
      fpodCode: '',
      polName: '',
      podName: '',
      fpodName: '',
    }));
    resetScope(quoteIncoterm, mode, false);
  };
  const additionalServices = [
    'Fumigation',
    'Certificate of Origin',
    'Warehousing',
  ];
  const referenceDestinationAvailable = ['CIF', 'CFR', 'CPT', 'CIP'].includes(
    quoteIncoterm,
  );
  const referenceDestinationSelected = form.lines.some(
    (line: any) =>
      line.description === 'Destination charges' &&
      line.pricingTreatment === 'FOR_REFERENCE',
  );
  const toggleReferenceDestination = () => {
    setScopeCustomized(true);
    setForm((current: any) => ({
      ...current,
      lines: referenceDestinationSelected
        ? current.lines.filter(
            (line: any) =>
              !(
                line.description === 'Destination charges' &&
                line.pricingTreatment === 'FOR_REFERENCE'
              ),
          )
        : current.lines.some(
              (line: any) => line.description === 'Destination charges',
            )
          ? current.lines.map((line: any) =>
              line.description === 'Destination charges'
                ? { ...line, pricingTreatment: 'FOR_REFERENCE' }
                : line,
            )
          : [
              ...current.lines,
              {
                description: 'Destination charges',
                quantity: 1,
                unitPrice: 0,
                currency: current.currency,
                pricingTreatment: 'FOR_REFERENCE',
              },
            ],
    }));
  };
  const toggleScope = (description: string) => {
    setScopeCustomized(true);
    if (
      /freight/i.test(description) &&
      form.lines.some((line: any) => line.description === description)
    )
      return;
    setForm((current: any) => {
      const included = current.lines.some(
        (line: any) =>
          line.description === description &&
          line.pricingTreatment !== 'FOR_REFERENCE',
      );
      return {
        ...current,
        lines: included
          ? current.lines.filter(
              (line: any) =>
                line.description !== description ||
                line.pricingTreatment === 'FOR_REFERENCE',
            )
          : current.lines.some((line: any) => line.description === description)
            ? current.lines.map((line: any) =>
                line.description === description
                  ? { ...line, pricingTreatment: 'INCLUDED' }
                  : line,
              )
            : [
                ...current.lines,
                {
                  description,
                  quantity: 1,
                  unitPrice: 0,
                  currency: current.currency,
                },
              ],
      };
    });
  };
  const pricedLines = form.lines.filter(
    (line: any) => line.pricingTreatment !== 'FOR_REFERENCE',
  );
  const hasMixedCurrency = pricedLines.some(
    (line: any) => (line.currency || form.currency) !== form.currency,
  );
  const [previewInput, setPreviewInput] = useState<{
    currency: string;
    lines: { quantity: number; unitPrice: number; currency: string }[];
  } | null>(null);
  useEffect(() => {
    if (!hasMixedCurrency) {
      setPreviewInput(null);
      return;
    }
    const timer = window.setTimeout(() => {
      setPreviewInput({
        currency: form.currency,
        lines: pricedLines.map((line: any) => ({
          quantity: Number(line.quantity),
          unitPrice: Number(line.unitPrice),
          currency: line.currency || form.currency,
        })),
      });
    }, 450);
    return () => window.clearTimeout(timer);
  }, [form.currency, form.lines, hasMixedCurrency]);
  const fxPreview = useQuery({
    queryKey: ['quotation-fx-preview', previewInput],
    queryFn: () => post('/fx/quote-preview', previewInput),
    enabled: hasMixedCurrency && !!previewInput,
    retry: false,
    staleTime: 60_000,
  });
  const previewMatchesForm =
    previewInput?.currency === form.currency &&
    JSON.stringify(previewInput?.lines) ===
      JSON.stringify(
        pricedLines.map((line: any) => ({
          quantity: Number(line.quantity),
          unitPrice: Number(line.unitPrice),
          currency: line.currency || form.currency,
        })),
      );
  const convertedPreview =
    hasMixedCurrency && previewMatchesForm ? fxPreview.data : null;
  const total = pricedLines.reduce(
    (sum: number, line: any) =>
      sum + Number(line.quantity) * Number(line.unitPrice),
    0,
  );
  const save = async () => {
    setSaving(true);
    setErr('');
    try {
      if (!route.polCode || !route.podCode) {
        setErr(
          'Select canonical loading and discharge locations before saving.',
        );
        return;
      }
      const data = {
        ...form,
        shipmentOverride: {
          incoterm: quoteIncoterm,
          ...shipment,
          polCode: route.polCode || null,
          podCode: route.podCode || null,
          fpodCode: route.fpodCode || null,
          shipmentMode,
          cargoReadyDate: shipment.cargoReadyDate || shipment.readyDate || null,
          scope: [...scopeOptions, ...additionalServices].filter(
            (description) =>
              form.lines.some(
                (line: any) =>
                  line.description === description &&
                  line.pricingTreatment !== 'FOR_REFERENCE',
              ),
          ),
        },
        lines: form.lines.map((x: any) => ({
          description: x.description,
          quantity: Number(x.quantity),
          unitPrice: Number(x.unitPrice),
          currency: x.currency || form.currency,
          pricingTreatment: x.pricingTreatment || 'INCLUDED',
        })),
      };
      const r = id
        ? await put('/quotations/' + id, data)
        : await post(`/enquiries/${enquiryId}/quotations`, data);
      qc.invalidateQueries();
      toast('Quotation saved');
      nav('/quotations/' + r.id);
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <>
      <Heading
        title={id ? 'Edit Quotation' : 'Create Quotation'}
        subtitle="Build and review a customer freight offer."
      />
      <div className="card quote-summary">
        <strong>{e?.customerName || 'Enquiry'}</strong>
        <span>
          {routeDisplay.label} · {e?.mode} · {e?.commodity}
        </span>
      </div>
      <div className="quote-builder-grid">
        <div className="card quote-editor">
          <Stepper labels={['Shipment', 'Charges', 'Review']} current={step} />
          {step === 0 && (
            <div className="quote-step freight-quote-sections">
              <section className="freight-form-section">
                <h2>Shipment</h2>
                {
                  <div className="freight-field-grid freight-field-grid-quote freight-quote-route">
                    {(
                      [
                        [
                          'pol',
                          transportMode === 'AIR'
                            ? 'Airport of origin'
                            : 'Port of loading (POL)',
                        ],
                        [
                          'pod',
                          transportMode === 'AIR'
                            ? 'Airport of destination'
                            : 'Port of discharge (POD)',
                        ],
                        ['fpod', 'Final place (FPOD)'],
                      ] as const
                    ).map(([field, label]) => {
                      const codeKey = `${field}Code` as
                        | 'polCode'
                        | 'podCode'
                        | 'fpodCode';
                      const nameKey = `${field}Name` as
                        | 'polName'
                        | 'podName'
                        | 'fpodName';
                      return (
                        <ReferenceLocationPicker
                          key={field}
                          id={`quotation-${field}`}
                          label={label}
                          mode={
                            field === 'fpod' || transportMode === 'ROAD'
                              ? 'ALL'
                              : transportMode
                          }
                          value={route[nameKey]}
                          code={route[codeKey]}
                          onTextChange={(value) =>
                            changeRouteLocation(field, value, '')
                          }
                          onSelect={(location: LocationOption) =>
                            changeRouteLocation(
                              field,
                              location.name,
                              location.code,
                            )
                          }
                        />
                      );
                    })}
                  </div>
                }
                <div className="freight-field-grid freight-field-grid-quote">
                  <label>
                    <span className="label">Shipment mode</span>
                    <SearchableSelect ariaLabel="Shipment mode" value={shipmentMode} onChange={selectMode} options={['FCL', 'LCL', 'AIR', 'FTL', 'LTL'].map((value) => ({ value, label: value }))} />
                  </label>
                  <label>
                    <span className="label">Customer reference</span>
                    <input
                      className="field"
                      value={shipment.customerReference || ''}
                      onChange={(event) =>
                        shipmentChange('customerReference', event.target.value)
                      }
                    />
                  </label>
                  <label>
                    <span className="label">Cargo ready date</span>
                    <input
                      className="field"
                      type="date"
                      value={
                        shipment.cargoReadyDate || shipment.readyDate || ''
                      }
                      onChange={(event) =>
                        shipmentChange('cargoReadyDate', event.target.value)
                      }
                    />
                  </label>
                  <label>
                    <span className="label">Commodity</span>
                    <input
                      className="field"
                      value={shipment.commodity || ''}
                      onChange={(event) =>
                        shipmentChange('commodity', event.target.value)
                      }
                    />
                  </label>
                  <label>
                    <span className="label">Free days at destination</span>
                    <input
                      className="field"
                      type="number"
                      min="0"
                      value={shipment.freeDaysAtDestination ?? ''}
                      onChange={(event) =>
                        shipmentChange(
                          'freeDaysAtDestination',
                          event.target.value === ''
                            ? null
                            : Number(event.target.value),
                        )
                      }
                    />
                  </label>
                  <label>
                    <span className="label">Dangerous goods</span>
                    <input
                      type="checkbox"
                      checked={!!shipment.dangerousGoods}
                      onChange={(event) =>
                        shipmentChange('dangerousGoods', event.target.checked)
                      }
                    />
                  </label>
                  {shipmentMode === 'FCL' && (
                    <>
                      <label>
                        <span className="label">Container type</span>
                        <SearchableSelect ariaLabel="Container type" value={shipment.containerType || ''} onChange={(value) => shipmentChange('containerType', value)} placeholder="Select container" options={CONTAINER_TYPES.map((value) => ({ value, label: value }))} />
                      </label>
                      <label>
                        <span className="label">Container quantity</span>
                        <input
                          className="field"
                          type="number"
                          min="1"
                          value={shipment.containerQuantity ?? ''}
                          onChange={(event) =>
                            shipmentChange(
                              'containerQuantity',
                              Number(event.target.value),
                            )
                          }
                        />
                      </label>
                      <label>
                        <span className="label">
                          Gross weight per container (kg)
                        </span>
                        <input
                          className="field"
                          type="number"
                          min="0"
                          value={shipment.grossWeightPerContainerKg ?? ''}
                          onChange={(event) =>
                            shipmentChange(
                              'grossWeightPerContainerKg',
                              Number(event.target.value),
                            )
                          }
                        />
                      </label>
                    </>
                  )}
                  {['LCL', 'AIR'].includes(shipmentMode) && (
                    <>
                      <label>
                        <span className="label">
                          {shipmentMode === 'AIR' ? 'Pieces' : 'Packages'}
                        </span>
                        <input
                          className="field"
                          type="number"
                          min="1"
                          value={
                            shipment[
                              shipmentMode === 'AIR' ? 'pieces' : 'packageCount'
                            ] ?? ''
                          }
                          onChange={(event) =>
                            shipmentChange(
                              shipmentMode === 'AIR'
                                ? 'pieces'
                                : 'packageCount',
                              Number(event.target.value),
                            )
                          }
                        />
                      </label>
                      <label>
                        <span className="label">CBM</span>
                        <input
                          className="field"
                          type="number"
                          min="0"
                          step="0.001"
                          value={shipment.cbm ?? shipment.volume ?? ''}
                          onChange={(event) =>
                            shipmentChange('cbm', Number(event.target.value))
                          }
                        />
                      </label>
                      <label>
                        <span className="label">Weight (kg)</span>
                        <input
                          className="field"
                          type="number"
                          min="0"
                          step="0.001"
                          value={
                            shipment.weightKg ?? shipment.grossWeight ?? ''
                          }
                          onChange={(event) =>
                            shipmentChange(
                              'weightKg',
                              Number(event.target.value),
                            )
                          }
                        />
                      </label>
                      {shipmentMode === 'LCL' ? (
                        <>
                          <label>
                            <span className="label">Stackable</span>
                            <input
                              type="checkbox"
                              checked={!!shipment.stackable}
                              onChange={(event) =>
                                shipmentChange(
                                  'stackable',
                                  event.target.checked,
                                )
                              }
                            />
                          </label>
                          <span>
                            W/M:{' '}
                            {Math.max(
                              Number(shipment.cbm ?? shipment.volume) || 0,
                              (Number(
                                shipment.weightKg ?? shipment.grossWeight,
                              ) || 0) / 1000,
                            ).toFixed(3)}
                          </span>
                        </>
                      ) : (
                        <label>
                          <span className="label">Chargeable weight (kg)</span>
                          <input
                            className="field"
                            type="number"
                            min="0"
                            value={shipment.chargeableWeightKg ?? ''}
                            onChange={(event) =>
                              shipmentChange(
                                'chargeableWeightKg',
                                Number(event.target.value),
                              )
                            }
                          />
                        </label>
                      )}
                    </>
                  )}
                  {shipmentMode === 'FTL' && (
                    <>
                      <label>
                        <span className="label">Truck type</span>
                        <input
                          className="field"
                          value={shipment.truckType || ''}
                          onChange={(event) =>
                            shipmentChange('truckType', event.target.value)
                          }
                        />
                      </label>
                      <label>
                        <span className="label">Truck count</span>
                        <input
                          className="field"
                          type="number"
                          min="1"
                          value={shipment.truckCount ?? ''}
                          onChange={(event) =>
                            shipmentChange(
                              'truckCount',
                              Number(event.target.value),
                            )
                          }
                        />
                      </label>
                    </>
                  )}
                </div>
                <dl className="freight-quote-shipment">
                  {shipmentFacts.map(([label, value]) => (
                    <div key={String(label)}>
                      <dt>{label}</dt>
                      <dd>{value}</dd>
                    </div>
                  ))}
                </dl>
                <div className="freight-field-grid freight-field-grid-quote">
                  {[
                    ['transitTimeText', 'Transit time', 'text'],
                    ['validUntil', 'Valid until', 'date'],
                  ].map(([key, label, type]) => (
                    <label key={key}>
                      <span className="label">{label}</span>
                      <input
                        className="field"
                        type={type}
                        value={form[key] || ''}
                        onChange={(event) => change(key, event.target.value)}
                      />
                    </label>
                  ))}
                </div>
              </section>
              <section className="freight-form-section freight-scope-card">
                <h2>Scope &amp; currency</h2>
                <label className="freight-currency-field freight-scope-term-field">
                  <span className="label">Incoterm</span>
                  <SearchableSelect ariaLabel="Incoterm" value={quoteIncoterm} onChange={selectIncoterm} placeholder="Select Incoterm" allowClear options={INCOTERMS.map((value) => ({ value, label: value }))} />
                </label>
                <div className="freight-scope-heading">
                  <strong>What this quotation covers</strong>
                  {quoteIncoterm && (
                    <>
                      <span className="freight-incoterm">{quoteIncoterm}</span>
                      <button
                        type="button"
                        className="btn secondary"
                        onClick={() => {
                          if (
                            scopeCustomized &&
                            !window.confirm(
                              'Reset your customized scope to Incoterm defaults?',
                            )
                          )
                            return;
                          resetScope();
                        }}
                      >
                        Reset to Incoterm default
                      </button>
                    </>
                  )}
                  <span>
                    Set by the Incoterm. Click to add or remove services; set
                    prices in Charges.
                  </span>
                </div>
                <div className="freight-scope-chips">
                  {scopeOptions.map((description) => {
                    const selected = form.lines.some(
                      (line: any) =>
                        line.description === description &&
                        line.pricingTreatment !== 'FOR_REFERENCE',
                    );
                    return (
                      <button
                        key={description}
                        type="button"
                        className={
                          'freight-scope-chip' + (selected ? ' selected' : '')
                        }
                        aria-pressed={selected}
                        disabled={selected && /freight/i.test(description)}
                        onClick={() =>
                          description === 'Destination charges' &&
                          referenceDestinationAvailable
                            ? toggleReferenceDestination()
                            : toggleScope(description)
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
                <div className="freight-scope-chips">
                  {additionalServices.map((description) => {
                    const selected = form.lines.some(
                      (line: any) => line.description === description,
                    );
                    return (
                      <button
                        key={description}
                        type="button"
                        className={
                          'freight-scope-chip' + (selected ? ' selected' : '')
                        }
                        aria-pressed={selected}
                        onClick={() => toggleScope(description)}
                      >
                        <span aria-hidden="true">{selected ? '✓' : '+'}</span>
                        {description}
                      </button>
                    );
                  })}
                </div>
                {referenceDestinationAvailable && (
                  <label className="freight-reference-destination">
                    <input
                      type="checkbox"
                      checked={referenceDestinationSelected}
                      onChange={toggleReferenceDestination}
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
                    <SearchableSelect ariaLabel="Quote currency" value={form.currency} onChange={(value) => change('currency', value)} options={currencyOptions.map((value) => ({ value, label: value }))} />
                  </label>
                  <p className="freight-currency-note">
                    Each charge can use its own currency. Included charges are
                    converted into {form.currency} using the available ECB
                    reference or authorized manual rate. The saved rate appears
                    on the quotation PDF.
                  </p>
                </div>
              </section>
            </div>
          )}
          {step === 1 && (
            <div className="quote-step">
              <div className="flex justify-between items-center gap-3 mb-3">
                <div>
                  <h2>Freight and additional charges</h2>
                  <p>
                    Included lines contribute to the total after currency
                    conversion.
                  </p>
                </div>
                <button
                  type="button"
                  className="btn secondary"
                  onClick={() =>
                    change('lines', [
                      ...form.lines,
                      {
                        description: '',
                        quantity: 1,
                        unitPrice: 0,
                        currency: form.currency,
                      },
                    ])
                  }
                >
                  <Plus size={14} /> Add charge
                </button>
              </div>
              <div className="table-wrap">
                <datalist id="quotation-charge-descriptions">
                  {CHARGE_DESCRIPTIONS.map((description) => (
                    <option key={description} value={description} />
                  ))}
                </datalist>
                <Table className="table">
                  <thead>
                    <tr>
                      {[
                        'Description',
                        'Quantity',
                        'Unit price',
                        'Currency',
                        'Treatment',
                        'Amount',
                        '',
                      ].map((label) => (
                        <th key={label}>{label}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {form.lines.map((line: any, index: number) => (
                      <tr key={index}>
                        <td>
                          <input
                            className="field min-w-48"
                            aria-label={`Charge ${index + 1} description`}
                            list="quotation-charge-descriptions"
                            value={line.description}
                            onChange={(event) =>
                              change(
                                'lines',
                                form.lines.map((x: any, i: number) =>
                                  i === index
                                    ? {
                                        ...x,
                                        description: event.target.value,
                                        pricingTreatment:
                                          event.target.value ===
                                            'Destination charges' &&
                                          ['CIF', 'CFR', 'CPT', 'CIP'].includes(
                                            quoteIncoterm,
                                          )
                                            ? 'FOR_REFERENCE'
                                            : 'INCLUDED',
                                      }
                                    : x,
                                ),
                              )
                            }
                          />
                        </td>
                        <td>
                          <input
                            className="field w-24"
                            aria-label={`Charge ${index + 1} quantity`}
                            type="number"
                            min="0.01"
                            step="0.01"
                            value={line.quantity}
                            onChange={(event) =>
                              change(
                                'lines',
                                form.lines.map((x: any, i: number) =>
                                  i === index
                                    ? { ...x, quantity: event.target.value }
                                    : x,
                                ),
                              )
                            }
                          />
                        </td>
                        <td>
                          <input
                            className="field w-32"
                            aria-label={`Charge ${index + 1} unit price`}
                            type="number"
                            min="0"
                            value={line.unitPrice}
                            onChange={(event) =>
                              change(
                                'lines',
                                form.lines.map((x: any, i: number) =>
                                  i === index
                                    ? { ...x, unitPrice: event.target.value }
                                    : x,
                                ),
                              )
                            }
                          />
                        </td>
                        <td>
                          <SearchableSelect
                            ariaLabel={`Charge ${index + 1} currency`}
                            value={line.currency || form.currency}
                            onChange={(value) =>
                              change(
                                'lines',
                                form.lines.map((x: any, i: number) =>
                                  i === index
                                    ? { ...x, currency: value }
                                    : x,
                                ),
                              )
                            }
                            options={currencyOptions.map((value) => ({ value, label: value }))}
                          />
                        </td>
                        <td>
                          {line.description === 'Destination charges' &&
                          ['CIF', 'CFR', 'CPT', 'CIP'].includes(quoteIncoterm)
                            ? 'For reference'
                            : 'Included'}
                        </td>
                        <td>
                          {line.currency || form.currency}{' '}
                          {fmt(Number(line.quantity) * Number(line.unitPrice))}
                          {line.pricingTreatment === 'FOR_REFERENCE' &&
                            ' · For reference'}
                        </td>
                        <td>
                          <button
                            type="button"
                            aria-label={`Remove charge ${index + 1}`}
                            onClick={() =>
                              change(
                                'lines',
                                form.lines.filter(
                                  (_: any, i: number) => i !== index,
                                ),
                              )
                            }
                          >
                            <Trash2 size={16} />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </div>
            </div>
          )}
          {step === 2 && (
            <div className="quote-step">
              <h2>Review and save</h2>
              <p>
                Check the amount and customer terms before saving the draft.
              </p>
              <label className="block mt-4">
                <span className="label">Terms and conditions</span>
                <textarea
                  className="field"
                  rows={5}
                  value={form.termsAndConditions}
                  onChange={(event) =>
                    change('termsAndConditions', event.target.value)
                  }
                />
              </label>
              <label className="block mt-4">
                <span className="label">Internal notes</span>
                <textarea
                  className="field"
                  rows={3}
                  value={form.notes || ''}
                  onChange={(event) => change('notes', event.target.value)}
                />
              </label>
              <div className="quote-checklist">
                <span>✓ Enquiry selected</span>
                <span>
                  {form.lines.length ? '✓' : '○'} Charge lines entered
                </span>
                <span>{form.validUntil ? '✓' : '○'} Validity date set</span>
              </div>
            </div>
          )}
          {err && (
            <p className="quote-error" role="alert">
              {err}
            </p>
          )}
          <div className="quote-step-actions">
            <button
              type="button"
              className="btn secondary"
              disabled={step === 0}
              onClick={() => setStep(step - 1)}
            >
              Back
            </button>
            {step < 2 ? (
              <button
                type="button"
                className="btn primary"
                onClick={() => setStep(step + 1)}
              >
                Continue <ArrowRight size={15} />
              </button>
            ) : (
              <button
                type="button"
                className="btn primary"
                onClick={save}
                disabled={saving}
              >
                {saving ? 'Saving…' : 'Save draft'}
              </button>
            )}
          </div>
        </div>
        <aside className="card quote-live-preview">
          <span className="cw-eyebrow">LIVE QUOTATION SUMMARY</span>
          <h2>{e?.customerName || 'Customer quotation'}</h2>
          <p>{routeDisplay.label}</p>
          <div className="quote-preview-lines">
            {form.lines.map((line: any, index: number) => (
              <div key={index}>
                <span>{line.description || 'Charge'}</span>
                <strong>
                  {line.currency || form.currency}{' '}
                  {fmt(Number(line.quantity) * Number(line.unitPrice))}
                  {line.pricingTreatment === 'FOR_REFERENCE' &&
                    ' · For reference'}
                  {line.pricingTreatment !== 'FOR_REFERENCE' &&
                    convertedPreview?.lines?.[pricedLines.indexOf(line)] && (
                      <small>
                        {' '}
                        → {form.currency}{' '}
                        {fmt(
                          convertedPreview.lines[pricedLines.indexOf(line)]
                            .convertedAmount,
                        )}
                        {convertedPreview.lines[pricedLines.indexOf(line)]
                          .source === 'ECB_REFERENCE' &&
                          ` · ECB ${convertedPreview.lines[pricedLines.indexOf(line)].observationDate}`}
                      </small>
                    )}
                </strong>
              </div>
            ))}
          </div>
          <div className="quote-preview-total">
            <span>Total</span>
            <strong>
              {hasMixedCurrency
                ? convertedPreview
                  ? `${form.currency} ${fmt(convertedPreview.total)}`
                  : fxPreview.isError && previewMatchesForm
                    ? 'Rate unavailable'
                    : 'Calculating…'
                : `${form.currency} ${fmt(total)}`}
            </strong>
          </div>
          {hasMixedCurrency && fxPreview.isError && previewMatchesForm && (
            <p role="alert" className="quote-error">
              Currency conversion unavailable:{' '}
              {(fxPreview.error as Error).message}
            </p>
          )}
          <small>
            Valid until {form.validUntil ? date(form.validUntil) : '—'}
          </small>
          {id && (
            <Link to={`/quotations/${id}`} className="btn secondary">
              Open saved PDF and email actions <ArrowRight size={14} />
            </Link>
          )}
        </aside>
      </div>
      {id && (
        <AiActionCard
          title="Quotation AI Assistant"
          description="Suggestions use the saved quotation. Save changes before requesting a new draft."
          actions={[
            {
              label: 'Generate Quotation Text',
              path: `/quotations/${id}/text`,
            },
            {
              label: 'Check for Inconsistencies',
              path: `/quotations/${id}/check`,
            },
            {
              label: 'Improve Customer Email',
              path: `/quotations/${id}/email`,
            },
            {
              label: 'Add Standard Terms Draft',
              path: `/quotations/${id}/terms`,
            },
          ]}
        />
      )}
    </>
  );
}
export function QuoteDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const q = useData('quote', `/quotations/${id}`);
  const shipment =
    shipmentSnapshot(q.data?.quotation?.shipmentSnapshot) ||
    q.data?.enquiry?.enquiry;
  const finalPlaceName = useResolvedFinalPlace(
    shipment?.fpodCode,
    shipment?.fpodName,
  );
  const detailRoute = formatQuotationRoute(
    {
      code: shipment?.polCode,
      name:
        shipment?.portOfLoading ||
        shipment?.airportOfOrigin ||
        shipment?.origin,
    },
    {
      code: shipment?.podCode,
      name:
        shipment?.portOfDischarge ||
        shipment?.airportOfDestination ||
        shipment?.destination,
    },
    { code: shipment?.fpodCode, name: finalPlaceName },
  );
  const [compose, setCompose] = useState<any>(null);
  const [replySource, setReplySource] = useState('');
  const [mailChoices, setMailChoices] = useState<
    { id: string; subject: string; fromEmail: string }[]
  >([]);
  const [err, setErr] = useState('');
  const [sending, setSending] = useState(false);
  const preview = async () => {
    const r = await apiFetch('/quotations/' + id + '/pdf');
    if (!r.ok) {
      setErr('Could not load PDF');
      return;
    }
    window.open(URL.createObjectURL(await r.blob()), '_blank');
  };
  const finalize = async () => {
    try {
      await post(`/quotations/${id}/finalize`, {});
      qc.invalidateQueries();
      toast('Quotation finalized and ready to send');
    } catch (e: any) {
      setErr(e.message);
    }
  };
  const email = async () => {
    try {
      setCompose(await api(`/quotations/${id}/email-preview`));
      const response = await apiFetch('/mail/messages');
      const choices: { id: string; subject: string; fromEmail: string }[] =
        response.ok ? await response.json() : [];
      const sourceId = q.data?.enquiry?.enquiry?.sourceMessageId;
      if (sourceId) {
        // Detail enforces the current tenant and active mailbox before selecting a reply.
        const sourceResponse = await apiFetch(`/mail/messages/${sourceId}`);
        if (sourceResponse.ok) {
          const source = await sourceResponse.json();
          if (!choices.some((message) => message.id === sourceId))
            choices.unshift(source);
          setReplySource(sourceId);
        } else {
          setReplySource('');
        }
      } else {
        setReplySource('');
      }
      setMailChoices(choices);
    } catch (e: any) {
      setErr(e.message);
    }
  };
  const openMailDraft = async () => {
    setSending(true);
    setErr('');
    try {
      const result = await post(`/quotations/${id}/mail-draft`, {
        replyToMessageId: replySource || null,
        to: compose.to,
        cc: compose.cc,
        subject: compose.subject,
        body: compose.body,
      });
      nav(`/mail/compose?draftId=${result.draftId}`);
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setSending(false);
    }
  };
  return (
    <State query={q}>
      <Heading
        title={q.data?.quotationNumber || ''}
        subtitle={`${q.data?.customerName} · ${q.data?.status}`}
        action={
          <div className="flex flex-wrap gap-2">
            {q.data?.status === 'DRAFT' && (
              <>
                <Link
                  className="btn secondary"
                  to={'/quotations/' + id + '/edit'}
                >
                  Edit
                </Link>
                <button className="btn primary" onClick={finalize}>
                  Finalize quotation
                </button>
              </>
            )}
            <button className="btn secondary flex gap-2" onClick={preview}>
              <Download size={17} /> Preview PDF
            </button>
            {q.data?.status === 'READY_TO_SEND' && (
              <button className="btn primary flex gap-2" onClick={email}>
                <Mail size={17} /> Email Quote
              </button>
            )}
          </div>
        }
      />
      {err && <p className="text-red-600 mb-4">{err}</p>}
      <div className="grid md:grid-cols-3 gap-5">
        <div className="card p-6 md:col-span-2">
          <h2 className="font-bold mb-4">Charges</h2>
          <Table className="table">
            <thead>
              <tr>
                {[
                  'Description',
                  'Quantity',
                  'Original unit / amount',
                  'FX reference',
                  'Converted amount',
                ].map((x) => (
                  <th key={x}>{x}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {q.data?.lines?.map((l: any) => (
                <tr key={l.id}>
                  <td>{l.description}</td>
                  <td>{l.quantity}</td>
                  <td>
                    {l.originalCurrency || q.data?.currency}{' '}
                    {fmt(l.originalUnitPrice ?? l.unitPrice)}
                    {' / '}
                    {fmt(
                      Number(l.quantity) *
                        Number(l.originalUnitPrice ?? l.unitPrice),
                    )}
                  </td>
                  <td>
                    {l.fxSource === 'ECB_REFERENCE'
                      ? 'ECB reference'
                      : l.fxSource || '—'}
                    {l.fxReferenceRate
                      ? ` · reference ${l.fxReferenceRate}`
                      : ''}
                    {l.fxRate ? ` · effective ${l.fxRate}` : ''}
                    {l.fxObservationDate ? ` · ${l.fxObservationDate}` : ''}
                    {l.fxBufferPercent && Number(l.fxBufferPercent) !== 0
                      ? ` · ${l.fxBufferPercent}% buffer`
                      : ''}
                  </td>
                  <td>
                    {q.data?.currency} {fmt(l.amount)}
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
          <p className="text-right text-xl font-bold mt-5">
            {q.data?.currency} {fmt(q.data?.totalAmount)}
          </p>
        </div>
        <div className="card p-6">
          <h2 className="font-bold mb-4">Quotation details</h2>
          <p>Valid until: {date(q.data?.validUntil)}</p>
          <p>Transit time: {q.data?.quotation?.transitTimeText || '—'}</p>
          <p className="mt-4 text-sm text-slate-500">{detailRoute.label}</p>
          <p className="text-sm">POL: {detailRoute.loading}</p>
          <p className="text-sm">POD: {detailRoute.discharge}</p>
          {detailRoute.finalPlace && (
            <p className="text-sm">FPOD: {detailRoute.finalPlace}</p>
          )}
        </div>
      </div>
      <div className="card p-6 mt-5">
        <h2 className="font-bold mb-3">Terms and Conditions</h2>
        <p className="whitespace-pre-wrap text-slate-600">
          {q.data?.quotation?.termsAndConditions}
        </p>
      </div>
      {compose && (
        <div className="modal-backdrop">
          <div className="modal">
            <div className="flex justify-between">
              <div>
                <h2 className="text-xl font-bold">Email quotation</h2>
                <p className="text-sm text-slate-500">
                  Review your message before sending.
                </p>
              </div>
              <button onClick={() => setCompose(null)}>✕</button>
            </div>
            <div className="space-y-4 mt-6">
              {[
                ['to', 'To'],
                ['cc', 'CC'],
                ['subject', 'Subject'],
              ].map(([k, l]) => (
                <label key={k} className="block">
                  <span className="label">{l}</span>
                  <input
                    className="field"
                    value={compose[k]}
                    onChange={(e) =>
                      setCompose({ ...compose, [k]: e.target.value })
                    }
                  />
                </label>
              ))}
              <label className="block">
                <span className="label">Body</span>
                <textarea
                  rows={13}
                  className="field"
                  value={compose.body}
                  onChange={(e) =>
                    setCompose({ ...compose, body: e.target.value })
                  }
                />
              </label>
              <div className="badge">📎 {compose.attachment.filename}</div>
              <label className="block">
                <span className="label">Conversation</span>
                <SearchableSelect ariaLabel="Conversation" value={replySource} onChange={setReplySource} placeholder="Compose new email" allowClear options={mailChoices.map((message) => ({ value: message.id, label: `Reply to ${message.fromEmail}: ${message.subject}` }))} />
              </label>
              {err && <p className="text-red-600">{err}</p>}
              <div className="flex justify-end gap-2">
                <button
                  className="btn secondary"
                  onClick={() => setCompose(null)}
                >
                  Cancel
                </button>
                <button
                  className="btn primary"
                  disabled={sending || !compose.to}
                  onClick={openMailDraft}
                >
                  {sending ? 'Opening…' : 'Review and send in Mail'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </State>
  );
}
