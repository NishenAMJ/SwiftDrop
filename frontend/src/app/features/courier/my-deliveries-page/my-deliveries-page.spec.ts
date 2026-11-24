import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import {
  CourierProfile,
  DeliveryAssignment,
  DeliveryIssue,
  DeliveryPage,
} from '../courier-delivery.models';
import { CourierDeliveryService } from '../courier-delivery.service';
import { MyDeliveriesPage } from './my-deliveries-page';

const profile = {
  id: 'courier-1',
  identityUserId: 'user-1',
  fullName: 'Nimal Perera',
  categoryName: 'Motorcycle',
  status: 'AVAILABLE',
} as CourierProfile;

function assignment(status: DeliveryAssignment['status'], id = 'assignment-1'): DeliveryAssignment {
  return {
    id,
    parcelReference: 'SWD-2026-000201',
    courierId: 'courier-1',
    courierName: 'Nimal Perera',
    categoryCode: 'MOTORCYCLE',
    assignedBy: 'Dispatcher One',
    status,
    notes: null,
    assignedAt: '2026-11-20T09:00:00Z',
    completedAt: null,
  } as DeliveryAssignment;
}

function page<T = DeliveryAssignment>(content: T[]): DeliveryPage<T> {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    size: 50,
    number: 0,
    first: true,
    last: true,
    empty: content.length === 0,
  };
}

function issue(status: DeliveryIssue['status']): DeliveryIssue {
  return {
    id: 'issue-1',
    courierId: 'courier-1',
    courierName: 'Nimal Perera',
    assignmentId: 'assignment-1',
    parcelReference: 'SWD-2026-000206',
    issueType: 'DAMAGED_PARCEL',
    description: 'Box crushed during pickup',
    status,
    reportedAt: '2026-11-20T09:30:00Z',
    reviewedBy: status === 'OPEN' ? null : 'Dispatcher One',
    resolutionNotes: status === 'OPEN' ? null : 'Customer phoned, replacement sent from head office',
    reviewedAt: null,
  };
}

