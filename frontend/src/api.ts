const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();
const apiBaseUrl = configuredBaseUrl
  ? configuredBaseUrl.replace(/\/+$/, '')
  : import.meta.env.DEV ? 'http://localhost:8080' : null;

export function apiUrl(path: string): string {
  if (!apiBaseUrl) {
    throw new Error('VITE_API_BASE_URL is required for production API requests');
  }
  return `${apiBaseUrl}/api${path.startsWith('/') ? path : `/${path}`}`;
}

export function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  return fetch(apiUrl(path), { ...options, credentials: 'include' });
}
