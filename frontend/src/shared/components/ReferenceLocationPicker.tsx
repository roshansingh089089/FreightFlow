import { useQuery } from '@tanstack/react-query';
import { X } from 'lucide-react';
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../lib/workspace';
import { CountryFlag } from './CountryFlag';
import './reference-location-picker.css';

export type LocationOption = {
  code: string;
  name: string;
  countryCode: string;
  countryName?: string;
  locationTypes?: string[];
  iataCode?: string | null;
  icaoCode?: string | null;
};

export function ReferenceLocationPicker({
  id,
  label,
  mode,
  value,
  code,
  error,
  onTextChange,
  onSelect,
}: {
  id: string;
  label: string;
  mode: string;
  value: string;
  code: string;
  error?: string;
  onTextChange: (value: string) => void;
  onSelect: (location: LocationOption) => void;
}) {
  const listId = useId();
  const input = useRef<HTMLInputElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const [focused, setFocused] = useState(false);
  const [active, setActive] = useState(0);
  const [box, setBox] = useState({ top: 0, left: 0, width: 0, maxHeight: 230 });
  const searchText = value.trim();
  const [debouncedSearchText, setDebouncedSearchText] = useState('');
  useEffect(() => {
    const timer = window.setTimeout(
      () => setDebouncedSearchText(searchText),
      300,
    );
    return () => window.clearTimeout(timer);
  }, [searchText]);
  const searchReady =
    focused &&
    !code &&
    searchText.length >= 2 &&
    debouncedSearchText === searchText;
  const choices = useQuery<LocationOption[]>({
    queryKey: ['reference-locations', debouncedSearchText.toLowerCase(), mode],
    queryFn: ({ signal }) =>
      api(
        `/reference/locations?q=${encodeURIComponent(debouncedSearchText)}&mode=${encodeURIComponent(mode)}`,
        { signal },
      ),
    enabled: searchReady,
    staleTime: 600_000,
    gcTime: 1_800_000,
  });
  useLayoutEffect(() => {
    if (!searchReady) return;
    const position = () => {
      const rect = input.current?.getBoundingClientRect();
      if (!rect) return;
      const below = window.innerHeight - rect.bottom;
      const above = rect.top;
      const placeAbove = below < 220 && above > below;
      setBox({
        top: placeAbove
          ? Math.max(8, rect.top - Math.min(270, above - 12))
          : rect.bottom + 4,
        left: Math.max(
          8,
          Math.min(
            rect.left,
            window.innerWidth - Math.max(rect.width, 260) - 8,
          ),
        ),
        width: Math.min(Math.max(rect.width, 260), window.innerWidth - 16),
        maxHeight: Math.max(
          120,
          Math.min(260, (placeAbove ? above : below) - 12),
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
  }, [searchReady]);
  useEffect(() => {
    if (!searchReady) return;
    const outside = (event: PointerEvent) => {
      const target = event.target as Node;
      if (!input.current?.contains(target) && !menu.current?.contains(target))
        setFocused(false);
    };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [searchReady]);
  const select = (location: LocationOption) => {
    onSelect(location);
    setFocused(false);
    setActive(0);
    input.current?.focus();
  };
  return (
    <div className="freight-location-field">
      <label className="label" htmlFor={id}>
        {label}
      </label>
      <div className="ff-location-input-wrap">
        <input
          ref={input}
          id={id}
          className="field"
          role="combobox"
          autoComplete="off"
          aria-autocomplete="list"
          aria-haspopup="listbox"
          aria-expanded={searchReady}
          aria-controls={searchReady ? listId : undefined}
          aria-activedescendant={
            searchReady && choices.data?.[active]
              ? `${listId}-${active}`
              : undefined
          }
          aria-invalid={!!error}
          aria-describedby={error ? `${id}-error` : undefined}
          value={value}
          onChange={(event) => {
            onTextChange(event.target.value);
            setFocused(true);
            setActive(0);
          }}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              setFocused(false);
              return;
            }
            if (event.key === 'ArrowDown' && choices.data?.length) {
              event.preventDefault();
              setActive((current) => (current + 1) % choices.data!.length);
            }
            if (event.key === 'ArrowUp' && choices.data?.length) {
              event.preventDefault();
              setActive(
                (current) =>
                  (current - 1 + choices.data!.length) % choices.data!.length,
              );
            }
            if (
              event.key === 'Enter' &&
              searchReady &&
              choices.data?.[active]
            ) {
              event.preventDefault();
              select(choices.data[active]);
            }
          }}
        />
        {value && (
          <button
            type="button"
            className="ff-location-clear"
            aria-label={`Clear ${label}`}
            onClick={() => {
              onTextChange('');
              input.current?.focus();
            }}
          >
            <X size={14} />
          </button>
        )}
        {code ? (
          <small className="freight-location-hint">UN/LOCODE {code}</small>
        ) : value.trim().length >= 2 ? (
          <small className="freight-location-hint">
            Select a matching location
          </small>
        ) : null}
      </div>
      {error && (
        <small id={`${id}-error`} className="enquiry-field-error" role="alert">
          {error}
        </small>
      )}
      {searchReady &&
        createPortal(
          <div
            ref={menu}
            className="ff-location-popup"
            style={{
              top: box.top,
              left: box.left,
              width: box.width,
              maxHeight: box.maxHeight,
            }}
          >
            <div
              id={listId}
              role="listbox"
              aria-label={`${label} suggestions`}
              className="ff-location-list"
            >
              {choices.isPending || choices.isFetching ? (
                <div role="status" className="ff-location-message">
                  Searching locations…
                </div>
              ) : choices.isError ? (
                <div role="alert" className="ff-location-message">
                  Could not load locations. Try again.
                </div>
              ) : choices.data?.length ? (
                choices.data.map((location, index) => (
                  <button
                    id={`${listId}-${index}`}
                    key={location.code}
                    type="button"
                    role="option"
                    aria-label={`${location.name} · ${location.countryCode} · ${location.code}`}
                    aria-selected={index === active}
                    className={index === active ? 'active' : ''}
                    onMouseDown={(event) => event.preventDefault()}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => select(location)}
                  >
                    <CountryFlag
                      code={location.countryCode}
                      name={location.countryName}
                    />
                    <span className="ff-location-main">
                      <strong>{location.name}</strong>
                      <small>
                        {location.countryName || location.countryCode} ·{' '}
                        {location.locationTypes?.join(' / ') || 'Place'}
                      </small>
                    </span>
                    <span className="ff-location-codes">
                      {location.code}
                      {location.iataCode ? ` · ${location.iataCode}` : ''}
                      {location.icaoCode ? ` · ${location.icaoCode}` : ''}
                    </span>
                  </button>
                ))
              ) : (
                <div role="status" className="ff-location-message">
                  No matching locations
                </div>
              )}
            </div>
          </div>,
          document.body,
        )}
    </div>
  );
}
