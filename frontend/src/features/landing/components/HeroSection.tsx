import {
  ArrowRight,
  CheckCircle2,
  Layers3,
  Play,
  Sparkles,
  Users,
  Zap,
} from 'lucide-react';
import { HeroBackground } from './HeroBackground';
import { HeroProductShowcase } from './HeroProductShowcase';

const benefits = [
  { icon: Zap, title: 'Save hours', detail: 'on manual work' },
  { icon: CheckCircle2, title: 'More accurate', detail: 'quotations' },
  { icon: Users, title: 'Better customer', detail: 'follow ups' },
  {
    icon: Layers3,
    title: 'All in one platform',
    detail: 'for freight forwarding',
  },
];

export function HeroSection() {
  return (
    <section className="ff-hero">
      <HeroBackground />
      <div className="ff-hero-content">
        <div className="ff-hero-intro">
          <div className="ff-hero-eyebrow">
            <Sparkles size={15} /> AI POWERED FREIGHT FORWARDING
          </div>
          <h1>
            Freight forwarding,
            <br />
            <span>without the friction.</span>
          </h1>
          <p>
            From customer emails to professional quotations, manage your entire
            freight business in one intelligent workspace.
          </p>
          <div className="ff-hero-actions">
            <a className="ff-hero-start" href="/signup">
              Start free trial <ArrowRight size={17} />
            </a>
            <a className="ff-hero-demo" href="#workflow">
              <span>
                <Play size={12} fill="currentColor" />
              </span>{' '}
              Watch AI demo
            </a>
          </div>
        </div>
        <HeroProductShowcase />
        <div className="ff-hero-benefits">
          {benefits.map(({ icon: Icon, title, detail }) => (
            <div key={title}>
              <span className="ff-hero-benefit-icon">
                <Icon size={22} />
              </span>
              <span>
                <strong>{title}</strong>
                <small>{detail}</small>
              </span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
