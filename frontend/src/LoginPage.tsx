import { useState, type FormEventHandler } from 'react';
import type { UseFormReturn } from 'react-hook-form';
import { Link } from 'react-router-dom';
import {
  Anchor, ArrowRight, Bell, Check, ChevronDown, Eye, EyeOff, FileText,
  Globe2, LayoutDashboard, LockKeyhole, Mail, Search, ShieldCheck,
  Ship, Truck, UsersRound,
} from 'lucide-react';
import './login-page.css';

type LoginPageProps = {
  form: UseFormReturn<any>;
  onSubmit: FormEventHandler<HTMLFormElement>;
  error: string;
};

const features = [
  { icon: UsersRound, title: 'Manage Customers', detail: 'Keep all your client information in one place' },
  { icon: FileText, title: 'Track Enquiries & Quotes', detail: 'From first enquiry to quote ready to send' },
  { icon: Truck, title: 'Streamline Operations', detail: 'Get real-time visibility across your freight business' },
];

function FreightLogo({ small = false }: { small?: boolean }) {
  return <div className={small ? 'login-logo login-logo-small' : 'login-logo'}>
    <Anchor aria-hidden="true" strokeWidth={2.4} />
    <span>Freight<span>Flow</span></span>
  </div>;
}

function NetworkBackdrop() {
  return <svg className="login-network" viewBox="0 0 900 560" aria-hidden="true" preserveAspectRatio="xMidYMid slice">
    <defs>
      <pattern id="login-map-dots" width="7" height="7" patternUnits="userSpaceOnUse"><circle cx="2" cy="2" r="1.25" fill="#63a9ed" /></pattern>
      <linearGradient id="login-route-line"><stop stopColor="#337ac4" stopOpacity="0"/><stop offset=".5" stopColor="#64b9ff" stopOpacity=".8"/><stop offset="1" stopColor="#337ac4" stopOpacity="0"/></linearGradient>
    </defs>
    <g fill="url(#login-map-dots)" opacity=".27">
      <path d="M74 150l53-33 91-2 49 22 48-10 36 18-8 30-42 13-12 34-33 20-13 44-41 33-31-17-18-51-29-17-32-55z"/>
      <path d="M268 346l46-12 48 22 14 38-18 48-22 35-11 49-20 11-11-37-21-26-13-51-19-33z"/>
      <path d="M404 143l31-26 54 5 24-17 56 3 27-14 84 4 48 35 72-7 42 25-19 30-54 7-44 42-39-4-27 44-53 4-31 23-46-16-20-54-42-11-35-36-37-2z"/>
      <path d="M465 295l65-12 44 25 17 55-21 50-37 43-34-20-27-46-21-51z"/>
      <path d="M710 406l56-21 53 17 18 35-21 21-66-4-39-23z"/>
    </g>
    <g fill="none" stroke="url(#login-route-line)" strokeWidth="1.5" opacity=".65"><path d="M202 202Q349 42 508 211"/><path d="M508 211Q621 103 749 168"/><path d="M202 202Q390 373 617 280"/></g>
    <g fill="#82c9ff" opacity=".9"><circle cx="202" cy="202" r="5"/><circle cx="508" cy="211" r="5"/><circle cx="749" cy="168" r="5"/><circle cx="617" cy="280" r="4"/></g>
  </svg>;
}

function ShipmentMap() {
  return <svg className="login-preview-map" viewBox="0 0 330 144" role="img" aria-label="Illustration of a shipment route across the world">
    <path fill="#e6f0fa" d="M13 48l28-22 34 2 16 10 21-6 14 12-8 16-16 5-8 22-22 6-13-21-22-8zM98 102l19-10 21 8 10 20-13 18-18-11-10-14zM152 36l19-11 24 4 16-11 44 7 11 17 37-4 13 14-22 11-27 4-14 20-25-4-19 11-21-18-26-3zM186 92l32-8 19 13-5 31-17 11-24-18zM270 105l37-9 18 16-19 18-33-4z"/>
    <path d="M72 83Q153 24 249 83" fill="none" stroke="#2778de" strokeWidth="2.5" strokeDasharray="5 5"/>
    <circle cx="72" cy="83" r="5" fill="#216ef3"/><circle cx="249" cy="83" r="5" fill="#216ef3"/>
    <circle cx="72" cy="83" r="10" fill="#216ef3" opacity=".12"/><circle cx="249" cy="83" r="10" fill="#216ef3" opacity=".12"/>
  </svg>;
}

