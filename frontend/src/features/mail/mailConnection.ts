import { apiFetch } from '../../api';

export type MailConnection = {
  id: string;
  providerType: string;
  emailAddress: string;
  active: boolean;
  status: string;
  lastSyncedAt?: string;
  imapHost?: string;
  imapPort?: number;
  imapSecurityMode?: string;
  smtpHost?: string;
  smtpPort?: number;
  smtpSecurityMode?: string;
};
export const mailConnectionKey = ['mail', 'connection', 'current'] as const;
export const mailCountsKey = (connectionId: string | null | undefined) =>
  ['mail', 'counts', connectionId] as const;
export type MailCounts = { inbox: number; unread: number };

export async function getMailCounts(): Promise<MailCounts> {
  const response = await apiFetch('/mail/counts');
  if (!response.ok)
    throw new Error(`Could not load mail counts (${response.status}).`);
  return response.json();
}

export async function getCurrentMailConnection(): Promise<MailConnection | null> {
  const response = await apiFetch('/mail/connections/current');
  if (response.status === 204) return null;
  if (!response.ok)
    throw new Error(`Could not load mailbox connection (${response.status}).`);
  return response.json();
}
