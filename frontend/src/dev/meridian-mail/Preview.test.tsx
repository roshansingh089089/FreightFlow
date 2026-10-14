import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import Preview from './Preview';

describe('isolated Meridian Mail preview', () => {
  beforeEach(() =>
    window.history.replaceState({}, '', '/meridian-preview.html'),
  );
  it('shows fixture folders, messages and contextual onboarding', () => {
    render(<Preview />);
    expect(
      screen.getAllByText(
        'Introduction & rate enquiry – Bangkok to Chennai',
      )[0],
    ).toBeTruthy();
    fireEvent.click(
      screen.getByRole('button', { name: 'Onboard customer with AI' }),
    );
    expect(screen.getByDisplayValue('Golden Orchard Co., Ltd.')).toBeTruthy();
    expect(screen.getByText(/Source email:/)).toBeTruthy();
  });
  it('expands the selected row inline only after a click in Side by side', () => {
    render(<Preview />);
    fireEvent.click(
      document.querySelector<HTMLButtonElement>(
        '.mmp-mode button:nth-child(2)',
      )!,
    );
    expect(document.querySelector('[data-measure="inline-reader"]')).toBeNull();
    const row = document.querySelector<HTMLButtonElement>(
      '[data-measure="row"] .mmp-row-hit',
    )!;
    fireEvent.click(row);
    expect(
      document.querySelector('[data-measure="inline-reader"]'),
    ).toBeTruthy();
    fireEvent.click(row);
    expect(document.querySelector('[data-measure="inline-reader"]')).toBeNull();
  });
});
