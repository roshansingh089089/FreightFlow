import { describe, expect, it } from 'vitest';
import { quotationActions } from './quotationActions';

describe('quotation actions', () => {
  it('offers only implemented actions for the supported statuses', () => {
    expect(quotationActions('DRAFT', 'SALES')).toMatchObject({
      download: true,
      continueDraft: true,
      prepareEmail: false,
      viewInMail: false,
      revise: false,
      convertToShipment: false,
    });
    expect(quotationActions('READY_TO_SEND', 'SALES')).toMatchObject({
      continueDraft: false,
      prepareEmail: true,
    });
    expect(quotationActions('SENT', 'SALES')).toMatchObject({
      continueDraft: false,
      prepareEmail: false,
      viewInMail: true,
    });
  });

  it('hides update and mail actions from read only users and unsupported statuses', () => {
    expect(quotationActions('DRAFT', 'READ_ONLY').continueDraft).toBe(false);
    expect(quotationActions('SENT', 'FINANCE').viewInMail).toBe(false);
    for (const status of ['NEGOTIATING', 'ACCEPTED', 'REVISED', 'EXPIRED']) {
      expect(quotationActions(status, 'ADMIN')).toMatchObject({
        continueDraft: false,
        prepareEmail: false,
        viewInMail: false,
        revise: false,
        convertToShipment: false,
      });
    }
  });
});
