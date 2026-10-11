import { apiFetch } from '../../api';

export type ContactInput = {
  name: string;
  email: string;
  phone: string;
  designation: string;
  role: string;
  primary: boolean;
  autoCc: boolean;
  active: boolean;
};
export type ExtraCcInput = { email: string; displayName: string };
export type AddressInput = {
  addressType: string;
  line1: string;
  line2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
};
async function request<T>(
  path: string,
  method: string,
  body?: unknown,
): Promise<T> {
  const response = await apiFetch(path, {
    method,
    headers:
      body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || `Request failed (${response.status})`);
  }
  return response.status === 204
    ? (undefined as T)
    : (response.json() as Promise<T>);
}
export const customerProfileApi = {
  addContact: (id: string, body: ContactInput) =>
    request(`/customers/${id}/contacts`, 'POST', body),
  updateContact: (id: string, contactId: string, body: ContactInput) =>
    request(`/customers/${id}/contacts/${contactId}`, 'PUT', body),
  updateCc: (id: string, body: ExtraCcInput[]) =>
    request(`/customers/${id}/cc`, 'PUT', body),
  addAddress: (id: string, body: AddressInput) =>
    request(`/customers/${id}/addresses`, 'POST', body),
  updateAddress: (id: string, addressId: string, body: AddressInput) =>
    request(`/customers/${id}/addresses/${addressId}`, 'PUT', body),
  archive: (id: string) => request(`/customers/${id}/archive`, 'POST'),
  activity: (id: string) =>
    request<
      { id: string; action: string; createdAt: string; detail: string | null }[]
    >(`/customers/${id}/activity`, 'GET'),
};
