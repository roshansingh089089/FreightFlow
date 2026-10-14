import { ArrowRight, ArrowUpRight, Sparkles, X } from 'lucide-react';
import type { CSSProperties, PointerEvent as ReactPointerEvent } from 'react';
import { useEffect, useRef, useState } from 'react';
import { RobotMascot } from './RobotMascot';
import './floating-ai-chat.css';

type Message = { from: 'user' | 'bot'; text: string };
const prompts = [
  'How does quoting work?',
  'Can I connect Outlook?',
  'What is the pricing?',
];

function demoReply(question: string) {
  if (/pric|plan|cost/i.test(question))
    return 'Pricing has not been announced yet. You can request a demo to discuss options for your team.';
  if (/gmail|outlook|email|mail/i.test(question))
    return 'FreightFlow is designed to connect your customer email workflow with enquiries and quotations. Gmail and Microsoft 365 integration availability will depend on your deployment.';
  if (/quotation|quote|rate/i.test(question))
    return 'The quotation workflow starts with an enquiry, extracts shipment details for review, allows your team to select rates and charges, and produces an editable draft before anyone sends it.';
  return 'FreightFlow brings customer emails, enquiries, customer records, quotations and AI-assisted workflows into one platform. Book a demo to learn more.';
}

export function FloatingAiChat() {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState<{ x: number; y: number } | null>(
    null,
  );
  const [viewport, setViewport] = useState({
    width: window.innerWidth,
    height: window.innerHeight,
  });
  const dragRef = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    x: number;
    y: number;
    moved: boolean;
  } | null>(null);
  const suppressClickRef = useRef(false);
  const chatbotRef = useRef<HTMLDivElement>(null);
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<Message[]>([
    {
      from: 'bot',
      text: 'Hi! I can help you explore FreightFlow, its freight workflows, and how to request a demo.',
    },
  ]);
  const inputRef = useRef<HTMLInputElement>(null);
  const launcherRef = useRef<HTMLButtonElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const clampPosition = (x: number, y: number) => {
    const width = launcherRef.current?.offsetWidth || 56;
    const height = launcherRef.current?.offsetHeight || 56;
    return {
      x: Math.max(8, Math.min(x, window.innerWidth - width - 8)),
      y: Math.max(8, Math.min(y, window.innerHeight - height - 8)),
    };
  };
  useEffect(() => {
    const onResize = () => {
      setViewport({ width: window.innerWidth, height: window.innerHeight });
      setPosition((current) => current && clampPosition(current.x, current.y));
    };
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  const onPointerDown = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (event.button !== 0) return;
    const rect = chatbotRef.current?.getBoundingClientRect();
    if (!rect) return;
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      x: rect.left,
      y: rect.top,
      moved: false,
    };
    event.currentTarget.setPointerCapture?.(event.pointerId);
  };
  const onPointerMove = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const dx = event.clientX - drag.startX;
    const dy = event.clientY - drag.startY;
    if (!drag.moved && Math.hypot(dx, dy) < 5) return;
    drag.moved = true;
    setPosition(clampPosition(drag.x + dx, drag.y + dy));
  };
  const onPointerUp = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    if (drag.moved) {
      suppressClickRef.current = true;
      window.setTimeout(() => {
        suppressClickRef.current = false;
      }, 0);
    }
    dragRef.current = null;
    event.currentTarget.releasePointerCapture?.(event.pointerId);
  };
  useEffect(() => {
    const launcher = launcherRef.current;
    if (typeof window.matchMedia !== 'function') return;
    const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (
      !launcher ||
      window.innerWidth <= 600 ||
      !finePointer.matches ||
      reducedMotion.matches
    )
      return;

    let frame = 0;
    let targetX = 0;
    let targetY = 0;
    let currentX = 0;
    let currentY = 0;
    const animate = () => {
      currentX += (targetX - currentX) * 0.18;
      currentY += (targetY - currentY) * 0.18;
      launcher.style.setProperty(
        '--robot-turn-y',
        `${(currentX * 10).toFixed(2)}deg`,
      );
      launcher.style.setProperty(
        '--robot-turn-x',
        `${(-currentY * 4).toFixed(2)}deg`,
      );
      launcher.style.setProperty(
        '--robot-eye-x',
        `${(currentX * 3).toFixed(2)}px`,
      );
      launcher.style.setProperty(
        '--robot-eye-y',
        `${(currentY * 2.5).toFixed(2)}px`,
      );
      frame =
        Math.abs(targetX - currentX) + Math.abs(targetY - currentY) > 0.005
          ? requestAnimationFrame(animate)
          : 0;
    };
    const onPointerMove = (event: PointerEvent) => {
      const bounds = launcher.getBoundingClientRect();
      const centerX = bounds.left + bounds.width / 2;
      const centerY = bounds.top + bounds.height / 2;
      const distance = Math.hypot(
        event.clientX - centerX,
        event.clientY - centerY,
      );
      const strength = Math.max(0, 1 - distance / 170);
      targetX =
        Math.max(-1, Math.min(1, (event.clientX - centerX) / 75)) * strength;
      targetY =
        Math.max(-1, Math.min(1, (event.clientY - centerY) / 75)) * strength;
      if (!frame) frame = requestAnimationFrame(animate);
    };
    window.addEventListener('pointermove', onPointerMove, { passive: true });
    return () => {
      window.removeEventListener('pointermove', onPointerMove);
      cancelAnimationFrame(frame);
    };
  }, []);
  const send = (value: string) => {
    const trimmed = value.trim();
    if (!trimmed) return;
    setMessages((current) => [
      ...current,
      { from: 'user', text: trimmed },
      { from: 'bot', text: demoReply(trimmed) },
    ]);
    setQuestion('');
  };
  const close = () => {
    setOpen(false);
    launcherRef.current?.focus();
  };
  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);
  useEffect(() => {
    if (open) endRef.current?.scrollIntoView?.({ block: 'end' });
  }, [messages, open]);
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open]);
  const launcherWidth =
    launcherRef.current?.offsetWidth || (viewport.width <= 600 ? 50 : 56);
  const launcherHeight = launcherRef.current?.offsetHeight || launcherWidth;
  const panelWidth = Math.min(
    viewport.width <= 600 ? viewport.width - 28 : 400,
    viewport.width - 16,
  );
  const panelHeight = Math.min(
    viewport.width <= 600 ? 620 : 600,
    viewport.height - 24,
  );
  const panelStyle: CSSProperties | undefined = position
    ? {
        position: 'fixed',
        left: Math.max(
          8,
          Math.min(
            position.x + launcherWidth - panelWidth,
            viewport.width - panelWidth - 8,
          ),
        ),
        top:
          position.y >= panelHeight + 20
            ? position.y - panelHeight - 14
            : Math.min(
                position.y + launcherHeight + 14,
                viewport.height - panelHeight - 8,
              ),
        right: 'auto',
        bottom: 'auto',
        height: panelHeight,
      }
    : undefined;
  return (
    <div
      ref={chatbotRef}
      className="ff-chatbot"
      style={
        position
          ? { left: position.x, top: position.y, right: 'auto', bottom: 'auto' }
          : undefined
      }
    >
      <button
        ref={launcherRef}
        className="ff-chatbot-launcher"
        type="button"
        aria-label="Open FreightFlow AI Assistant"
        aria-describedby="ff-chatbot-tooltip"
        aria-controls="ff-chatbot-panel"
        aria-expanded={open}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onClick={() => {
          if (suppressClickRef.current) {
            suppressClickRef.current = false;
            return;
          }
          setOpen((value) => !value);
        }}
      >
        <span className="ff-robot-motion">
          <RobotMascot />
        </span>
        <span className="ff-chatbot-online" aria-hidden="true" />
      </button>
      <span
        id="ff-chatbot-tooltip"
        className="ff-chatbot-tooltip"
        role="tooltip"
      >
        Ask FreightFlow AI
      </span>
      <section
        id="ff-chatbot-panel"
        className={'ff-chatbot-panel' + (open ? ' is-open' : '')}
        style={panelStyle}
        aria-label="FreightFlow AI chat"
        aria-hidden={!open}
        inert={!open}
      >
        <header className="ff-chatbot-head">
          <span className="ff-chatbot-head-icon">
            <Sparkles size={18} />
          </span>
          <span>
            <strong>FreightFlow AI</strong>
            <small>
              <i /> Online · Demo assistant
            </small>
          </span>
          <button
            type="button"
            aria-label="Close FreightFlow AI Assistant"
            onClick={close}
          >
            <X size={21} />
          </button>
        </header>
        <div
          className="ff-chatbot-messages"
          role="log"
          aria-label="Chat messages"
          aria-live="polite"
        >
          {messages.map((message, index) => (
            <div key={index} className={'ff-chatbot-bubble ' + message.from}>
              {message.text}
            </div>
          ))}
          {messages.length === 1 && (
            <div className="ff-chatbot-prompts">
              {prompts.map((prompt) => (
                <button type="button" key={prompt} onClick={() => send(prompt)}>
                  {prompt}
                  <ArrowUpRight size={14} />
                </button>
              ))}
            </div>
          )}
          <div ref={endRef} />
        </div>
        <form
          className="ff-chatbot-compose"
          onSubmit={(event) => {
            event.preventDefault();
            send(question);
          }}
        >
          <input
            ref={inputRef}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            aria-label="Message"
            placeholder="Ask about FreightFlow..."
          />
          <button
            type="submit"
            aria-label="Send message"
            disabled={!question.trim()}
          >
            <ArrowRight size={19} />
          </button>
        </form>
        <p className="ff-chatbot-disclaimer">
          Demo assistant · sample responses only
        </p>
      </section>
    </div>
  );
}
