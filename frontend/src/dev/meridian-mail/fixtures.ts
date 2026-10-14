/** Deterministic preview fixtures. These records never reach FreightFlow APIs. */
export type FolderId = 'inbox' | 'sent' | 'drafts';
export interface PreviewFolder {
  id: FolderId;
  label: string;
  unread: number;
}
export interface PreviewAttachment {
  name: string;
  size: string;
  kind: string;
}
export interface PreviewMessage {
  id: string;
  folder: FolderId;
  sender: string;
  email: string;
  to: string;
  cc?: string;
  subject: string;
  preview: string;
  body: string[];
  time: string;
  date: string;
  unread: boolean;
  customer?: string;
  customerId?: string;
  enquiryId?: string;
  ai?: { title: string; detail: string; extracted?: Record<string, string> };
  attachments?: PreviewAttachment[];
  replies?: { author: string; date: string; body: string }[];
  quotationId?: string;
}
export const folders: PreviewFolder[] = [
  { id: 'inbox', label: 'Inbox', unread: 3 },
  { id: 'sent', label: 'Sent Items', unread: 0 },
  { id: 'drafts', label: 'Drafts', unread: 0 },
];
export const messages: PreviewMessage[] = [
  {
    id: 'unknown',
    folder: 'inbox',
    sender: 'Pimchanok Srisuk',
    email: 'pimchanok@goldenorchard.co.th',
    to: 'operations@freightflow.example',
    subject: 'Introduction & rate enquiry – Bangkok to Chennai',
    preview:
      'Dear team, We are an exporter of canned fruit based in Bangkok...',
    body: [
      'Dear team,',
      'We are an exporter of canned fruit based in Bangkok. Please share your best all-in rate for one 20GP container from Bangkok to Chennai.',
      'We look forward to working with you.',
      'Kind regards,\nPimchanok Srisuk\nGolden Orchard Co., Ltd.',
    ],
    time: '08:31',
    date: 'Tue 13 Oct 2026',
    unread: true,
    ai: {
      title: 'New sender – not a customer yet',
      detail:
        'AI found name, company, email and shipment request in this email.',
      extracted: {
        Name: 'Pimchanok Srisuk',
        Company: 'Golden Orchard Co., Ltd.',
        Email: 'pimchanok@goldenorchard.co.th',
        Origin: 'Bangkok',
        Destination: 'Chennai',
      },
    },
  },
  {
    id: 'rate',
    folder: 'inbox',
    sender: 'Somchai Phongsri',
    email: 'somchai@pacificcargo.example',
    to: 'operations@freightflow.example',
    subject: 'RFQ: 2×40HC Laem Chabang → Jebel Ali, early November',
    preview: 'Hi team, Please share your best all-in rate for 2×40HC...',
    body: [
      'Hi team,',
      'Please share your best all-in rate for 2×40HC from Laem Chabang to Jebel Ali, ready early November.',
      'Best,\nSomchai',
    ],
    time: '10:42',
    date: 'Tue 13 Oct 2026',
    unread: true,
    customer: 'Pacific Cargo',
    customerId: 'fixture-customer-1',
    ai: {
      title: 'Rate request detected',
      detail: '2×40HC · Laem Chabang → Jebel Ali · ready early November.',
    },
    attachments: [
      { name: 'Shipment requirements.pdf', size: '128 KB', kind: 'PDF' },
    ],
  },
  {
    id: 'accepted',
    folder: 'inbox',
    sender: 'Nguyen Thi Lan',
    email: 'lan@vietfreight.example',
    to: 'operations@freightflow.example',
    subject: 'Re: Quotation FF-2410-131 – OK, please proceed',
    preview: 'Dear team, We accept your quotation. Please proceed...',
    body: [
      'Dear team,',
      'We accept your quotation FF-2410-131. Please proceed with the booking.',
      'Regards,\nLan',
    ],
    time: '09:15',
    date: 'Tue 13 Oct 2026',
    unread: true,
    customer: 'Viet Freight',
    customerId: 'fixture-customer-2',
    quotationId: 'FF-2410-131',
    ai: {
      title: 'Rate accepted – customer wants to proceed',
      detail: 'Quotation FF-2410-131 is referenced in this reply.',
    },
    replies: [
      {
        author: 'FreightFlow Operations',
        date: 'Mon 12 Oct 2026',
        body: 'Please find our quotation FF-2410-131 attached.',
      },
    ],
  },
  {
    id: 'followup',
    folder: 'inbox',
    sender: 'Rahul Verma',
    email: 'rahul@northstar.example',
    to: 'operations@freightflow.example',
    subject: 'Rate needed – 2×40HC Laem Chabang to Mumbai',
    preview: 'Hello, We need a rate for a shipment next month...',
    body: [
      'Hello,',
      'We need a rate for a shipment next month. Please advise sailing options.',
    ],
    time: '08:05',
    date: 'Tue 13 Oct 2026',
    unread: false,
    customer: 'Northstar Logistics',
    customerId: 'fixture-customer-3',
  },
  ...Array.from(
    { length: 12 },
    (_, i): PreviewMessage => ({
      id: `fixture-${i + 1}`,
      folder: i % 5 === 0 ? 'sent' : 'inbox',
      sender: ['Mei Tan', 'Arun Kapoor', 'Nadia Hassan', 'Daniel Cho'][i % 4],
      email: `contact${i + 1}@example.test`,
      to: 'operations@freightflow.example',
      subject: [
        'Schedule confirmation – Chennai departure',
        'Shipping documents for review',
        'Revised sailing plan for November',
        'Container availability update',
      ][i % 4],
      preview: 'Please find the latest details for this shipment attached...',
      body: [
        'Dear team,',
        'Please find the latest details for this shipment. We will update you when the carrier confirms.',
        'Kind regards',
      ],
      time: `${String(17 - Math.floor(i / 3)).padStart(2, '0')}:${String((i * 13) % 60).padStart(2, '0')}`,
      date: 'Mon 12 Oct 2026',
      unread: false,
      customer: 'Preview customer',
    }),
  ),
];
