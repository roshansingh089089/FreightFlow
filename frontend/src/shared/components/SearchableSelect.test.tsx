import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { useForm } from 'react-hook-form';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FormSearchableSelect } from './FormSearchableSelect';
import { SearchableSelect } from './SearchableSelect';

const options = [
  { value: 'EXPORTER', label: 'Exporter' },
  { value: 'FREIGHT_PARTNER', label: 'Freight Partner', keywords: 'shipping' },
];

afterEach(cleanup);

describe('SearchableSelect', () => {
  it('opens, filters, and returns backend values', () => {
    const onChange = vi.fn();
    render(
      <SearchableSelect
        ariaLabel="Customer Type"
        value=""
        onChange={onChange}
        options={options}
      />,
    );
    fireEvent.click(
      screen.getByRole('combobox', { name: 'Choose Customer Type' }),
    );
    fireEvent.change(
      screen.getByRole('textbox', { name: 'Search Customer Type' }),
      { target: { value: 'shipping' } },
    );
    expect(screen.queryByRole('option', { name: 'Exporter' })).toBeNull();
    fireEvent.click(screen.getByRole('option', { name: 'Freight Partner' }));
    expect(onChange).toHaveBeenCalledWith('FREIGHT_PARTNER');
  });

  it('opens from typing on the closed control and supports keyboard selection', () => {
    const onChange = vi.fn();
    render(
      <SearchableSelect
        ariaLabel="Customer Type"
        value=""
        onChange={onChange}
        options={options}
      />,
    );
    const trigger = screen.getByRole('combobox', {
      name: 'Choose Customer Type',
    });
    fireEvent.keyDown(trigger, { key: 'f' });
    expect(
      screen.getByRole('textbox', { name: 'Search Customer Type' }),
    ).toHaveProperty('value', 'f');
    fireEvent.keyDown(
      screen.getByRole('textbox', { name: 'Search Customer Type' }),
      { key: 'ArrowDown' },
    );
    fireEvent.keyDown(
      screen.getByRole('textbox', { name: 'Search Customer Type' }),
      { key: 'Enter' },
    );
    expect(onChange).toHaveBeenCalledWith('FREIGHT_PARTNER');
  });

  it('moves to the next form control on Tab', () => {
    render(
      <>
        <SearchableSelect
          ariaLabel="Type"
          value=""
          onChange={vi.fn()}
          options={options}
        />
        <button type="button">Next field</button>
      </>,
    );
    fireEvent.click(screen.getByRole('combobox', { name: 'Choose Type' }));
    fireEvent.keyDown(screen.getByRole('textbox', { name: 'Search Type' }), {
      key: 'Tab',
    });
    expect(document.activeElement).toBe(
      screen.getByRole('button', { name: 'Next field' }),
    );
  });

  it('shows disabled, loading, empty, and clear states', () => {
    const onChange = vi.fn();
    const view = render(
      <SearchableSelect
        ariaLabel="Type"
        value=""
        onChange={onChange}
        options={options}
        disabled
      />,
    );
    fireEvent.click(screen.getByRole('combobox', { name: 'Choose Type' }));
    expect(screen.queryByRole('listbox')).toBeNull();
    view.rerender(
      <SearchableSelect
        ariaLabel="Type"
        value="EXPORTER"
        onChange={onChange}
        options={options}
        allowClear
        loading
      />,
    );
    fireEvent.click(screen.getByRole('combobox', { name: 'Choose Type' }));
    expect(screen.getByRole('status').textContent).toContain('Loading');
    fireEvent.click(screen.getByRole('button', { name: 'Clear Type' }));
    expect(onChange).toHaveBeenCalledWith('');
    view.rerender(
      <SearchableSelect
        ariaLabel="Type"
        value=""
        onChange={onChange}
        options={[]}
      />,
    );
    fireEvent.click(screen.getByRole('combobox', { name: 'Choose Type' }));
    expect(screen.getByRole('status').textContent).toContain(
      'No matching options',
    );
  });

  it('updates React Hook Form with the original enum value', () => {
    function Form() {
      const form = useForm({ defaultValues: { customerType: '' } });
      return (
        <form onSubmit={form.handleSubmit(() => undefined)}>
          <FormSearchableSelect
            form={form}
            name="customerType"
            label="Customer Type"
            required
            options={options}
          />
          <output data-testid="value">{form.watch('customerType')}</output>
        </form>
      );
    }
    render(<Form />);
    fireEvent.click(
      screen.getByRole('combobox', { name: 'Choose Customer Type' }),
    );
    fireEvent.click(screen.getByRole('option', { name: 'Freight Partner' }));
    expect(screen.getByTestId('value').textContent).toBe('FREIGHT_PARTNER');
  });

  it('portals the menu and keeps it inside a narrow viewport', () => {
    const bounds = vi
      .spyOn(HTMLElement.prototype, 'getBoundingClientRect')
      .mockReturnValue({
        left: 350,
        right: 450,
        top: 780,
        bottom: 816,
        width: 100,
        height: 36,
      } as DOMRect);
    const oldWidth = window.innerWidth;
    const oldHeight = window.innerHeight;
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      value: 390,
    });
    Object.defineProperty(window, 'innerHeight', {
      configurable: true,
      value: 844,
    });
    try {
      render(
        <div style={{ overflow: 'hidden' }}>
          <SearchableSelect
            ariaLabel="Type"
            value=""
            onChange={vi.fn()}
            options={options}
          />
        </div>,
      );
      fireEvent.click(screen.getByRole('combobox', { name: 'Choose Type' }));
      const popup = screen.getByRole('listbox').parentElement!;
      expect(popup.parentElement).toBe(document.body);
      expect(
        Number.parseFloat(popup.style.left) +
          Number.parseFloat(popup.style.width),
      ).toBeLessThanOrEqual(390);
      expect(Number.parseFloat(popup.style.top)).toBeLessThan(780);
    } finally {
      bounds.mockRestore();
      Object.defineProperty(window, 'innerWidth', {
        configurable: true,
        value: oldWidth,
      });
      Object.defineProperty(window, 'innerHeight', {
        configurable: true,
        value: oldHeight,
      });
    }
  });
});
