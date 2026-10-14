import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { FloatingAiChat } from './FloatingAiChat';

afterEach(cleanup);

describe('floating AI chat', () => {
  it('opens, replies, minimizes without losing messages, and preserves state through parent rerenders', () => {
    const { rerender } = render(
      <>
        <main>Landing</main>
        <FloatingAiChat />
      </>,
    );
    expect(
      screen.getAllByRole('button', { name: 'Open FreightFlow AI Assistant' }),
    ).toHaveLength(1);
    fireEvent.click(
      screen.getByRole('button', { name: 'Open FreightFlow AI Assistant' }),
    );
    fireEvent.click(
      screen.getByRole('button', { name: 'What is the pricing?' }),
    );
    expect(screen.getByText(/Pricing has not been announced yet/)).toBeTruthy();
    fireEvent.click(
      screen.getByRole('button', { name: 'Close FreightFlow AI Assistant' }),
    );
    rerender(
      <>
        <main>Dashboard</main>
        <FloatingAiChat />
      </>,
    );
    fireEvent.click(
      screen.getByRole('button', { name: 'Open FreightFlow AI Assistant' }),
    );
    expect(screen.getByText(/Pricing has not been announced yet/)).toBeTruthy();
    fireEvent.change(screen.getByRole('textbox', { name: 'Message' }), {
      target: { value: 'How does quoting work?' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send message' }));
    expect(screen.getByText(/FreightFlow brings customer emails/)).toBeTruthy();
  });
  it('drags the launcher without opening chat and keeps the chat panel inside the viewport', () => {
    render(<FloatingAiChat />);
    const launcher = screen.getByRole('button', {
      name: 'Open FreightFlow AI Assistant',
    });
    const root = launcher.closest('.ff-chatbot') as HTMLElement;
    root.getBoundingClientRect = () => ({
      left: 100,
      top: 100,
      width: 56,
      height: 56,
      right: 156,
      bottom: 156,
      x: 100,
      y: 100,
      toJSON: () => ({}),
    });
    const pointer = (type: string, x: number, y: number) => {
      const event = new Event(type, { bubbles: true });
      Object.defineProperties(event, {
        pointerId: { value: 1 },
        button: { value: 0 },
        clientX: { value: x },
        clientY: { value: y },
      });
      fireEvent(launcher, event);
    };
    pointer('pointerdown', 110, 110);
    pointer('pointermove', 210, 210);
    pointer('pointerup', 210, 210);
    fireEvent.click(launcher);
    expect(root.style.left).toBe('200px');
    expect(root.style.top).toBe('200px');
    expect(launcher.getAttribute('aria-expanded')).toBe('false');
  });
});
