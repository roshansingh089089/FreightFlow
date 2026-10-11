import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Maximize2, Minus, Paperclip, Sparkles, Trash2, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { apiFetch } from '../../api';
import { Button, Input } from '../../shared/components/PageElements';
import {
  MailApiError,
  mailApi,
  type Address,
  type DraftInput,
} from './mailApi';

const schema = z.object({
  to: z.string().min(1, 'Add at least one recipient'),
  cc: z.string(),
  bcc: z.string(),
  subject: z.string().trim().min(1, 'Subject is required').max(998),
  bodyText: z.string().trim().min(1, 'Message is required'),
});
type Fields = z.infer<typeof schema>;
const parseAddresses = (text: string): Address[] =>
  text
    .split(/[;,\n]+/)
    .map((value) => value.trim())
    .filter(Boolean)
    .map((email) => ({ email }));
const validEmail = (value: string) => z.email().safeParse(value).success;
const join = (items: Address[]) => items.map((item) => item.email).join(', ');

export default function EmailComposer() {
  const nav = useNavigate(),
    qc = useQueryClient(),
    [params] = useSearchParams();
  const customerId = params.get('customerId'),
    quotationId = params.get('quotationId'),
    enquiryId = params.get('enquiryId'),
    draftId = params.get('draftId');
  const [savedId, setSavedId] = useState(draftId),
    [attachments, setAttachments] = useState<
      { id: string; filename: string }[]
    >([]),
    [error, setError] = useState(''),
    [sendErrorCode, setSendErrorCode] = useState<string | null>(null),
    [notice, setNotice] = useState(''),
    [confirmUnknown, setConfirmUnknown] = useState(false),
    [queued, setQueued] = useState(false),
    [submittedThreadId, setSubmittedThreadId] = useState<string | null>(null);
  const [aiBusy, setAiBusy] = useState(false);
  const [aiInstruction, setAiInstruction] = useState('');
  const [aiError, setAiError] = useState('');
  const [aiAction, setAiAction] = useState<
    'DRAFT_EMAIL' | 'REWRITE' | 'IMPROVE_TONE' | 'ASK_MISSING'
  >('DRAFT_EMAIL');
  const [aiUndoHistory, setAiUndoHistory] = useState<string[]>([]);
  const [suggestedSubject, setSuggestedSubject] = useState('');
  const [showCopyFields, setShowCopyFields] = useState(false);
  const [showAiOptions, setShowAiOptions] = useState(false);
  const [minimized, setMinimized] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [saveStatus, setSaveStatus] = useState<
    'idle' | 'saving' | 'saved' | 'error'
  >('idle');
  const initializedDraftId = useRef<string | null>(null);
  const savedIdRef = useRef<string | null>(draftId);
  const savedDraftRef = useRef<typeof draft.data>(undefined);
  const lastSavedRef = useRef('');
  const saveQueueRef = useRef<Promise<unknown>>(Promise.resolve());
  const sendingRef = useRef(false);
  const aiRequestRef = useRef(0);
  const form = useForm<Fields>({
    resolver: zodResolver(schema),
    defaultValues: { to: '', cc: '', bcc: '', subject: '', bodyText: '' },
  });
  const customer = useQuery({
    queryKey: ['composer-customer', customerId],
    queryFn: async () => {
      const response = await apiFetch(`/customers/${customerId}`);
      if (!response.ok) throw new Error('Could not load customer');
      return response.json();
    },
    enabled: !!customerId,
  });
  const draft = useQuery({
    queryKey: ['mail-draft', draftId],
    queryFn: () => mailApi.draft(draftId!),
    enabled: !!draftId,
  });
  useEffect(() => {
    if (!draft.data || initializedDraftId.current === draft.data.id) return;
    aiRequestRef.current += 1;
    setAiUndoHistory([]);
    setSuggestedSubject('');
    initializedDraftId.current = draft.data.id;
    form.reset({
      to: join(draft.data.to),
      cc: join(draft.data.cc),
      bcc: join(draft.data.bcc),
      subject: draft.data.subject,
      bodyText: draft.data.bodyText,
    });
    setAttachments(
      draft.data.attachmentIds.map((id) => ({
        id,
        filename: `Attachment ${id.slice(0, 8)}`,
      })),
    );
    setSavedId(draft.data.id);
    savedIdRef.current = draft.data.id;
    savedDraftRef.current = draft.data;
    lastSavedRef.current = JSON.stringify({
      values: {
        to: join(draft.data.to),
        cc: join(draft.data.cc),
        bcc: join(draft.data.bcc),
        subject: draft.data.subject,
        bodyText: draft.data.bodyText,
      },
      attachments: draft.data.attachmentIds,
    });
    setShowCopyFields(Boolean(draft.data.cc.length || draft.data.bcc.length));
  }, [draft.data]);
  useEffect(() => {
    if (!customer.data || draftId) return;
    const primary = customer.data.primaryContact?.email;
    if (primary && !form.getValues('to')) form.setValue('to', primary);
  }, [customer.data, draftId]);
  const autoCc: Address[] = [
    ...(customer.data?.contacts || [])
      .filter((contact: any) => contact.autoCc && contact.active)
      .map((contact: any) => ({ email: contact.email })),
    ...(customer.data?.extraCc || [])
      .filter((extra: any) => extra.active)
      .map((extra: any) => ({ email: extra.email })),
  ];
  const unknown = customer.data
    ? parseAddresses(
        form.watch('to') + ';' + form.watch('cc') + ';' + form.watch('bcc'),
      ).filter(
        (address) =>
          ![
            ...(customer.data?.contacts || []).map((c: any) =>
              c.email.toLowerCase(),
            ),
            ...autoCc.map((c) => c.email.toLowerCase()),
          ].includes(address.email.toLowerCase()),
      )
    : [];
  const save = useMutation({
    mutationFn: (values: Fields) => {
      const operation = saveQueueRef.current
        .catch(() => undefined)
        .then(async () => {
          const to = parseAddresses(values.to),
            cc = parseAddresses(values.cc),
            bcc = parseAddresses(values.bcc);
          const all = [...to, ...cc, ...bcc];
          if (all.some((address) => !validEmail(address.email)))
            throw new Error('Enter valid email addresses separated by commas.');
          if (
            new Set(all.map((address) => address.email.toLowerCase())).size !==
            all.length
          )
            throw new Error(
              'Each address can appear only once across To, CC and BCC.',
            );
          const input: DraftInput = {
            customerId: customerId || draft.data?.customerId || null,
            quotationId: quotationId || draft.data?.quotationId || null,
            to,
            cc,
            bcc,
            subject: values.subject,
            bodyText: values.bodyText,
            attachmentIds: attachments.map((a) => a.id),
          };
          const fingerprint = JSON.stringify({
            values,
            attachments: input.attachmentIds,
          });
          if (fingerprint === lastSavedRef.current && savedDraftRef.current)
            return savedDraftRef.current;
          setSaveStatus('saving');
          const result = savedIdRef.current
            ? await mailApi.update(savedIdRef.current, input)
            : await mailApi.create(input);
          savedIdRef.current = result.id;
          savedDraftRef.current = result;
          lastSavedRef.current = fingerprint;
          return result;
        });
      saveQueueRef.current = operation;
      return operation;
    },
    onSuccess: (result) => {
      setSavedId(result.id);
      setError('');
      setSendErrorCode(null);
      setSaveStatus('saved');
      qc.invalidateQueries({ queryKey: ['mail-drafts'] });
    },
    onError: (cause: Error) => {
      setSaveStatus('error');
      setError(cause.message);
    },
  });
  useEffect(() => {
    if (queued || (draftId && !draft.data)) return;
    let timer: number | undefined;
    const subscription = form.watch(() => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => {
        if (sendingRef.current) return;
        const values = form.getValues();
        if (
          !values.to &&
          !values.subject &&
          !values.bodyText &&
          !savedIdRef.current
        )
          return;
        if (
          [
            ...parseAddresses(values.to),
            ...parseAddresses(values.cc),
            ...parseAddresses(values.bcc),
          ].some((address) => !validEmail(address.email))
        )
          return;
        save.mutate(values);
      }, 1200);
    });
    return () => {
      window.clearTimeout(timer);
      subscription.unsubscribe();
    };
  }, [draft.data, draftId, queued]);
  useEffect(() => {
    if (!savedIdRef.current || queued || (draftId && !draft.data)) return;
    const timer = window.setTimeout(() => {
      if (!sendingRef.current) save.mutate(form.getValues());
    }, 1200);
    return () => window.clearTimeout(timer);
  }, [attachments]);
  const send = useMutation({
    mutationFn: async (values: Fields) => {
      if (unknown.length > 0 && !confirmUnknown)
        throw new Error('Confirm the external recipients before sending.');
      aiRequestRef.current += 1;
      setAiBusy(false);
      sendingRef.current = true;
      const result = await save.mutateAsync(values);
      return mailApi.send(result.id, crypto.randomUUID());
    },
    onSuccess: (result) => {
      setSendErrorCode(null);
      setQueued(true);
      setSubmittedThreadId(result.threadId);
      setNotice(
        `Message ${result.status.toLowerCase()}. Check Stored mail for delivery status.`,
      );
      qc.invalidateQueries({ queryKey: ['mail-drafts'] });
      if (customerId)
        qc.invalidateQueries({ queryKey: ['customer-sent-mail', customerId] });
      if (result.threadId) {
        qc.invalidateQueries({
          queryKey: ['mail-thread-messages', result.threadId],
        });
        qc.invalidateQueries({ queryKey: ['mail-thread', result.threadId] });
      }
    },
    onError: (cause: Error) => {
      sendingRef.current = false;
      const code = cause instanceof MailApiError ? cause.code : undefined;
      setSendErrorCode(code ?? null);
      setError(
        code === 'MAIL_NOT_CONNECTED'
          ? 'Connect an IMAP/SMTP mailbox before sending this draft.'
          : code === 'MAILBOX_CHANGED'
            ? 'This draft belongs to a different mailbox. Reconnect its original mailbox to send it.'
            : code === 'MAIL_SEND_UNAVAILABLE'
              ? 'The connected provider cannot send through this flow. Connect an IMAP/SMTP mailbox.'
              : cause.message,
      );
    },
  });
  const upload = async (file: File) => {
    try {
      const result = await mailApi.upload(file);
      setAttachments((items) => [...items, result]);
      setNotice(`${file.name} attached`);
    } catch (cause) {
      setError((cause as Error).message);
    }
  };
  const close = async () => {
    const values = form.getValues();
    if (values.to || values.subject || values.bodyText || savedIdRef.current) {
      try {
        await save.mutateAsync(values);
      } catch {
        return;
      }
    }
    nav(customerId ? `/customers/${customerId}` : '/mail');
  };
  const assist = async (
    action: 'DRAFT_EMAIL' | 'REWRITE' | 'IMPROVE_TONE' | 'ASK_MISSING',
  ) => {
    if (aiBusy) return;
    setAiAction(action);
    setAiBusy(true);
    setAiError('');
    const requestId = ++aiRequestRef.current;
    const originalBody = form.getValues('bodyText');
    const originalSubject = form.getValues('subject');
    const originalDraftId = savedIdRef.current;
    try {
      const composeMode = draft.data?.composeMode || 'NEW';
      let threadContext = '';
      if (composeMode !== 'NEW' && draft.data?.threadId) {
        const history = await mailApi.threadMessages(draft.data.threadId);
        const parentId =
          draft.data.replyToMessageId || draft.data.replyToOutboundMessageId;
        const parentIndex = history.findIndex(
          (message) => message.id === parentId,
        );
        const relevant = (
          parentIndex < 0 ? history : history.slice(0, parentIndex + 1)
        ).slice(-6);
        threadContext = relevant
          .map(
            (message) =>
              `${message.direction} from ${message.fromAddress}; subject: ${message.subject}\n${message.bodyText}`,
          )
          .join('\n\n');
      }
      const response = await apiFetch('/ai/mail/draft', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action,
          context: threadContext.slice(-10000),
          currentDraft: originalBody,
          employeeRequest: aiInstruction.trim(),
          subject: originalSubject,
          composeMode,
          customerId: customerId || draft.data?.customerId || null,
          enquiryId,
          quotationId: quotationId || draft.data?.quotationId || null,
        }),
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.message || 'AI draft failed');
      if (!result.draft || typeof result.draft !== 'string')
        throw new Error('AI returned an empty draft. Try again.');
      if (requestId !== aiRequestRef.current) return;
      if (
        form.getValues('bodyText') !== originalBody ||
        form.getValues('subject') !== originalSubject ||
        (originalDraftId !== null && savedIdRef.current !== originalDraftId)
      ) {
        setAiError(
          'The draft changed while AI was writing. Run the action again to use the latest text.',
        );
        return;
      }
      setAiUndoHistory((history) => [...history, originalBody]);
      form.setValue('bodyText', result.draft, { shouldDirty: true });
      if (composeMode === 'NEW' && typeof result.subject === 'string') {
        if (!originalSubject.trim()) {
          form.setValue('subject', result.subject, { shouldDirty: true });
          setSuggestedSubject('');
        } else if (
          result.subject.trim() &&
          result.subject !== originalSubject
        ) {
          setSuggestedSubject(result.subject);
        }
      }
      setNotice('AI updated the message. Review and edit it before sending.');
    } catch (cause) {
      if (requestId === aiRequestRef.current)
        setAiError((cause as Error).message);
    } finally {
      if (requestId === aiRequestRef.current) setAiBusy(false);
    }
  };
  const revertAiEdit = () => {
    const previousBody = aiUndoHistory.at(-1);
    if (previousBody === undefined) return;
    form.setValue('bodyText', previousBody, { shouldDirty: true });
    setAiUndoHistory((history) => history.slice(0, -1));
    setNotice('Previous message restored.');
  };
  return (
    <div
      className={
        'email-composer email-composer-compact' +
        (expanded ? ' is-expanded' : '') +
        (minimized ? ' is-minimized' : '')
      }
      role="dialog"
      aria-label="Email composer"
      aria-modal="false"
    >
      <header className="email-composer-header">
        <div className="email-composer-heading">
          <h1>
            {draft.data?.composeMode === 'REPLY_ALL'
              ? 'Reply all'
              : draft.data?.composeMode === 'REPLY'
                ? 'Reply'
                : 'New message'}
          </h1>
          {customer.data?.companyName && (
            <span>{customer.data.companyName}</span>
          )}
        </div>
        <nav aria-label="Composer navigation">
          <button
            type="button"
            onClick={() => setMinimized((value) => !value)}
            aria-label={minimized ? 'Restore composer' : 'Minimize composer'}
            title={minimized ? 'Restore composer' : 'Minimize composer'}
          >
            <Minus size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => {
              setExpanded((value) => !value);
              setMinimized(false);
            }}
            aria-label={expanded ? 'Restore composer size' : 'Expand composer'}
            title={expanded ? 'Restore composer size' : 'Expand composer'}
          >
            <Maximize2 size={15} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => void close()}
            aria-label="Close composer"
            title="Close composer"
          >
            <X size={17} aria-hidden="true" />
          </button>
        </nav>
      </header>
      {!minimized && (
        <form
          onSubmit={form.handleSubmit((values) => send.mutate(values))}
          onDragOver={(event) => {
            if (event.dataTransfer.types.includes('Files'))
              event.preventDefault();
          }}
          onDrop={(event) => {
            if (event.dataTransfer.files.length) {
              event.preventDefault();
              Array.from(event.dataTransfer.files).forEach(
                (file) => void upload(file),
              );
            }
          }}
        >
          <div className="email-composer-fields">
            {draft.isLoading && <p role="status">Loading draft…</p>}
            {draft.error && (
              <p role="alert">{(draft.error as Error).message}</p>
            )}
            {draft.data?.threadingWarning && (
              <p role="status" className="email-warning">
                {draft.data.threadingWarning}
              </p>
            )}
            <div className="email-address-row">
              <label htmlFor="email-to">To</label>
              <Input
                id="email-to"
                {...form.register('to')}
                placeholder="name@example.com, another@example.com"
                aria-invalid={!!form.formState.errors.to}
              />
              <Button
                variant="quiet"
                aria-expanded={showCopyFields}
                onClick={() => setShowCopyFields((value) => !value)}
              >
                Cc/Bcc
              </Button>
            </div>
            {form.formState.errors.to && (
              <small role="alert">{form.formState.errors.to.message}</small>
            )}
            {showCopyFields && (
              <div className="email-copy-fields">
                <div className="email-address-row">
                  <label htmlFor="email-cc">Cc</label>
                  <Input
                    id="email-cc"
                    {...form.register('cc')}
                    placeholder="Optional CC addresses"
                  />
                </div>
                <div className="email-address-row">
                  <label htmlFor="email-bcc">Bcc</label>
                  <Input
                    id="email-bcc"
                    {...form.register('bcc')}
                    placeholder="Optional BCC addresses"
                  />
                </div>
              </div>
            )}
            {autoCc.length > 0 && (
              <p className="email-auto-cc">
                Automatic CC when sent:{' '}
                {autoCc.map((item) => item.email).join(', ')}
              </p>
            )}
            {unknown.length > 0 && (
              <div className="email-warning">
                <p>
                  Review external recipients:{' '}
                  {unknown.map((item) => item.email).join(', ')}
                </p>
                <label>
                  <input
                    type="checkbox"
                    checked={confirmUnknown}
                    onChange={(event) =>
                      setConfirmUnknown(event.target.checked)
                    }
                  />{' '}
                  I confirm these recipients
                </label>
              </div>
            )}
            <div className="email-address-row">
              <label htmlFor="email-subject">Subject</label>
              <Input
                id="email-subject"
                {...form.register('subject')}
                aria-invalid={!!form.formState.errors.subject}
              />
            </div>
            {form.formState.errors.subject && (
              <small role="alert">
                {form.formState.errors.subject.message}
              </small>
            )}
          </div>
          <section className="email-ai-toolbar" aria-label="AI writing tools">
            <div className="email-ai-toolbar-top">
              <Button
                variant="quiet"
                className="email-ai-trigger"
                aria-expanded={showAiOptions}
                onClick={() => setShowAiOptions((value) => !value)}
                disabled={!!draftId && !draft.data}
              >
                <Sparkles size={15} aria-hidden="true" />
                AI Assist
              </Button>
              {aiUndoHistory.length > 0 && (
                <Button type="button" variant="quiet" onClick={revertAiEdit}>
                  Revert AI edit
                </Button>
              )}
            </div>
            {showAiOptions && (
              <div className="email-ai-assist" aria-label="AI Assist panel">
                <Input
                  aria-label="Instructions for AI draft"
                  value={aiInstruction}
                  maxLength={500}
                  onChange={(event) => setAiInstruction(event.target.value)}
                  placeholder="What should this email say? Add a goal or context."
                />
                <div className="email-ai-actions">
                  {(
                    [
                      ['DRAFT_EMAIL', 'Draft Email'],
                      ['REWRITE', 'Rewrite'],
                      ['IMPROVE_TONE', 'Improve Tone'],
                      ['ASK_MISSING', 'Ask for Missing Shipment Details'],
                    ] as const
                  ).map(([action, label]) => (
                    <Button
                      key={action}
                      type="button"
                      variant="quiet"
                      disabled={
                        aiBusy ||
                        (!!draftId && !draft.data) ||
                        ((action === 'REWRITE' || action === 'IMPROVE_TONE') &&
                          !form.watch('bodyText')?.trim())
                      }
                      onClick={() => void assist(action)}
                    >
                      {aiBusy && aiAction === action ? `${label}…` : label}
                    </Button>
                  ))}
                </div>
                {suggestedSubject && (
                  <div className="email-ai-subject">
                    <span>Suggested subject: {suggestedSubject}</span>
                    <Button
                      type="button"
                      variant="quiet"
                      onClick={() => {
                        form.setValue('subject', suggestedSubject, {
                          shouldDirty: true,
                        });
                        setSuggestedSubject('');
                      }}
                    >
                      Use suggested subject
                    </Button>
                  </div>
                )}
                {aiError && (
                  <div className="email-ai-error" role="alert">
                    <span>{aiError}</span>
                    <Button type="button" onClick={() => void assist(aiAction)}>
                      Retry
                    </Button>
                  </div>
                )}
              </div>
            )}
          </section>
          <div className="email-editor">
            <label htmlFor="email-body" className="sr-only">
              Message
            </label>
            <textarea
              id="email-body"
              {...form.register('bodyText')}
              placeholder="Write your message…"
              aria-invalid={!!form.formState.errors.bodyText}
            />
          </div>
          <div className="email-composer-extras">
            {form.formState.errors.bodyText && (
              <small role="alert">
                {form.formState.errors.bodyText.message}
              </small>
            )}
            {attachments.length > 0 && (
              <ul className="email-attachments">
                {attachments.map((file) => (
                  <li key={file.id}>
                    {file.filename}{' '}
                    <button
                      type="button"
                      onClick={() =>
                        setAttachments((items) =>
                          items.filter((item) => item.id !== file.id),
                        )
                      }
                    >
                      Remove
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {error && (
              <div className="email-error" role="alert">
                <p>{error}</p>
                {(sendErrorCode === 'MAIL_NOT_CONNECTED' ||
                  sendErrorCode === 'MAILBOX_CHANGED' ||
                  sendErrorCode === 'MAIL_SEND_UNAVAILABLE') && (
                  <Link to="/settings">Open mailbox settings</Link>
                )}
              </div>
            )}
            {notice && <p role="status">{notice}</p>}
            {queued && submittedThreadId && (
              <Link to={`/mail/threads/${submittedThreadId}`}>
                View conversation
              </Link>
            )}
          </div>
          <footer className="email-composer-footer">
            <Button
              variant="primary"
              type="submit"
              disabled={
                save.isPending ||
                send.isPending ||
                queued ||
                (unknown.length > 0 && !confirmUnknown)
              }
            >
              {send.isPending ? 'Queuing…' : queued ? 'Queued' : 'Send'}
            </Button>
            <Button
              onClick={form.handleSubmit((values) => save.mutate(values))}
              disabled={save.isPending || send.isPending || queued}
            >
              Save draft
            </Button>
            <span className="email-save-status" role="status">
              {saveStatus === 'saving'
                ? 'Saving…'
                : saveStatus === 'saved'
                  ? 'Saved'
                  : saveStatus === 'error'
                    ? 'Save failed'
                    : ''}
            </span>
            <label className="email-file ui-button ui-button-secondary">
              <Paperclip size={15} aria-hidden="true" />
              Attach file
              <input
                type="file"
                accept=".pdf,.png,.jpg,.jpeg,.txt"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void upload(file);
                  event.target.value = '';
                }}
              />
            </label>
            {savedId && !queued && (
              <Button
                variant="quiet"
                className="email-discard"
                onClick={async () => {
                  await mailApi.discard(savedId);
                  nav('/mail/drafts');
                }}
              >
                <Trash2 size={15} aria-hidden="true" />
                Discard
              </Button>
            )}
          </footer>
        </form>
      )}
    </div>
  );
}
