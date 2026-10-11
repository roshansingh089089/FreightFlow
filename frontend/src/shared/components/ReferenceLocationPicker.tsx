import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../lib/workspace';

export type LocationOption = {
  code: string;
  name: string;
  countryCode: string;
};

export function ReferenceLocationPicker({
  id,
  label,
  mode,
  value,
  code,
  onTextChange,
  onSelect,
}: {
  id: string;
  label: string;
  mode: string;
  value: string;
  code: string;
  onTextChange: (value: string) => void;
  onSelect: (location: LocationOption) => void;
}) {
  const [focused, setFocused] = useState(false);
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
    queryFn: () =>
      api(
        `/reference/locations?q=${encodeURIComponent(debouncedSearchText)}&mode=${encodeURIComponent(mode)}`,
      ),
    enabled: searchReady,
    staleTime: 600_000,
    gcTime: 1_800_000,
  });
  return (
    <div
      className="freight-location-field"
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node))
          setFocused(false);
      }}
    >
      <label className="label" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        className="field"
        autoComplete="off"
        aria-autocomplete="list"
        aria-expanded={searchReady && !!choices.data?.length}
        value={value}
        onChange={(event) => {
          onTextChange(event.target.value);
          setFocused(true);
        }}
        onFocus={() => setFocused(true)}
        onKeyDown={(event) => {
          if (event.key === 'Escape') setFocused(false);
          if (
            event.key === 'ArrowDown' &&
            searchReady &&
            choices.data?.length
          ) {
            event.preventDefault();
            event.currentTarget.parentElement
              ?.querySelector<HTMLButtonElement>('[role="option"]')
              ?.focus();
          }
        }}
      />
      {code ? (
        <small className="freight-location-hint">UN/LOCODE {code}</small>
      ) : value.trim().length >= 2 ? (
        <small className="freight-location-hint">
          Select a matching location
        </small>
      ) : null}
      {searchReady && !!choices.data?.length && (
        <span className="freight-location-options" role="listbox">
          {choices.data.map((location) => (
            <button
              key={location.code}
              type="button"
              role="option"
              aria-selected={false}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => {
                onSelect(location);
                setFocused(false);
              }}
            >
              {location.name} · {location.countryCode} · {location.code}
            </button>
          ))}
        </span>
      )}
    </div>
  );
}
