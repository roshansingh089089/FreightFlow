import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { FormSearchableSelect } from '../../shared/components/FormSearchableSelect';
import { mailApi } from '../mail/mailApi';
import {
  customerProfileApi,
  type AddressInput,
  type ContactInput,
} from './customerProfileApi';

const contactSchema = z.object({
  name: z.string().trim().min(1),
  email: z.email(),
  phone: z.string().trim().min(1),
  designation: z.string(),
  role: z.string(),
  primary: z.boolean(),
  autoCc: z.boolean(),
  active: z.boolean(),
});
type Customer = { contacts: any[]; extraCc: any[]; primaryContact: any };
export function CustomerUsersPanel({
  id,
  customer,
}: {
  id: string;
  customer: Customer;
}) {
  const qc = useQueryClient(),
    [editing, setEditing] = useState<string | null>(null),
    [error, setError] = useState('');
  const form = useForm<ContactInput>({
    resolver: zodResolver(contactSchema),
    defaultValues: {
      name: '',
      email: '',
      phone: '',
      designation: '',
      role: '',
      primary: false,
      autoCc: false,
      active: true,
    },
  });
  const save = useMutation({
    mutationFn: (value: ContactInput) =>
      editing
        ? customerProfileApi.updateContact(id, editing, value)
        : customerProfileApi.addContact(id, value),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['customer', `/customers/${id}`] });
      setEditing(null);
      form.reset();
      setError('');
    },
    onError: (cause: Error) => setError(cause.message),
  });
  const begin = (contact: any) => {
    setEditing(contact.id);
    form.reset({
      name: contact.name,
      email: contact.email,
      phone: contact.phone,
      designation: contact.designation || '',
      role: contact.contactRole || '',
      primary: contact.primary,
      autoCc: contact.autoCc,
      active: contact.active,
    });
  };
  const [extraEmail, setExtraEmail] = useState(''),
    [extraName, setExtraName] = useState('');
  const cc = useMutation({
    mutationFn: (items: { email: string; displayName: string }[]) =>
      customerProfileApi.updateCc(id, items),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['customer', `/customers/${id}`] });
      setExtraEmail('');
      setExtraName('');
      setError('');
    },
    onError: (cause: Error) => setError(cause.message),
  });
  const currentCc = (customer.extraCc || [])
    .filter((item: any) => item.active)
    .map((item: any) => ({
      email: item.email,
      displayName: item.displayName || '',
    }));
  return (
    <div className="space-y-6">
      <section>
        <h3 className="font-bold mb-3">My Users</h3>
        <div className="space-y-2">
          {customer.contacts?.map((contact: any) => (
            <div
              key={contact.id}
              className="border rounded-lg p-3 flex justify-between gap-3"
            >
              <div>
                <strong>{contact.name}</strong>
                {contact.primary && ' · Primary'}
                {contact.autoCc && ' · Auto CC'}
                {!contact.active && ' · Inactive'}
                <p>
                  {contact.designation || contact.contactRole || 'Contact'} ·{' '}
                  {contact.email} · {contact.phone}
                </p>
              </div>
              <button
                type="button"
                className="btn secondary"
                onClick={() => begin(contact)}
              >
                Edit
              </button>
            </div>
          ))}
        </div>
      </section>
      <form
        className="grid md:grid-cols-2 gap-3"
        onSubmit={form.handleSubmit((value) => save.mutate(value))}
      >
        <h4 className="md:col-span-2 font-semibold">
          {editing ? 'Edit contact' : 'Add contact'}
        </h4>
        <label>
          Name
          <input className="field" {...form.register('name')} />
        </label>
        <label>
          Email
          <input className="field" type="email" {...form.register('email')} />
        </label>
        <label>
          Phone
          <input className="field" {...form.register('phone')} />
        </label>
        <label>
          Position
          <input className="field" {...form.register('designation')} />
        </label>
        <label>
          Role
          <input className="field" {...form.register('role')} />
        </label>
        <div className="flex gap-4 items-center">
          <label>
            <input type="checkbox" {...form.register('primary')} /> Primary
          </label>
          <label>
            <input type="checkbox" {...form.register('autoCc')} /> Auto CC
          </label>
          <label>
            <input type="checkbox" {...form.register('active')} /> Active
          </label>
        </div>
        <div className="md:col-span-2 flex gap-2">
          <button className="btn primary" disabled={save.isPending}>
            {editing ? 'Save contact' : 'Add contact'}
          </button>
          {editing && (
            <button
              type="button"
              className="btn secondary"
              onClick={() => {
                setEditing(null);
                form.reset();
              }}
            >
              Cancel
            </button>
          )}
        </div>
      </form>
      <section>
        <h4 className="font-semibold mb-2">Additional CC addresses</h4>
        {currentCc.map((item: any) => (
          <p key={item.email} className="py-1">
            {item.displayName} &lt;{item.email}&gt;{' '}
            <button
              type="button"
              onClick={() =>
                cc.mutate(currentCc.filter((x: any) => x.email !== item.email))
              }
            >
              Remove
            </button>
          </p>
        ))}
        <div className="flex flex-wrap gap-2">
          <input
            className="field"
            type="email"
            aria-label="Additional CC email"
            placeholder="Email address"
            value={extraEmail}
            onChange={(event) => setExtraEmail(event.target.value)}
          />
          <input
            className="field"
            aria-label="Additional CC name"
            placeholder="Display name"
            value={extraName}
            onChange={(event) => setExtraName(event.target.value)}
          />
          <button
            type="button"
            className="btn secondary"
            disabled={!z.email().safeParse(extraEmail).success || cc.isPending}
            onClick={() =>
              cc.mutate([
                ...currentCc,
                { email: extraEmail, displayName: extraName },
              ])
            }
          >
            Add CC
          </button>
        </div>
      </section>
      {error && (
        <p role="alert" className="text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}

const addressSchema = z.object({
  addressType: z.string().min(1),
  line1: z.string().trim().min(1),
  line2: z.string(),
  city: z.string().trim().min(1),
  state: z.string(),
  postalCode: z.string(),
  country: z.string().trim().min(2),
});
export function CustomerAddressesPanel({
  id,
  addresses,
  country,
}: {
  id: string;
  addresses: any[];
  country: string;
}) {
  const qc = useQueryClient(),
    [editing, setEditing] = useState<string | null>(null),
    [error, setError] = useState('');
  const form = useForm<AddressInput>({
    resolver: zodResolver(addressSchema),
    defaultValues: {
      addressType: 'REGISTERED',
      line1: '',
      line2: '',
      city: '',
      state: '',
      postalCode: '',
      country,
    },
  });
  const save = useMutation({
    mutationFn: (value: AddressInput) =>
      editing
        ? customerProfileApi.updateAddress(id, editing, value)
        : customerProfileApi.addAddress(id, value),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['customer', `/customers/${id}`] });
      setEditing(null);
      form.reset({
        addressType: 'REGISTERED',
        line1: '',
        line2: '',
        city: '',
        state: '',
        postalCode: '',
        country,
      });
      setError('');
    },
    onError: (cause: Error) => setError(cause.message),
  });
  return (
    <div>
      <h3 className="font-bold mb-3">Addresses</h3>
      {addresses?.length ? (
        addresses.map((address) => (
          <div
            key={address.id}
            className="border rounded-lg p-3 mb-2 flex justify-between gap-3"
          >
            <div>
              <strong>{address.addressType}</strong>
              <p>
                {[
                  address.line1,
                  address.line2,
                  address.city,
                  address.state,
                  address.postalCode,
                  address.country,
                ]
                  .filter(Boolean)
                  .join(', ')}
              </p>
            </div>
            <button
              type="button"
              className="btn secondary"
              onClick={() => {
                setEditing(address.id);
                form.reset({
                  addressType: address.addressType,
                  line1: address.line1,
                  line2: address.line2 || '',
                  city: address.city,
                  state: address.state || '',
                  postalCode: address.postalCode || '',
                  country: address.country,
                });
              }}
            >
              Edit
            </button>
          </div>
        ))
      ) : (
        <p>No addresses yet.</p>
      )}
      <form
        className="grid md:grid-cols-2 gap-3 mt-5"
        onSubmit={form.handleSubmit((value) => save.mutate(value))}
      >
        <h4 className="md:col-span-2 font-semibold">
          {editing ? 'Edit address' : 'Add address'}
        </h4>
        <FormSearchableSelect form={form} name="addressType" label="Type" options={[
          { value: 'REGISTERED', label: 'Registered' },
          { value: 'BILLING', label: 'Billing' },
          { value: 'BUSINESS', label: 'Business' },
        ]} />
        <label>
          Address line 1<input className="field" {...form.register('line1')} />
        </label>
        <label>
          Address line 2<input className="field" {...form.register('line2')} />
        </label>
        <label>
          City
          <input className="field" {...form.register('city')} />
        </label>
        <label>
          State
          <input className="field" {...form.register('state')} />
        </label>
        <label>
          Postal code
          <input className="field" {...form.register('postalCode')} />
        </label>
        <label>
          Country
          <input className="field" {...form.register('country')} />
        </label>
        <div className="md:col-span-2 flex gap-2">
          <button className="btn primary" disabled={save.isPending}>
            {editing ? 'Save address' : 'Add address'}
          </button>
          {editing && (
            <button
              type="button"
              className="btn secondary"
              onClick={() => {
                setEditing(null);
                form.reset();
              }}
            >
              Cancel
            </button>
          )}
        </div>
      </form>
      {error && (
        <p role="alert" className="text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}

export function CustomerMailPanel({ id }: { id: string }) {
  const history = useQuery({
    queryKey: ['customer-sent-mail', id],
    queryFn: () => mailApi.customerHistory(id),
    refetchInterval: 5000,
  });
  const conversations = useQuery({
    queryKey: ['customer-conversations', id],
    queryFn: () => mailApi.customerConversations(id),
  });
  const mailboxThreads = useQuery({
    queryKey: ['customer-mail-threads', id],
    queryFn: () => mailApi.customerThreads(id),
  });
  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h3 className="font-bold">Customer mail</h3>
        <Link className="btn primary" to={`/mail/compose?customerId=${id}`}>
          Compose email
        </Link>
      </div>
      {history.isLoading && <p>Loading mail history…</p>}
      {history.error && <p role="alert">{(history.error as Error).message}</p>}
      {history.data?.content.length === 0 && (
        <p>No stored outgoing mail for this customer.</p>
      )}
      {history.data?.content.map((message) => (
        <div className="border-b py-3" key={message.id}>
          <strong>{message.subject}</strong>
          <p>
            To {message.to.map((item) => item.email).join(', ')} ·{' '}
            {message.status} · {new Date(message.createdAt).toLocaleString()}
          </p>
          {message.errorCode && <small>{message.errorCode}</small>}
        </div>
      ))}
      <h4 className="font-semibold mt-6 mb-2">Mailbox threads</h4>
      {mailboxThreads.isLoading && <p>Loading mailbox threads…</p>}
      {mailboxThreads.error && (
        <p role="alert">{(mailboxThreads.error as Error).message}</p>
      )}
      {mailboxThreads.data?.content.length === 0 && (
        <p>No mailbox threads linked yet.</p>
      )}
      {mailboxThreads.data?.content.map((thread) => (
        <div className="border-b py-3" key={thread.id}>
          <Link to={`/mail/threads/${thread.id}`} className="font-semibold">
            {thread.normalizedSubject || '(No subject)'}
          </Link>
          <p>
            Last activity {new Date(thread.lastActivityAt).toLocaleString()}
          </p>
        </div>
      ))}
      <h4 className="font-semibold mt-6 mb-2">Conversations</h4>
      <p className="text-sm text-slate-500">
        Groups linked inbox messages and stored outgoing mail when message
        headers are available.
      </p>
      {conversations.isLoading && <p>Loading conversations…</p>}
      {conversations.error && (
        <p role="alert">{(conversations.error as Error).message}</p>
      )}
      {conversations.data?.length === 0 && <p>No linked conversations yet.</p>}
      {conversations.data?.map((group) => (
        <div key={group.key} className="border rounded-lg p-3 my-2">
          <strong>{group.messages.at(-1)?.subject || '(No subject)'}</strong>
          {group.messages.map((message) => (
            <p key={message.id} className="text-sm py-1">
              {message.direction === 'INBOUND' ? 'Received' : 'Sent'} ·{' '}
              {message.fromAddress || 'Unknown sender'} →{' '}
              {message.toAddress || 'Unknown recipient'} · {message.status} ·{' '}
              {new Date(message.at).toLocaleString()}
            </p>
          ))}
        </div>
      ))}
    </div>
  );
}

export function CustomerActivityPanel({ id }: { id: string }) {
  const activity = useQuery({
    queryKey: ['customer-activity', id],
    queryFn: () => customerProfileApi.activity(id),
  });
  return (
    <div>
      <h3 className="font-bold mb-3">Activity</h3>
      {activity.isLoading && <p>Loading activity…</p>}
      {activity.error && (
        <p role="alert">{(activity.error as Error).message}</p>
      )}
      {activity.data?.length === 0 && <p>No activity recorded yet.</p>}
      {activity.data?.map((item) => (
        <p key={item.id} className="border-b py-2">
          {item.action.replaceAll('_', ' ')} ·{' '}
          {new Date(item.createdAt).toLocaleString()}
        </p>
      ))}
    </div>
  );
}
