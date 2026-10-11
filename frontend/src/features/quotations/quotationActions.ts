export function quotationActions(status: string, role?: string) {
  const canEdit = ['ADMIN', 'SALES', 'SALES_MANAGER', 'PRICING'].includes(
    role || '',
  );
  const canSend = ['ADMIN', 'SALES', 'SALES_MANAGER'].includes(role || '');
  const canReadMail = [
    'ADMIN',
    'SALES',
    'SALES_MANAGER',
    'PRICING',
    'OPERATIONS',
  ].includes(role || '');
  return {
    download: true,
    continueDraft: status === 'DRAFT' && canEdit,
    prepareEmail: status === 'READY_TO_SEND' && canSend,
    viewInMail: status === 'SENT' && canReadMail,
    revise: false,
    convertToShipment: false,
  };
}
