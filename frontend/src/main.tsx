import { QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import './app.css';
import './app/layouts/app-header.css';
import { AppLayout } from './app/layouts/Shell';
import { UnavailablePage } from './app/pages/UnavailablePage';
import { Auth } from './features/auth/AuthPage';
import { FloatingAiChat } from './features/chatbot/FloatingAiChat';
import {
  CustomerDetail,
  CustomerForm,
} from './features/customers/CustomerPages';
import CustomerWorkspace from './features/customers/CustomerWorkspace';
import { Dashboard } from './features/dashboard/DashboardPage';
import {
  Enquiries,
  EnquiryDetail,
  EnquiryForm,
} from './features/enquiries/EnquiryPages';
import LandingPage from './features/landing/LandingPage';
import EmailComposer from './features/mail/EmailComposer';
import MailDrafts from './features/mail/MailDrafts';
import MailSearch from './features/mail/MailSearch';
import MailThreadPage from './features/mail/MailThreadPage';
import './features/mail/meridian-mail.css';
import StoredMail from './features/mail/StoredMail';
import {
  Quotations,
  QuoteDetail,
  QuoteForm,
} from './features/quotations/QuotationPages';
import { SettingsPage } from './features/settings/SettingsPage';
import './index.css';
import { qc } from './shared/lib/workspace';
import './styles/composer.css';
import './styles/crm-suite.css';
import './styles/list-pages.css';
import './styles/quotation-document.css';
import './styles/recovered-feature-styles.css';
import './styles/ui-system.css';
import './theme.css';
import './features/dashboard/dashboard.css';
import './zz-mail-workspace.css';
import './app/layouts/sidebar.css';
function App() {
  return (
    <Routes>
      <Route path="/login" element={<Auth />} />
      <Route path="/signup" element={<Auth signup />} />
      <Route path="/" element={<LandingPage />} />
      <Route element={<AppLayout />}>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/mail" element={<CustomerWorkspace />} />
        <Route path="/mail/compose" element={<EmailComposer />} />
        <Route path="/mail/drafts" element={<MailDrafts />} />
        <Route path="/mail/stored" element={<StoredMail />} />
        <Route path="/mail/search" element={<MailSearch />} />
        <Route path="/mail/threads/:id" element={<MailThreadPage />} />
        <Route
          path="/shipments"
          element={<UnavailablePage title="Shipments" />}
        />
        <Route path="/rates" element={<UnavailablePage title="Rate Desk" />} />
        <Route
          path="/documents"
          element={<UnavailablePage title="Documents" />}
        />
        <Route path="/reports" element={<UnavailablePage title="Reports" />} />
        <Route path="/customers" element={<CustomerWorkspace />} />
        <Route path="/customers/new" element={<CustomerForm />} />
        <Route path="/customers/:id/edit" element={<CustomerForm />} />
        <Route path="/customers/:id" element={<CustomerDetail />} />
        <Route path="/enquiries" element={<Enquiries />} />
        <Route path="/enquiries/new" element={<EnquiryForm />} />
        <Route path="/enquiries/:id/edit" element={<EnquiryForm />} />
        <Route path="/enquiries/:id" element={<EnquiryDetail />} />
        <Route
          path="/enquiries/:enquiryId/quotations/new"
          element={<QuoteForm />}
        />
        <Route path="/quotations" element={<Quotations />} />
        <Route path="/quotations/:id/edit" element={<QuoteForm />} />
        <Route path="/quotations/:id" element={<QuoteDetail />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
    </Routes>
  );
}
createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={qc}>
      <BrowserRouter>
        <App />
        <FloatingAiChat />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
