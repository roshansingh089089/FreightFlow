export type ComposerRequest = {
  draftId?: string;
  customerId?: string;
  quotationId?: string;
  enquiryId?: string;
};

export function openMailComposer(request: ComposerRequest = {}) {
  window.dispatchEvent(
    new CustomEvent<ComposerRequest>('ff-open-mail-composer', {
      detail: request,
    }),
  );
}
