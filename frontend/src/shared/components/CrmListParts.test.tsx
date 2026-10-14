import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CrmDetailPanel, desktopDetailInitiallyOpen } from './CrmListParts';

const originalMatchMedia = window.matchMedia;
afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

describe('CRM detail panel', () => {
  it('starts closed at mobile widths and closes with Escape', () => {
    window.matchMedia = vi.fn(
      (query: string) =>
        ({
          matches: query.includes('max-width'),
          media: query,
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
        }) as unknown as MediaQueryList,
    );
    expect(desktopDetailInitiallyOpen()).toBe(false);
    const close = vi.fn();
    render(
      <CrmDetailPanel
        title="Record"
        tabs={['Overview']}
        activeTab="Overview"
        onTab={() => {}}
        onClose={close}
      >
        Details
      </CrmDetailPanel>,
    );
    expect(document.activeElement).toBe(
      screen.getByRole('button', { name: 'Close details' }),
    );
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(close).toHaveBeenCalledTimes(1);
  });
});