describe('MyDeliveriesPage', () => {
  let service: {
    getCourierByIdentity: ReturnType<typeof vi.fn>;
    listAssignments: ReturnType<typeof vi.fn>;
    updateAssignmentStatus: ReturnType<typeof vi.fn>;
    recordProof: ReturnType<typeof vi.fn>;
    recordFailedAttempt: ReturnType<typeof vi.fn>;
    reportIssue: ReturnType<typeof vi.fn>;
    listIssues: ReturnType<typeof vi.fn>;
  };

  async function create(
    assignments: DeliveryAssignment[],
    issues: DeliveryIssue[] = [],
  ): Promise<ComponentFixture<MyDeliveriesPage>> {
    service = {
      listIssues: vi.fn().mockReturnValue(of(page<DeliveryIssue>(issues))),
      getCourierByIdentity: vi.fn().mockReturnValue(of(profile)),
      listAssignments: vi.fn().mockReturnValue(of(page(assignments))),
      updateAssignmentStatus: vi.fn().mockReturnValue(of(assignment('PICKED_UP'))),
      recordProof: vi.fn().mockReturnValue(of({})),
      recordFailedAttempt: vi.fn().mockReturnValue(of({})),
      reportIssue: vi.fn().mockReturnValue(of({})),
    };
    await TestBed.configureTestingModule({
      imports: [MyDeliveriesPage],
      providers: [
        { provide: CourierDeliveryService, useValue: service },
        { provide: AuthService, useValue: { user: signal({ id: 'user-1', fullName: 'Nimal Perera' }) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(MyDeliveriesPage);
    fixture.detectChanges();
    return fixture;
  }

  it('loads the courier profile and only their own assignments', async () => {
    const fixture = await create([assignment('ASSIGNED')]);
    expect(service.getCourierByIdentity).toHaveBeenCalledWith('user-1');
    expect(service.listAssignments).toHaveBeenCalledWith({ courierId: 'courier-1', size: 50 });
    expect(fixture.nativeElement.textContent).toContain('Mark as picked up');
  });

  it('shows the courier the problems they reported, with their status as text', async () => {
    const fixture = await create([assignment('ASSIGNED')], [issue('OPEN')]);

    expect(service.listIssues).toHaveBeenCalledWith({ courierId: 'courier-1', size: 10 });
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Your reported problems');
    expect(text).toContain('Damaged parcel');
    expect(text).toContain('SWD-2026-000206');
    expect(text).toContain('Open');
  });

  it('shows when a problem has been dealt with but never the dispatcher\'s internal notes', async () => {
    const fixture = await create([assignment('ASSIGNED')], [issue('RESOLVED')]);

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Resolved');
    expect(text).not.toContain('replacement sent from head office');
    expect(text).not.toContain('Box crushed during pickup');
  });

  it('shows no problems section when none were reported', async () => {
    const fixture = await create([assignment('ASSIGNED')], []);
    expect(fixture.nativeElement.textContent).not.toContain('Your reported problems');
  });

  it('refreshes the list after a new problem is reported', async () => {
    const fixture = await create([assignment('ASSIGNED')]);
    const component = fixture.componentInstance as any;
    const parcel = assignment('ASSIGNED');
    component.openPanel(parcel, 'issue');
    component.issueForm.setValue({ issueType: 'OTHER', description: 'Flat tyre' });
    service.listIssues.mockClear();

    component.submitIssue(parcel);

    expect(service.listIssues).toHaveBeenCalledWith({ courierId: 'courier-1', size: 10 });
  });

  it('keeps the deliveries usable when the problems list cannot be loaded', async () => {
    const fixture = await create([assignment('ASSIGNED')]);
    service.listIssues.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    (fixture.componentInstance as any).loadIssues('courier-1');
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('could not be loaded');
    expect(text).toContain('Mark as picked up');
  });

  it('moves an assigned parcel to picked up', async () => {
    const fixture = await create([assignment('ASSIGNED')]);
    (fixture.componentInstance as any).advance(assignment('ASSIGNED'));
    expect(service.updateAssignmentStatus).toHaveBeenCalledWith('assignment-1', 'PICKED_UP');
  });

  it('keeps Delivered and Delivery failed as separate actions only when out for delivery', async () => {
    const early = await create([assignment('PICKED_UP')]);
    expect(early.nativeElement.textContent).not.toContain('Delivery failed');
    TestBed.resetTestingModule();

    const late = await create([assignment('OUT_FOR_DELIVERY')]);
    const text = late.nativeElement.textContent;
    expect(text).toContain('Delivered');
    expect(text).toContain('Delivery failed');
  });

  it('records proof of delivery with the recipient name', async () => {
    const fixture = await create([assignment('OUT_FOR_DELIVERY')]);
    const component = fixture.componentInstance as any;
    const parcel = assignment('OUT_FOR_DELIVERY');
    component.openPanel(parcel, 'delivered');
    component.proofForm.setValue({ recipientName: ' Kamala Silva ', notes: '', collectedAmount: null });

    component.submitDelivered(parcel);

    expect(service.recordProof).toHaveBeenCalledWith('assignment-1', {
      recipientName: 'Kamala Silva',
      notes: null,
      mediaUrl: null,
      deliveredAt: null,
      collectedAmount: null,
    }, null);
  });

  it('lists a cancelled parcel as finished, with no actions to take on it', async () => {
    const fixture = await create([assignment('CANCELLED')]);
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('Cancelled');
    expect(text).not.toContain('Mark as picked up');
    expect(text).not.toContain('Delivery failed');
  });

  describe('proof file', () => {
    const parcel = assignment('OUT_FOR_DELIVERY');
    const photo = new File([new Uint8Array(2048)], 'door.jpg', { type: 'image/jpeg' });

    /** What the browser hands over when the courier picks a file in the file field. */
    function pick(component: any, file: File | null) {
      const input = { files: file ? [file] : [], value: 'C:\\fakepath\\x', focus: vi.fn() };
      component.chooseProofFile({ target: input } as unknown as Event);
      return input;
    }

    async function openDeliveredForm() {
      const fixture = await create([parcel]);
      const component = fixture.componentInstance as any;
      component.openPanel(parcel, 'delivered');
      fixture.detectChanges();
      return { fixture, component };
    }

    it('offers an optional, labelled file field with its limits, on the delivery form only', async () => {
      const { fixture, component } = await openDeliveredForm();

      const label = fixture.nativeElement.querySelector('label[for="proof-file-assignment-1"]');
      expect(label?.textContent).toContain('Photo or document of the delivery');
      expect(label?.textContent).toContain('Optional');
      const input = fixture.nativeElement.querySelector('#proof-file-assignment-1') as HTMLInputElement;
      expect(input.type).toBe('file');
      expect(input.accept).toBe('image/jpeg,image/png,application/pdf');
      expect(input.getAttribute('aria-describedby')).toContain('proof-file-hint-assignment-1');
      expect(fixture.nativeElement.textContent).toContain('JPEG, PNG or PDF, up to 5 MB.');

      component.openPanel(parcel, 'failed');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('#proof-file-assignment-1')).toBeNull();
    });

    it('keeps a valid file and shows its name and size', async () => {
      const { fixture, component } = await openDeliveredForm();

      pick(component, photo);
      fixture.detectChanges();

      expect(component.proofFile()).toBe(photo);
      expect(fixture.nativeElement.textContent).toContain('door.jpg');
      expect(fixture.nativeElement.textContent).toContain('2 KB');
      expect(fixture.nativeElement.textContent).toContain('Remove file');
    });

    it.each([
      ['a text file', new File(['hello'], 'notes.txt', { type: 'text/plain' }), 'Choose a JPEG, PNG or PDF file.'],
      ['a GIF', new File([new Uint8Array(10)], 'a.gif', { type: 'image/gif' }), 'Choose a JPEG, PNG or PDF file.'],
      [
        'a file over 5 MB',
        new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.jpg', { type: 'image/jpeg' }),
        'Choose a file smaller than 5 MB.',
      ],
      ['an empty file', new File([], 'empty.jpg', { type: 'image/jpeg' }), 'That file is empty.'],
    ])('refuses %s with a plain message and clears the picker', async (_name, file, message) => {
      const { fixture, component } = await openDeliveredForm();

      const input = pick(component, file);
      fixture.detectChanges();

      expect(component.proofFile()).toBeNull();
      expect(input.value).toBe('');
      const error = fixture.nativeElement.querySelector('#proof-file-error-assignment-1');
      expect(error?.textContent).toContain(message);
      expect(fixture.nativeElement.querySelector('#proof-file-assignment-1').getAttribute('aria-invalid')).toBe('true');
    });

    it('sends the chosen file together with the proof details', async () => {
      const { component } = await openDeliveredForm();
      component.proofForm.patchValue({ recipientName: 'Kamala Silva' });
      pick(component, photo);

      component.submitDelivered(parcel);

      expect(service.recordProof).toHaveBeenCalledWith(
        'assignment-1',
        { recipientName: 'Kamala Silva', notes: null, mediaUrl: null, deliveredAt: null, collectedAmount: null },
        photo,
      );
    });

    it('lets the courier remove the file again', async () => {
      const { fixture, component } = await openDeliveredForm();
      pick(component, photo);
      fixture.detectChanges();
      const input = { value: 'x', focus: vi.fn() } as unknown as HTMLInputElement;

      component.removeProofFile(input);

      expect(component.proofFile()).toBeNull();
      expect(input.value).toBe('');
      expect(input.focus).toHaveBeenCalled();
    });

    it('forgets the file when the form is closed or reopened', async () => {
      const { component } = await openDeliveredForm();
      pick(component, photo);

      component.closePanel();
      expect(component.proofFile()).toBeNull();

      pick(component, photo);
      component.openPanel(parcel, 'delivered');
      expect(component.proofFile()).toBeNull();
    });

    it.each([
      ['UNSUPPORTED_FILE_TYPE', 415, 'Choose a JPEG, PNG or PDF file.'],
      ['FILE_TOO_LARGE', 413, 'Choose a file smaller than 5 MB.'],
      ['FILE_EMPTY', 400, 'That file is empty.'],
    ])('explains the server refusing the file (%s) in plain words and keeps the form open', async (code, status, text) => {
      const { component } = await openDeliveredForm();
      service.recordProof.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status, error: { code, detail: 'internal wording' } })),
      );
      component.proofForm.patchValue({ recipientName: 'Kamala Silva' });
      pick(component, photo);

      component.submitDelivered(parcel);

      expect(component.error()).toContain(text);
      expect(component.error()).not.toContain('internal wording');
      expect(component.isPanelOpen(parcel, 'delivered')).toBe(true);
    });
  });

  describe('where to go', () => {
    const job = {
      ...assignment('ASSIGNED'),
      pickup: { name: 'Nimal Stores', phone: '+94 77-123 4567', address: '12 Main St, Colombo' },
      delivery: { name: 'Kamala Silva', phone: '077 765 4321', address: '45 Lake Rd, Kandy' },
    } as DeliveryAssignment;

    it('shows who to meet and where, with tap to call and open in maps', async () => {
      const fixture = await create([job]);
      const text = fixture.nativeElement.textContent;

      expect(text).toContain('Pick up from');
      expect(text).toContain('12 Main St, Colombo');
      expect(text).toContain('Deliver to');
      expect(text).toContain('Kamala Silva');
      const links = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('a')).map((a) => a.getAttribute('href'));
      expect(links).toContain('tel:+94771234567');
      expect(links).toContain('tel:0777654321');
      expect(links).toContain('https://www.google.com/maps/search/?api=1&query=45%20Lake%20Rd%2C%20Kandy');
    });

    it('shows only the delivery end once the parcel has been picked up', async () => {
      const fixture = await create([{ ...job, status: 'PICKED_UP' } as DeliveryAssignment]);
      const text = fixture.nativeElement.textContent;

      expect(text).not.toContain('Pick up from');
      expect(text).toContain('Deliver to');
    });

    it('shows nothing when no contact details came with the parcel', async () => {
      const fixture = await create([assignment('ASSIGNED')]);

      expect(fixture.nativeElement.textContent).not.toContain('Deliver to');
    });
  });

  describe('Cash on Delivery', () => {
    const cod = { ...assignment('OUT_FOR_DELIVERY'), codAmount: 2500, currency: 'LKR' } as DeliveryAssignment;

    it('tells the courier how much to collect', async () => {
      const fixture = await create([cod]);

      expect(fixture.nativeElement.textContent).toContain('Collect LKR 2,500.00 in cash');
    });

    it('will not mark a COD parcel delivered until the cash collected is entered', async () => {
      const fixture = await create([cod]);
      const component = fixture.componentInstance as any;
      component.openPanel(cod, 'delivered');
      component.proofForm.patchValue({ recipientName: 'Kamala Silva' });

      component.submitDelivered(cod);

      expect(service.recordProof).not.toHaveBeenCalled();
      expect(component.fieldError('proof', 'collectedAmount')).toBe('Enter the cash you collected.');
    });

    it('sends the cash collected with the proof', async () => {
      const fixture = await create([cod]);
      const component = fixture.componentInstance as any;
      component.openPanel(cod, 'delivered');
      component.proofForm.setValue({ recipientName: 'Kamala Silva', notes: '', collectedAmount: 2500 });

      component.submitDelivered(cod);

      expect(service.recordProof).toHaveBeenCalledWith('assignment-1', {
        recipientName: 'Kamala Silva',
        notes: null,
        mediaUrl: null,
        deliveredAt: null,
        collectedAmount: 2500,
      }, null);
    });

    it('asks for the amount in an accessible field only on COD parcels', async () => {
      const fixture = await create([cod]);
      const component = fixture.componentInstance as any;
      component.openPanel(cod, 'delivered');
      fixture.detectChanges();

      const label = fixture.nativeElement.querySelector('label[for="proof-cash-assignment-1"]');
      expect(label?.textContent).toContain('Cash collected (LKR)');
      expect(fixture.nativeElement.querySelector('#proof-cash-assignment-1')).not.toBeNull();
    });
  });

  it('requires a reason before recording a failed delivery', async () => {
    const fixture = await create([assignment('OUT_FOR_DELIVERY')]);
    const component = fixture.componentInstance as any;
    const parcel = assignment('OUT_FOR_DELIVERY');
    component.openPanel(parcel, 'failed');

    component.submitFailed(parcel);
    expect(service.recordFailedAttempt).not.toHaveBeenCalled();

    component.failedForm.setValue({ reason: 'RECIPIENT_UNAVAILABLE', notes: 'Nobody home' });
    component.submitFailed(parcel);
    expect(service.recordFailedAttempt).toHaveBeenCalledWith('assignment-1', {
      reason: 'RECIPIENT_UNAVAILABLE',
      notes: 'Nobody home',
      attemptedAt: null,
    });
  });

  it('reports a problem against the assignment', async () => {
    const fixture = await create([assignment('ASSIGNED')]);
    const component = fixture.componentInstance as any;
    const parcel = assignment('ASSIGNED');
    component.openPanel(parcel, 'issue');
    component.issueForm.setValue({ issueType: 'DAMAGED_PARCEL', description: ' Box crushed ' });

    component.submitIssue(parcel);

    expect(service.reportIssue).toHaveBeenCalledWith({
      courierId: 'courier-1',
      assignmentId: 'assignment-1',
      parcelReference: null,
      issueType: 'DAMAGED_PARCEL',
      description: 'Box crushed',
    });
  });

  it('explains when a courier has no delivery profile yet', async () => {
    service = {
      getCourierByIdentity: vi
        .fn()
        .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 }))),
      listIssues: vi.fn(),
      listAssignments: vi.fn(),
      updateAssignmentStatus: vi.fn(),
      recordProof: vi.fn(),
      recordFailedAttempt: vi.fn(),
      reportIssue: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [MyDeliveriesPage],
      providers: [
        { provide: CourierDeliveryService, useValue: service },
        { provide: AuthService, useValue: { user: signal({ id: 'user-1', fullName: 'Nimal Perera' }) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(MyDeliveriesPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('not set up yet');
    expect(service.listAssignments).not.toHaveBeenCalled();
  });
});
