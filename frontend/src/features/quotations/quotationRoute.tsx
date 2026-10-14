import { useQuery } from '@tanstack/react-query';
import type { LocationOption } from '../../shared/components/ReferenceLocationPicker';
import { api } from '../../shared/lib/workspace';

export type RouteLocation = {
  code?: string | null;
  name?: string | null;
};

export function formatQuotationRoute(
  pol: RouteLocation,
  pod: RouteLocation,
  fpod?: RouteLocation | null,
) {
  const loading = pol.name?.trim() || pol.code?.trim() || 'Origin';
  const discharge = pod.name?.trim() || pod.code?.trim() || 'Destination';
  const finalPlace = fpod?.name?.trim() || fpod?.code?.trim() || '';
  const distinctFinalPlace =
    !!fpod?.code &&
    fpod.code.trim().toUpperCase() !== (pod.code || '').trim().toUpperCase();
  return {
    loading,
    discharge,
    finalPlace: distinctFinalPlace ? finalPlace : null,
    label: [
      loading,
      discharge,
      ...(distinctFinalPlace ? [finalPlace] : []),
    ].join(' → '),
  };
}

export function quotationRouteFromShipment(
  shipment: Record<string, any> | null,
) {
  const value = shipment || {};
  return formatQuotationRoute(
    {
      code: value.polCode,
      name: value.portOfLoading || value.airportOfOrigin || value.origin,
    },
    {
      code: value.podCode,
      name:
        value.portOfDischarge ||
        value.airportOfDestination ||
        value.destination,
    },
    { code: value.fpodCode, name: value.fpodName },
  );
}

export function useResolvedFinalPlace(
  code?: string | null,
  name?: string | null,
) {
  const missingName = !!code && (!name || name === code);
  const result = useQuery<LocationOption[]>({
    queryKey: ['reference-location-code', code],
    queryFn: () =>
      api(`/reference/locations?q=${encodeURIComponent(code!)}&mode=ALL`),
    enabled: missingName,
    staleTime: 600_000,
    gcTime: 1_800_000,
  });
  return missingName
    ? result.data?.find(
        (location) => location.code.toUpperCase() === code?.toUpperCase(),
      )?.name ||
        code ||
        ''
    : name || '';
}

export function QuotationRouteLabel({
  shipment,
  compact = false,
}: {
  shipment: Record<string, any> | null;
  compact?: boolean;
}) {
  const finalName = useResolvedFinalPlace(
    shipment?.fpodCode,
    shipment?.fpodName,
  );
  const route = quotationRouteFromShipment({
    ...shipment,
    fpodName: finalName,
  });
  if (compact)
    return (
      <span className="quote-directory-route" title={route.label} tabIndex={0}>
        <span>
          {route.loading} → {route.discharge}
        </span>
        {route.finalPlace && <small>Final: {route.finalPlace}</small>}
      </span>
    );
  return <>{route.label}</>;
}
