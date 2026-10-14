import { useEffect } from 'react';
import type { UseFormReturn } from 'react-hook-form';
import { SearchableSelect, type SelectOption } from './SearchableSelect';

type Props = {
  form: UseFormReturn<any>;
  name: string;
  options: readonly SelectOption[];
  label: string;
  placeholder?: string;
  disabled?: boolean;
  loading?: boolean;
  required?: boolean;
  allowClear?: boolean;
  onChange?: (value: string) => void;
};

export function FormSearchableSelect({
  form,
  name,
  options,
  label,
  placeholder,
  disabled,
  loading,
  required,
  allowClear,
  onChange,
}: Props) {
  useEffect(() => {
    form.register(
      name,
      required ? { required: `Select ${label.toLowerCase()}` } : undefined,
    );
  }, [form, name, required, label]);
  const value = String(form.watch(name) ?? '');
  const error = form.formState.errors[name];
  return (
    <label className="block">
      <span className="label">
        {label}
        {required ? ' *' : ''}
      </span>
      <SearchableSelect
        ariaLabel={label}
        options={options}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        loading={loading}
        error={!!error}
        allowClear={allowClear}
        onChange={(next) => {
          form.setValue(name, next, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
          });
          onChange?.(next);
        }}
      />
      {error && (
        <small className="enquiry-field-error" role="alert">
          {String(error.message || 'Select a value')}
        </small>
      )}
    </label>
  );
}
