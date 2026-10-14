import { FileText, Sparkles } from 'lucide-react';

const callouts = [
  {
    icon: Sparkles,
    title: '2. AI extraction',
    detail: 'Shipment details identified and ready for review',
    className: 'extraction',
  },
  {
    icon: FileText,
    title: '3. Quotation draft',
    detail: 'Generate editable quotation in minutes',
    className: 'quotation',
  },
] as const;

export function HeroWorkflowCallouts() {
  return (
    <div className="ff-hero-callouts" aria-hidden="true">
      {callouts.map(({ icon: Icon, title, detail, className }) => (
        <div
          className={'ff-hero-callout ff-hero-callout-' + className}
          key={className}
        >
          <span className="ff-hero-callout-icon">
            <Icon size={21} strokeWidth={2} />
          </span>
          <span>
            <strong>{title}</strong>
            <small>{detail}</small>
          </span>
        </div>
      ))}
    </div>
  );
}
