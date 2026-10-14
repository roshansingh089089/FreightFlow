import { Check, ChevronDown, Search, X } from 'lucide-react';
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { CountryFlag } from './CountryFlag';
import './searchable-select.css';

export type SelectOption = {
  value: string;
  label: string;
  keywords?: string;
  disabled?: boolean;
  countryCode?: string;
};

type Props = {
  options: readonly SelectOption[];
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  ariaLabel: string;
  disabled?: boolean;
  loading?: boolean;
  error?: boolean;
  allowClear?: boolean;
  className?: string;
};

export function SearchableSelect({
  options,
  value,
  onChange,
  placeholder = 'Select an option',
  ariaLabel,
  disabled,
  loading,
  error,
  allowClear,
  className = '',
}: Props) {
  const id = useId();
  const button = useRef<HTMLButtonElement>(null);
  const search = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const [box, setBox] = useState({ top: 0, left: 0, width: 0, maxHeight: 280 });
  const chosen = options.find((option) => option.value === value);
  const visible = options.filter((option) =>
    `${option.label} ${option.keywords || ''} ${option.value}`
      .toLocaleLowerCase()
      .includes(query.toLocaleLowerCase()),
  );

  useLayoutEffect(() => {
    if (!open) return;
    const position = () => {
      const rect = button.current?.getBoundingClientRect();
      if (!rect) return;
      const below = window.innerHeight - rect.bottom;
      const above = rect.top;
      const placeAbove = below < 220 && above > below;
      setBox({
        top: placeAbove
          ? Math.max(8, rect.top - Math.min(320, above - 12))
          : rect.bottom + 5,
        left: Math.max(
          8,
          Math.min(
            rect.left,
            window.innerWidth - Math.max(rect.width, 220) - 8,
          ),
        ),
        width: Math.min(Math.max(rect.width, 220), window.innerWidth - 16),
        maxHeight: Math.max(
          120,
          Math.min(310, (placeAbove ? above : below) - 12),
        ),
      });
    };
    position();
    window.addEventListener('resize', position);
    window.addEventListener('scroll', position, true);
    return () => {
      window.removeEventListener('resize', position);
      window.removeEventListener('scroll', position, true);
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const closeOutside = (event: PointerEvent) => {
      const target = event.target as Node;
      if (
        !button.current?.contains(target) &&
        !document.getElementById(`${id}-popup`)?.contains(target)
      )
        setOpen(false);
    };
    document.addEventListener('pointerdown', closeOutside);
    return () => document.removeEventListener('pointerdown', closeOutside);
  }, [id, open]);

  const show = (initial = '') => {
    if (disabled) return;
    setQuery(initial);
    setActive(0);
    setOpen(true);
    window.setTimeout(() => search.current?.focus(), 0);
  };
  const choose = (next: string) => {
    onChange(next);
    setOpen(false);
    setQuery('');
    button.current?.focus();
  };
  const handleKey = (event: React.KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      setOpen(false);
      button.current?.focus();
      return;
    }
    if (event.key === 'Tab' && open) {
      event.preventDefault();
      setOpen(false);
      const popup = document.getElementById(`${id}-popup`);
      const controls = Array.from(
        document.querySelectorAll<HTMLElement>(
          'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ),
      ).filter(
        (element) =>
          !element.closest('[hidden]') &&
          !element.hasAttribute('hidden') &&
          !popup?.contains(element),
      );
      const current = controls.indexOf(button.current!);
      controls[current + (event.shiftKey ? -1 : 1)]?.focus();
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) {
        show();
        return;
      }
      setActive((current) => {
        if (!visible.length) return 0;
        let next = current;
        do {
          next =
            (next + (event.key === 'ArrowDown' ? 1 : -1) + visible.length) %
            visible.length;
        } while (visible[next].disabled && next !== current);
        return next;
      });
      return;
    }
    if (event.key === 'Enter' && open) {
      event.preventDefault();
      if (visible[active] && !visible[active].disabled)
        choose(visible[active].value);
      return;
    }
    if (
      !open &&
      event.key.length === 1 &&
      !event.ctrlKey &&
      !event.metaKey &&
      !event.altKey
    ) {
      event.preventDefault();
      show(event.key);
    }
  };
  return (
    <>
      <div className={`ff-searchable-select ${className}`}>
        {/* Keep a hidden native value bridge for existing forms and assistive test integrations. */}
        <select
          hidden
          aria-label={ariaLabel}
          aria-invalid={error || undefined}
          value={value}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
        >
          <option value="">{placeholder}</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <button
          ref={button}
          type="button"
          className={`field ff-searchable-trigger${error ? ' enquiry-invalid' : ''}`}
          role="combobox"
          aria-label={`Choose ${ariaLabel}`}
          aria-haspopup="listbox"
          aria-expanded={open}
          aria-controls={open ? `${id}-list` : undefined}
          aria-invalid={error || undefined}
          disabled={disabled}
          onClick={() => (open ? setOpen(false) : show())}
          onKeyDown={handleKey}
        >
          <span className={chosen ? '' : 'ff-searchable-placeholder'}>
            {chosen?.label || placeholder}
          </span>
          <ChevronDown size={16} aria-hidden="true" />
        </button>
        {allowClear && value && !disabled && (
          <button
            type="button"
            className="ff-searchable-clear"
            aria-label={`Clear ${ariaLabel}`}
            onClick={() => choose('')}
          >
            <X size={14} />
          </button>
        )}
      </div>
      {open &&
        createPortal(
          <div
            id={`${id}-popup`}
            className="ff-searchable-popup"
            style={{
              top: box.top,
              left: box.left,
              width: box.width,
              maxHeight: box.maxHeight,
            }}
            onKeyDown={handleKey}
          >
            <div className="ff-searchable-search">
              <Search size={16} aria-hidden="true" />
              <input
                ref={search}
                aria-label={`Search ${ariaLabel}`}
                aria-autocomplete="list"
                aria-controls={`${id}-list`}
                aria-activedescendant={
                  visible[active] ? `${id}-option-${active}` : undefined
                }
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value);
                  setActive(0);
                }}
                placeholder="Type to search"
              />
            </div>
            <div
              id={`${id}-list`}
              role="listbox"
              aria-label={ariaLabel}
              className="ff-searchable-list"
            >
              {loading ? (
                <div className="ff-searchable-message" role="status">
                  Loading…
                </div>
              ) : visible.length ? (
                visible.map((option, index) => (
                  <button
                    id={`${id}-option-${index}`}
                    key={`${option.value}-${index}`}
                    type="button"
                    role="option"
                    aria-selected={option.value === value}
                    disabled={option.disabled}
                    className={index === active ? 'active' : ''}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => choose(option.value)}
                  >
                    {option.countryCode && (
                      <CountryFlag
                        code={option.countryCode}
                        name={option.label}
                      />
                    )}
                    <span>{option.label}</span>
                    {option.value === value && (
                      <Check size={16} aria-hidden="true" />
                    )}
                  </button>
                ))
              ) : (
                <div className="ff-searchable-message" role="status">
                  No matching options
                </div>
              )}
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
