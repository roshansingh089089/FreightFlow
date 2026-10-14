import { Sparkles } from 'lucide-react';
import type { ReactNode } from 'react';
import { Button } from '../../shared/components/PageElements';
import type { MailDetail } from './CustomerMailWorkspace';
import type { MailAttachment } from './mailApi';

export function ReplyActionButton({
  all,
  messageId,
  connected,
  busy,
  onReply,
}: {
  all: boolean;
  messageId: string | null;
  connected: boolean;
  busy: boolean;
  onReply: (messageId: string, all: boolean) => void;
}) {
  return (
    <Button
      variant="secondary"
      className="cm-mail-action"
      disabled={!messageId || !connected || busy}
      title={!messageId ? 'Select a message to reply' : undefined}
      onClick={() => messageId && onReply(messageId, all)}
    >
      {all ? 'Reply all' : 'Reply'}
    </Button>
  );
}

export function MailReadingActions({
  detail,
  variant,
  connected,
  replyBusy,
  summaryBusy,
  onReply,
  onSummarize,
}: {
  detail: MailDetail;
  variant: 'full' | 'inline';
  connected: boolean;
  replyBusy: boolean;
  summaryBusy: boolean;
  onReply: (messageId: string, all: boolean) => void;
  onSummarize: () => void;
}) {
  return (
    <div
      className={
        variant === 'inline' ? 'cm-inline-actions' : 'cm-reader-actions'
      }
    >
      <ReplyActionButton
        all={false}
        messageId={detail.id}
        connected={connected}
        busy={replyBusy}
        onReply={onReply}
      />
      <ReplyActionButton
        all
        messageId={detail.id}
        connected={connected}
        busy={replyBusy}
        onReply={onReply}
      />
      {variant === 'inline' && (
        <button
          type="button"
          disabled={summaryBusy || !connected}
          onClick={onSummarize}
        >
          <Sparkles size={14} /> {summaryBusy ? 'Summarizing…' : 'Summarize'}
        </button>
      )}
    </div>
  );
}

export function MailMessageContent({
  detail,
  banner,
  attachments,
  onDownload,
}: {
  detail: MailDetail;
  banner: ReactNode;
  attachments: MailAttachment[] | undefined;
  onDownload: (id: string, filename: string) => void;
}) {
  return (
    <>
      {banner}
      {!!attachments?.length && (
        <div className="cm-attachments">
          <h3>Attachments</h3>
          {attachments.map((attachment) => (
            <button
              type="button"
              key={attachment.id}
              onClick={() => onDownload(attachment.id, attachment.filename)}
            >
              {attachment.filename} ({Math.ceil(attachment.sizeBytes / 1024)}{' '}
              KB)
            </button>
          ))}
        </div>
      )}
      <div className="cm-body">
        {detail.bodyText || '(No plain-text body available)'}
      </div>
    </>
  );
}
