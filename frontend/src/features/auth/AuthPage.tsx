import { zodResolver } from '@hookform/resolvers/zod';
import { Anchor, ArrowRight, Globe2, ShieldCheck, Ship } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Field } from '../../shared/components/PageElements';
import { post, qc } from '../../shared/lib/workspace';
import LoginPage from './LoginPage';
const authSchema = z.object({
  email: z.email('Enter a valid email'),
  password: z.string().min(8, 'At least 8 characters'),
});
const signupSchema = authSchema.extend({
  companyName: z.string().min(2),
  fullName: z.string().min(2),
  country: z.string().min(2),
  phone: z.string().optional(),
  terms: z.boolean().refine(Boolean, 'Accept the terms'),
});
export function Auth({ signup = false }: { signup?: boolean }) {
  const nav = useNavigate();
  const [err, setErr] = useState('');
  const form = useForm<any>({
    resolver: zodResolver(signup ? signupSchema : authSchema),
  });
  const submit = form.handleSubmit(async (v) => {
    setErr('');
    try {
      await post(signup ? '/auth/signup' : '/auth/login', v);
      qc.invalidateQueries();
      nav('/dashboard');
    } catch (e: any) {
      setErr(e.message);
    }
  });
  if (signup)
    return (
      <div className="signup-page">
        <section className="signup-story">
          <div className="signup-story-content">
            <Link to="/login" className="signup-brand">
              <span className="signup-brand-mark">
                <Anchor size={23} />
              </span>
              <span>
                Freight<span className="text-sky-300">Flow</span>
              </span>
            </Link>
            <div className="signup-headline">
              <div className="signup-eyebrow">
                <span className="signup-eyebrow-line" /> BUILT FOR FREIGHT
                FORWARDERS
              </div>
              <h1>
                Move freight.
                <br />
                <span>Move forward.</span>
              </h1>
              <p>
                From the first customer enquiry to the final quotation, keep
                your freight business moving in one clear workspace.
              </p>
            </div>
            <div className="signup-scene" aria-hidden="true">
              <div className="signup-scene-glow" />
              <div className="signup-scene-grid" />
              <div className="signup-route-card">
                <div className="signup-route-top">
                  <span className="signup-route-tag">
                    <Globe2 size={13} /> GLOBAL ROUTE
                  </span>
                  <span className="signup-route-live">
                    <span /> LIVE WORKSPACE
                  </span>
                </div>
                <div className="signup-route-points">
                  <div>
                    <small>ORIGIN</small>
                    <strong>Mumbai</strong>
                    <span>India · INBOM</span>
                  </div>
                  <div className="signup-route-line">
                    <span />
                    <Ship size={20} />
                    <span />
                  </div>
                  <div className="text-right">
                    <small>DESTINATION</small>
                    <strong>Hamburg</strong>
                    <span>Germany · DEHAM</span>
                  </div>
                </div>
              </div>
              <div className="signup-crane">
                <i />
                <b />
                <span />
              </div>
              <div className="signup-container signup-container-back" />
              <div className="signup-container signup-container-front" />
              <div className="signup-ship">
                <div className="signup-ship-deck" />
                <div className="signup-ship-hull" />
              </div>
              <div className="signup-water" />
            </div>
            <div className="signup-story-footer">
              <div className="flex items-center gap-2">
                <ShieldCheck size={17} /> One workspace for your whole
                forwarding team
              </div>
              <span>FreightFlow © 2026</span>
            </div>
          </div>
        </section>
        <main className="signup-form-side">
          <div className="signup-mobile-brand">
            <Anchor size={19} /> FreightFlow
          </div>
          <div className="signup-form-wrap">
            <div className="signup-form-intro">
              <span className="signup-step">GET STARTED · FREE PLAN</span>
              <h2>Create your freight workspace</h2>
              <p>
                Set up your company account and start managing shipments in
                minutes.
              </p>
            </div>
            <form onSubmit={submit} className="signup-form" noValidate>
              <div className="signup-form-section">
                <span className="signup-section-number">01</span>
                <div>
                  <h3>Company details</h3>
                  <p>Tell us about your forwarding business.</p>
                </div>
              </div>
              <div className="signup-fields">
                <Field
                  form={form}
                  name="companyName"
                  label="Company name *"
                  placeholder="e.g. Horizon Logistics"
                />
                <Field
                  form={form}
                  name="country"
                  label="Country *"
                  placeholder="e.g. India"
                />
              </div>
              <div className="signup-form-section signup-form-section-second">
                <span className="signup-section-number">02</span>
                <div>
                  <h3>Your account</h3>
                  <p>You'll be the workspace administrator.</p>
                </div>
              </div>
              <div className="signup-fields">
                <Field
                  form={form}
                  name="fullName"
                  label="Full name *"
                  placeholder="Your name"
                />
                <Field
                  form={form}
                  name="phone"
                  label="Phone (optional)"
                  placeholder="+91 98765 43210"
                />
                <div className="signup-span-two">
                  <Field
                    form={form}
                    name="email"
                    label="Work email *"
                    type="email"
                    placeholder="you@company.com"
                  />
                </div>
                <div className="signup-span-two">
                  <Field
                    form={form}
                    name="password"
                    label="Password *"
                    type="password"
                    placeholder="At least 8 characters"
                  />
                </div>
              </div>
              <div className="signup-form-footer">
                <label className="signup-terms">
                  <input type="checkbox" {...form.register('terms')} />
                  <span>
                    I agree to the terms of service and understand that this
                    account starts on the free plan.
                  </span>
                </label>
                {form.formState.errors.terms && (
                  <small className="text-red-600">
                    {String(form.formState.errors.terms.message)}
                  </small>
                )}
                {err && (
                  <p className="signup-error" role="alert">
                    {err}
                  </p>
                )}
                <button
                  disabled={form.formState.isSubmitting}
                  className="signup-submit"
                >
                  {form.formState.isSubmitting
                    ? 'Creating workspace...'
                    : 'Create free workspace'}
                  <ArrowRight size={18} />
                </button>
                <p className="signup-signin">
                  Already have an account? <Link to="/login">Sign in</Link>
                </p>
              </div>
            </form>
          </div>
        </main>
      </div>
    );
  return <LoginPage form={form} onSubmit={submit} error={err} />;
}
