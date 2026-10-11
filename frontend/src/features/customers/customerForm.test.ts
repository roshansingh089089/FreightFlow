import { describe, expect, it } from 'vitest';
import { customerPayload, customerSchema } from './customerForm';

const validCustomer = {
  companyName: 'Meridian Exports',
  customerType: 'EXPORTER',
  country: 'India',
  contactName: 'Asha Patel',
  contactEmail: 'asha@example.com',
  contactPhone: '+91 9876543210',
  contactPosition: 'Operations Manager',
  addressLine1: '  10 Port Road  ',
  addressCity: '  Mumbai  ',
  gstin: '27ABCDE1234F1Z5',
  iec: 'ABCDE1234F',
  status: 'ACTIVE' as const,
  creditTermsDays: '30',
  creditLimit: '125000.50',
  salesOwnerUserId: 'owner-1',
};

describe('customer form', () => {
  it('requires essential company and contact values', () => {
    const result = customerSchema.safeParse({
      ...validCustomer,
      companyName: '',
      contactEmail: 'invalid',
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.map((issue) => issue.path[0])).toEqual(
        expect.arrayContaining(['companyName', 'contactEmail']),
      );
    }
  });

  it('requires a city when the business address is provided', () => {
    const result = customerSchema.safeParse({
      ...validCustomer,
      addressCity: '',
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].path).toEqual(['addressCity']);
    }
  });

  it('maps editable fields to the existing customer DTO', () => {
    const parsed = customerSchema.parse(validCustomer);
    expect(customerPayload(parsed)).toMatchObject({
      companyName: 'Meridian Exports',
      customerType: 'EXPORTER',
      country: 'India',
      primaryContact: {
        name: 'Asha Patel',
        email: 'asha@example.com',
        phone: '+91 9876543210',
        designation: 'Operations Manager',
      },
      addresses: [
        {
          addressType: 'BUSINESS',
          line1: '10 Port Road',
          city: 'Mumbai',
          country: 'India',
        },
      ],
      creditTermsDays: 30,
      creditLimit: '125000.50',
      salesOwnerUserId: 'owner-1',
    });
  });
});
