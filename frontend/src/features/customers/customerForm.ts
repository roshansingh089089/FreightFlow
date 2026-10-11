import { z } from 'zod';

export const customerSchema = z
  .object({
    companyName: z.string().min(2, 'Company name is required'),
    customerType: z.string().min(1),
    country: z.string().min(2),
    contactName: z.string().min(2),
    contactEmail: z.email(),
    contactPhone: z.string().min(5),
    contactPosition: z.string().optional(),
    addressLine1: z.string().optional(),
    addressCity: z.string().optional(),
    gstin: z.string().optional(),
    iec: z.string().optional(),
    status: z.enum(['ACTIVE', 'INACTIVE']).optional(),
    creditTermsDays: z.string().optional(),
    creditLimit: z.string().optional(),
    salesOwnerUserId: z.string().optional(),
  })
  .refine((v) => !v.addressLine1?.trim() || !!v.addressCity?.trim(), {
    path: ['addressCity'],
    message: 'City is required when adding an address',
  })
  .refine((v) => !v.creditTermsDays || /^\d+$/.test(v.creditTermsDays), {
    path: ['creditTermsDays'],
    message: 'Enter whole days',
  })
  .refine((v) => !v.creditLimit || /^\d+(\.\d{1,4})?$/.test(v.creditLimit), {
    path: ['creditLimit'],
    message: 'Enter a nonnegative amount',
  });

export function customerPayload(v: z.infer<typeof customerSchema>) {
  return {
    companyName: v.companyName,
    customerType: v.customerType,
    country: v.country,
    gstin: v.gstin,
    iec: v.iec,
    primaryContact: {
      name: v.contactName,
      email: v.contactEmail,
      phone: v.contactPhone,
      designation: v.contactPosition || null,
    },
    ...(v.addressLine1?.trim()
      ? {
          addresses: [
            {
              addressType: 'BUSINESS',
              line1: v.addressLine1.trim(),
              city: v.addressCity?.trim(),
              country: v.country,
            },
          ],
        }
      : {}),
    status: v.status || 'ACTIVE',
    ...(v.creditTermsDays
      ? { creditTermsDays: Number(v.creditTermsDays) }
      : {}),
    ...(v.creditLimit ? { creditLimit: v.creditLimit } : {}),
    ...(v.salesOwnerUserId ? { salesOwnerUserId: v.salesOwnerUserId } : {}),
  };
}
