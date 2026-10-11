import { QueryClient, useQuery } from '@tanstack/react-query';
import { apiFetch } from '../../api';
export const qc = new QueryClient();
export const api = async (path: string, opts?: RequestInit) => {
  const r = await apiFetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...opts,
  });
  if (!r.ok) {
    let e: any;
    try {
      e = await r.json();
    } catch {}
    throw new Error(e?.message || `Request failed (${r.status})`);
  }
  return r.status === 204 ? null : r.json();
};
export const post = (p: string, data: any) =>
  api(p, { method: 'POST', body: JSON.stringify(data) });
export const put = (p: string, data: any) =>
  api(p, { method: 'PUT', body: JSON.stringify(data) });
export const useData = (key: string, path: string) =>
  useQuery({ queryKey: [key, path], queryFn: () => api(path) });
export const toast = (message: string) =>
  window.dispatchEvent(new CustomEvent('ff-toast', { detail: message }));
export const fmt = (n: any) =>
  new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(
    Number(n || 0),
  );
export const date = (v: any) => (v ? new Date(v).toLocaleDateString() : '—');
