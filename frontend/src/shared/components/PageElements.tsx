import { FolderOpen } from 'lucide-react';
import React from 'react';

export const Button = React.forwardRef<
  HTMLButtonElement,
  React.ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: 'primary' | 'secondary' | 'quiet';
  }
>(function Button(
  { variant = 'secondary', className = '', type = 'button', ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={`ui-button ui-button-${variant} ${className}`.trim()}
      {...props}
    />
  );
});

export const Input = React.forwardRef<
  HTMLInputElement,
  React.InputHTMLAttributes<HTMLInputElement>
>(function Input({ className = '', ...props }, ref) {
  return (
    <input ref={ref} className={`ui-input ${className}`.trim()} {...props} />
  );
});

export function Card({
  children,
  className = '',
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`ui-card ${className}`.trim()}>{children}</section>
  );
}

export function Tabs<T extends string>({
  options,
  value,
  onChange,
  label,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  label: string;
}) {
  return (
    <div className="ui-tabs" role="group" aria-label={label}>
      {options.map((option) => (
        <Button
          key={option.value}
          variant="quiet"
          className={value === option.value ? 'active' : ''}
          aria-pressed={value === option.value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </Button>
      ))}
    </div>
  );
}

export function Badge({
  children,
  tone = 'neutral',
  className = '',
}: {
  children: React.ReactNode;
  tone?: 'neutral' | 'success' | 'warning' | 'danger';
  className?: string;
}) {
  return (
    <span className={`ui-badge ui-badge-${tone} ${className}`.trim()}>
      {children}
    </span>
  );
}

export function Table({
  children,
  className = '',
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className="ui-table-scroll">
      <table className={`ui-table ${className}`.trim()}>{children}</table>
    </div>
  );
}
export function PageContainer({
  children,
  variant = 'standard',
}: {
  children: React.ReactNode;
  variant?: 'standard' | 'dashboard' | 'workspace';
}) {
  return <div className={`ff-page ff-page-${variant}`}>{children}</div>;
}
export function PageContent({ children }: { children: React.ReactNode }) {
  return <div className="ff-page-content">{children}</div>;
}
export function PageActions({ children }: { children: React.ReactNode }) {
  return <div className="page-heading-action">{children}</div>;
}
export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}) {
  return (
    <header className="page-heading">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {action && <PageActions>{action}</PageActions>}
    </header>
  );
}
export function Field({
  form,
  name,
  label,
  type = 'text',
  placeholder,
}: {
  form: any;
  name: string;
  label: string;
  type?: string;
  placeholder?: string;
}) {
  return (
    <label className="block">
      <span className="label">{label}</span>
      <Input
        className="field"
        type={type}
        placeholder={placeholder}
        {...form.register(name)}
      />
      {form.formState.errors[name] && (
        <small className="text-red-600">
          {String(form.formState.errors[name].message)}
        </small>
      )}
    </label>
  );
}
export function Notice({ text }: { text: string }) {
  return (
    <div className="state-box">
      <div className="state-icon">
        <FolderOpen size={22} />
      </div>
      <strong>{text}</strong>
    </div>
  );
}
export function State({
  query,
  children,
}: {
  query: any;
  children: React.ReactNode;
}) {
  return query.isLoading ? (
    <div className="skeleton-stack">
      <div className="skeleton" />
      <div className="skeleton" />
      <div className="skeleton" />
    </div>
  ) : query.isError ? (
    <Notice text={query.error.message} />
  ) : (
    <>{children}</>
  );
}
export function Heading({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}) {
  return <PageHeader title={title} subtitle={subtitle} action={action} />;
}
export function Empty({
  text,
  action,
  description,
}: {
  text: string;
  action?: React.ReactNode;
  description?: string;
}) {
  return (
    <div className="empty-state">
      <div className="state-icon">
        <FolderOpen size={22} />
      </div>
      <strong>{text}</strong>
      {description && <p>{description}</p>}
      {action && <div>{action}</div>}
    </div>
  );
}
export function Pager({
  data,
  page,
  setPage,
}: {
  data: any;
  page: number;
  setPage: (n: number) => void;
}) {
  return (
    <div className="pager">
      <span>
        Page {page + 1} of {Math.max(1, data.totalPages || 1)}
      </span>
      <div>
        <Button disabled={page === 0} onClick={() => setPage(page - 1)}>
          Previous
        </Button>
        <Button
          disabled={page + 1 >= data.totalPages}
          onClick={() => setPage(page + 1)}
        >
          Next
        </Button>
      </div>
    </div>
  );
}
export function StatusBadge({ value }: { value: string }) {
  const labels: Record<string, string> = {
    PRICING_PENDING: 'Pricing',
    QUOTED: 'Quoted',
    NEW: 'New',
  };
  return (
    <Badge
      tone={
        ['QUOTED', 'ACCEPTED', 'SENT', 'ACTIVE'].includes(value)
          ? 'success'
          : ['PRICING_PENDING', 'FOLLOW_UP'].includes(value)
            ? 'warning'
            : 'neutral'
      }
      className={'status-badge status-' + String(value || '').toLowerCase()}
    >
      {labels[value] || titleCase(value)}
    </Badge>
  );
}
export function titleCase(value: any) {
  return String(value || '—')
    .toLowerCase()
    .split('_')
    .map((x) => x[0]?.toUpperCase() + x.slice(1))
    .join(' ');
}
export function SectionCard({
  title,
  action,
  children,
  className = '',
}: {
  title: string;
  action?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <Card className={'section-card ' + className}>
      <div className="section-card-header">
        <h2>{title}</h2>
        {action}
      </div>
      {children}
    </Card>
  );
}
export function Stepper({
  labels,
  current,
}: {
  labels: string[];
  current: number;
}) {
  return (
    <ol className="stepper" aria-label="Enquiry progress">
      {labels.map((label, index) => (
        <li
          key={label}
          className={
            index === current ? 'current' : index < current ? 'done' : ''
          }
          aria-current={index === current ? 'step' : undefined}
        >
          <span>{index < current ? '✓' : index + 1}</span>
          <b>{label}</b>
        </li>
      ))}
    </ol>
  );
}
