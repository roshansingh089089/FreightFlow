import { describe, expect, it } from 'vitest';
import { quotationRouteFromShipment } from './quotationRoute';

const shipment = {
  polCode: 'VNOCL',
  portOfLoading: 'Cat Lai Oil Port',
  podCode: 'USNYC',
  portOfDischarge: 'New York',
};

describe('quotation route display', () => {
  it('shows only loading and discharge when no final place is selected', () => {
    expect(quotationRouteFromShipment(shipment).label).toBe(
      'Cat Lai Oil Port → New York',
    );
  });

  it('shows the selected final place name as a third stop', () => {
    expect(
      quotationRouteFromShipment({
        ...shipment,
        fpodCode: 'USCA2',
        fpodName: 'Final Delivery City',
      }).label,
    ).toBe('Cat Lai Oil Port → New York → Final Delivery City');
  });

  it('compares location codes and omits a duplicate final stop', () => {
    expect(
      quotationRouteFromShipment({
        ...shipment,
        fpodCode: 'usnyc',
        fpodName: 'New York City',
      }).label,
    ).toBe('Cat Lai Oil Port → New York');
  });

  it('uses the code only when the verified final place name is unavailable', () => {
    expect(
      quotationRouteFromShipment({ ...shipment, fpodCode: 'USCA2' }).label,
    ).toBe('Cat Lai Oil Port → New York → USCA2');
  });
});
