import { useQuery, useQueryClient } from '@tanstack/react-query';
import React, { useEffect, useState } from 'react';
import {
  Heading,
  PageContent,
  State,
} from '../../shared/components/PageElements';
import { api, post, useData } from '../../shared/lib/workspace';
import {
  getCurrentMailConnection,
  mailConnectionKey,
} from '../mail/mailConnection';
export function SettingsPage() {
  const q = useData('me', '/auth/me');
  const queryClient = useQueryClient();
  const connectionQuery = useQuery({
    queryKey: mailConnectionKey,
    queryFn: getCurrentMailConnection,
  });
  const connection = connectionQuery.data;
  const [provider, setProvider] = useState<'HOSTINGER' | 'GENERIC'>(
    'HOSTINGER',
  );
  const [form, setForm] = useState({
    emailAddress: '',
    password: '',
    imapHost: 'imap.hostinger.com',
    imapPort: '993',
    imapSecurityMode: 'IMPLICIT_TLS',
    imapUsername: '',
    imapPassword: '',
    smtpHost: 'smtp.hostinger.com',
    smtpPort: '465',
    smtpSecurityMode: 'IMPLICIT_TLS',
    smtpUsername: '',
    smtpPassword: '',
    smtpFrom: '',
  });
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    if (connection?.providerType === 'IMAP_SMTP') {
      const hostinger =
        connection.imapHost === 'imap.hostinger.com' &&
        connection.smtpHost === 'smtp.hostinger.com';
      setProvider(hostinger ? 'HOSTINGER' : 'GENERIC');
      setForm((previous) => ({
        ...previous,
        emailAddress: connection.emailAddress || '',
        imapHost: connection.imapHost || '',
        imapPort: String(connection.imapPort || 993),
        imapSecurityMode: connection.imapSecurityMode || 'IMPLICIT_TLS',
        smtpHost: connection.smtpHost || '',
        smtpPort: String(connection.smtpPort || 465),
        smtpSecurityMode: connection.smtpSecurityMode || 'IMPLICIT_TLS',
      }));
    }
  }, [connection?.id, connection?.providerType, connection?.emailAddress]);
  const refreshConnection = async () => {
    await queryClient.invalidateQueries({ queryKey: mailConnectionKey });
    await queryClient.fetchQuery({
      queryKey: mailConnectionKey,
      queryFn: getCurrentMailConnection,
      staleTime: 0,
    });
  };
  const clearMailCache = () => {
    queryClient.removeQueries({ queryKey: ['mail', 'counts'] });
    queryClient.removeQueries({ queryKey: ['mail', 'messages'] });
    queryClient.removeQueries({ queryKey: ['mail', 'message'] });
  };
  const change = (name: string, value: string) => {
    setForm((current) => ({ ...current, [name]: value }));
    setFeedback('');
  };
  const choose = (value: 'HOSTINGER' | 'GENERIC') => {
    setProvider(value);
    setFeedback('');
    setError('');
    setForm((current) => ({
      ...current,
      password: '',
      imapPassword: '',
      smtpPassword: '',
      ...(value === 'HOSTINGER'
        ? {
            imapHost: 'imap.hostinger.com',
            imapPort: '993',
            imapSecurityMode: 'IMPLICIT_TLS',
            smtpHost: 'smtp.hostinger.com',
            smtpPort: '465',
            smtpSecurityMode: 'IMPLICIT_TLS',
          }
        : {
            imapHost: '',
            imapPort: '993',
            imapSecurityMode: 'IMPLICIT_TLS',
            smtpHost: '',
            smtpPort: '465',
            smtpSecurityMode: 'IMPLICIT_TLS',
          }),
    }));
  };
  const clearPasswords = () =>
    setForm((current) => ({
      ...current,
      password: '',
      imapPassword: '',
      smtpPassword: '',
    }));
  const payload = () => ({
    providerType: 'IMAP_SMTP',
    emailAddress: form.emailAddress.trim(),
    imapHost: form.imapHost.trim(),
    imapPort: Number(form.imapPort),
    imapSecurityMode: form.imapSecurityMode,
    imapUsername:
      provider === 'HOSTINGER'
        ? form.emailAddress.trim()
        : form.imapUsername.trim(),
    imapPassword: provider === 'HOSTINGER' ? form.password : form.imapPassword,
    smtpHost: form.smtpHost.trim(),
    smtpPort: Number(form.smtpPort),
    smtpSecurityMode: form.smtpSecurityMode,
    smtpUsername:
      provider === 'HOSTINGER'
        ? form.smtpUsername.trim() || form.emailAddress.trim()
        : form.smtpUsername.trim(),
    smtpPassword: provider === 'HOSTINGER' ? form.password : form.smtpPassword,
    smtpFrom: form.smtpFrom.trim() || form.emailAddress.trim(),
  });
  const test = async () => {
    setBusy(true);
    setError('');
    setFeedback('');
    try {
      const result = await post('/mail/connections/test', payload());
      setFeedback(
        result.imapOk && result.smtpOk
          ? 'Connection successful. Save to use this mailbox. Reenter the password before saving.'
          : result.message,
      );
    } catch (e: any) {
      setError(e.message);
    } finally {
      clearPasswords();
      setBusy(false);
    }
  };
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    setFeedback('');
    try {
      const data = await post('/mail/connections', payload());
      clearMailCache();
      await refreshConnection();
      setFeedback(
        data.active
          ? `${provider === 'HOSTINGER' ? 'Hostinger' : 'IMAP/SMTP'} mailbox connected.`
          : 'Connection saved. Save successfully to activate this mailbox.',
      );
    } catch (e: any) {
      setError(e.message);
    } finally {
      clearPasswords();
      setBusy(false);
    }
  };
  const disconnect = async () => {
    if (!connection?.id) return;
    setBusy(true);
    setError('');
    try {
      await api(`/mail/connections/${connection.id}`, { method: 'DELETE' });
      clearMailCache();
      await refreshConnection();
      setFeedback('Mailbox disconnected. Imported messages remain stored.');
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  const sync = async () => {
    if (!connection?.active) return;
    setBusy(true);
    setError('');
    try {
      const result = await api('/mail/sync', { method: 'POST' });
      await refreshConnection();
      await queryClient.invalidateQueries({
        queryKey: ['mail', 'messages', connection.id],
      });
      setFeedback(
        `Fetched ${result.fetched}, added ${result.inserted}, already stored ${result.skippedDuplicates}, failed ${result.failed}.`,
      );
    } catch (e: any) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };
  const field = (
    key: keyof typeof form,
    label: string,
    type = 'text',
    required = true,
  ) => (
    <label key={key}>
      <span className="label">{label}</span>
      <input
        className="field"
        type={type}
        value={form[key]}
        onChange={(event) => change(key, event.target.value)}
        required={required}
        autoComplete={type === 'password' ? 'new-password' : undefined}
      />
    </label>
  );
  const security = (
    key: 'imapSecurityMode' | 'smtpSecurityMode',
    label: string,
  ) => (
    <label key={key}>
      <span className="label">{label}</span>
      <select
        className="field"
        value={form[key]}
        onChange={(event) => change(key, event.target.value)}
      >
        <option value="IMPLICIT_TLS">Implicit TLS</option>
        <option value="STARTTLS">STARTTLS</option>
      </select>
    </label>
  );
  const providerLabel =
    connection?.providerType === 'GMAIL_API'
      ? 'Gmail'
      : connection?.imapHost === 'imap.hostinger.com' &&
          connection?.smtpHost === 'smtp.hostinger.com'
        ? 'Hostinger Email'
        : 'Other IMAP/SMTP';
  return (
    <>
      <Heading
        title="Settings"
        subtitle="Your profile and mailbox connection."
      />
      <PageContent>
        <State query={q}>
          <div className="card p-6 max-w-3xl">
            <h2 className="font-bold mb-3">Profile</h2>
            <p>Name: {q.data?.fullName}</p>
            <p>Email: {q.data?.email}</p>
            <p>Company: {q.data?.companyName}</p>
            <h2 className="font-bold mt-8 mb-3">Mailbox</h2>
            {connection && (
              <div className="mailbox-status">
                <strong>
                  {connection.active ? 'Connected as' : 'Mailbox'}:{' '}
                  {connection.emailAddress}
                </strong>
                <span>Provider: {providerLabel}</span>
                <span>
                  Status: {connection.status}
                  {connection.active ? ' · Active' : ''}
                </span>
                {connection.lastSyncedAt && (
                  <small>
                    Last synced:{' '}
                    {new Date(connection.lastSyncedAt).toLocaleString()}
                  </small>
                )}
              </div>
            )}
            <label className="block mt-4">
              <span className="label">Provider</span>
              <select
                className="field"
                value={provider}
                onChange={(event) =>
                  choose(event.target.value as 'HOSTINGER' | 'GENERIC')
                }
              >
                <option value="HOSTINGER">Hostinger Email</option>
                <option value="GENERIC">Other IMAP/SMTP</option>
              </select>
            </label>
            <form className="mailbox-form mt-4" onSubmit={save}>
              <div className="mailbox-fields">
                {field('emailAddress', 'Email address', 'email')}
                {provider === 'HOSTINGER'
                  ? field('password', 'Mailbox password', 'password')
                  : null}
                {provider === 'GENERIC' ? (
                  <>
                    {field('imapUsername', 'IMAP username')}
                    {field('imapPassword', 'IMAP password', 'password')}
                    {field('smtpUsername', 'SMTP username')}
                    {field('smtpPassword', 'SMTP password', 'password')}
                  </>
                ) : null}
              </div>
              {provider === 'HOSTINGER' && (
                <p className="mailbox-note mt-4">
                  Use the password for your Hostinger Business Email mailbox.
                  Settings are filled in for IMAP and SMTP.
                </p>
              )}
              <details
                key={provider}
                className="mailbox-advanced mt-4"
                open={provider === 'GENERIC' ? true : undefined}
              >
                <summary>Advanced settings</summary>
                <div className="mailbox-fields">
                  {field('imapHost', 'IMAP host')}
                  {field('imapPort', 'IMAP port', 'number')}
                  {security('imapSecurityMode', 'IMAP security')}
                  {field('smtpHost', 'SMTP host')}
                  {field('smtpPort', 'SMTP port', 'number')}
                  {security('smtpSecurityMode', 'SMTP security')}
                  {provider === 'HOSTINGER'
                    ? field(
                        'smtpUsername',
                        'SMTP username (defaults to email)',
                        'text',
                        false,
                      )
                    : null}
                  {field(
                    'smtpFrom',
                    'From address (defaults to email)',
                    'email',
                    false,
                  )}
                </div>
              </details>
              <div className="mailbox-actions">
                <button
                  className="btn secondary"
                  disabled={busy}
                  type="button"
                  onClick={test}
                >
                  Test connection
                </button>
                <button className="btn primary" disabled={busy} type="submit">
                  Save connection
                </button>
                <button
                  className="btn secondary"
                  disabled={busy || !connection?.active}
                  type="button"
                  onClick={sync}
                >
                  Sync mail
                </button>
                <button
                  className="btn secondary"
                  disabled={
                    busy ||
                    !connection?.id ||
                    connection.status === 'DISCONNECTED'
                  }
                  type="button"
                  onClick={disconnect}
                >
                  Disconnect
                </button>
              </div>
            </form>
            {feedback && (
              <p className="mt-4 text-green-700" role="status">
                {feedback}
              </p>
            )}
            {error && (
              <p className="mt-4 text-red-600" role="alert">
                {error}
              </p>
            )}
          </div>
        </State>
      </PageContent>
    </>
  );
}
