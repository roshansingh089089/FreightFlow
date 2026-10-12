export function apiUrl(path: string): string {
  return `/api${path.startsWith('/') ? path : `/${path}`}`;
}

export async function apiFetch(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const response = await fetch(apiUrl('/auth/csrf'), {
      credentials: 'include',
    });
    if (!response.ok)
      throw new Error('Could not verify this request. Please retry.');
    const { token } = (await response.json()) as { token: string };
    headers.set('X-XSRF-TOKEN', token);
  }
  return fetch(apiUrl(path), { ...options, headers, credentials: 'include' });
}