function DashboardPreview() {
  return <div className="login-dashboard-preview" aria-hidden="true">
    <div className="login-preview-sidebar">
      <FreightLogo small />
      <div className="login-preview-nav active"><LayoutDashboard size={13}/> Dashboard</div>
      <div className="login-preview-nav"><UsersRound size={13}/> Customers</div>
      <div className="login-preview-nav"><FileText size={13}/> Enquiries</div>
      <div className="login-preview-nav"><FileText size={13}/> Quotations</div>
      <div className="login-preview-nav"><Ship size={13}/> Shipments</div>
    </div>
    <div className="login-preview-workspace">
      <div className="login-preview-toolbar"><Search size={12}/><span>Search shipments, customers...</span><Bell size={12}/><span className="login-preview-avatar">A</span></div>
      <div className="login-preview-main">
        <div className="login-preview-heading"><div><small>OVERVIEW</small><h3>Dashboard</h3></div><span>Today <ChevronDown size={10}/></span></div>
        <div className="login-preview-metrics"><div><span className="login-preview-metric-icon blue"><UsersRound size={15}/></span><small>Total Enquiries</small><strong>128</strong><em>↗ 12% this month</em></div><div><span className="login-preview-metric-icon green"><FileText size={15}/></span><small>Quotes Sent</small><strong>86</strong><em>↗ 18% this month</em></div></div>
        <div className="login-preview-map-card"><div><h4>Shipments Overview</h4><span>Live route activity</span></div><ShipmentMap/><div className="login-preview-map-note"><Ship size={13}/><span>IN TRANSIT<br/><strong>Mumbai → Singapore</strong></span></div></div>
        <div className="login-preview-table"><h4>Recent Enquiries</h4><div className="login-preview-row head"><span>Customer</span><span>Route</span><span>Status</span></div><div className="login-preview-row"><span>ABC Textiles</span><span>IN → SG</span><b className="sent">Quote Sent</b></div><div className="login-preview-row"><span>Global Traders</span><span>IN → UAE</span><b className="progress">In Progress</b></div><div className="login-preview-row"><span>Skyline Imports</span><span>IN → US</span><b className="new">New</b></div></div>
      </div>
    </div>
  </div>;
}

function PortSilhouette() {
  return <div className="login-port" aria-hidden="true"><div className="login-port-haze"/><div className="login-crane one"><span/></div><div className="login-crane two"><span/></div><div className="login-container-stack first"/><div className="login-container-stack second"/><div className="login-container-stack third"/><div className="login-port-ground"/></div>;
}

export default function LoginPage({ form, onSubmit, error }: LoginPageProps) {
  const [showPassword, setShowPassword] = useState(false);
  return <div className="login-page">
    <aside className="login-brand-panel">
      <NetworkBackdrop />
      <div className="login-brand-inner">
        <FreightLogo />
        <div className="login-hero">
          <span className="login-kicker"><i/> FREIGHT OPERATIONS PLATFORM</span>
          <h1>Move Freight<br/><span>Smarter</span></h1>
          <p>Manage customers, enquiries, quotations and operations from one powerful workspace.</p>
        </div>
        <DashboardPreview />
        <div className="login-feature-list">{features.map(({ icon: Icon, title, detail }) => <div className="login-feature" key={title}><span className="login-feature-icon"><Icon size={22} strokeWidth={1.8}/></span><span><strong>{title}</strong><small>{detail}</small></span></div>)}</div>
        <PortSilhouette />
        <div className="login-stat-bar"><div><Globe2 size={25}/><span><strong>50+</strong><small>Countries</small></span></div><div><Ship size={26}/><span><strong>1,000+</strong><small>Shipments Handled</small></span></div><div><UsersRound size={26}/><span><strong>200+</strong><small>Happy Customers</small></span></div></div>
      </div>
    </aside>
    <main className="login-auth-panel">
      <div className="login-auth-glow"/>
      <div className="login-auth-card">
        <div className="login-auth-heading"><span className="login-auth-eyebrow">YOUR WORKSPACE AWAITS</span><h2>Welcome back</h2><p>Sign in to your FreightFlow workspace.</p></div>
        <form onSubmit={onSubmit} noValidate>
          <label className="login-form-group" htmlFor="login-email"><span>Work Email</span><span className={'login-input-wrap'+(form.formState.errors.email?' is-invalid':'')}><Mail size={19} aria-hidden="true"/><input id="login-email" type="email" autoComplete="email" placeholder="Enter your work email" aria-invalid={!!form.formState.errors.email} {...form.register('email')}/></span>{form.formState.errors.email&&<small className="login-field-error">{String(form.formState.errors.email.message)}</small>}</label>
          <label className="login-form-group" htmlFor="login-password"><span>Password</span><span className={'login-input-wrap'+(form.formState.errors.password?' is-invalid':'')}><LockKeyhole size={19} aria-hidden="true"/><input id="login-password" type={showPassword?'text':'password'} autoComplete="current-password" placeholder="Enter your password" aria-invalid={!!form.formState.errors.password} {...form.register('password')}/><button className="login-visibility" type="button" aria-label={showPassword?'Hide password':'Show password'} onClick={event=>{event.preventDefault();setShowPassword(!showPassword)}}>{showPassword?<EyeOff size={19}/>:<Eye size={19}/>}</button></span>{form.formState.errors.password&&<small className="login-field-error">{String(form.formState.errors.password.message)}</small>}</label>
          <div className="login-form-options"><label><input type="checkbox" defaultChecked/><span>Remember me</span></label><button type="button" title="Password recovery is coming soon">Forgot password?</button></div>
          {error&&<p className="login-form-error" role="alert">{error}</p>}
          <button className="login-submit" type="submit" disabled={form.formState.isSubmitting}><span>{form.formState.isSubmitting?'Signing in...':'Login'}</span>{form.formState.isSubmitting?<span className="login-spinner"/>:<ArrowRight size={20}/>}</button>
        </form>
        <div className="login-divider"><span>or</span></div>
        <p className="login-signup-link">New to FreightFlow? <Link to="/signup">Sign up</Link></p>
        <div className="login-security-note"><span><ShieldCheck size={24}/></span><div><strong>Secure access for your freight operations</strong><small>Your data is protected and encrypted</small></div><Check className="login-security-check" size={16}/></div>
      </div>
      <p className="login-legal">© 2026 FreightFlow · Freight operations, simplified</p>
    </main>
  </div>;
}
