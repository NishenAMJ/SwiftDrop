import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import {
  CourierProfile,
  DeliveryAssignment,
  DeliveryAttempt,
  DeliveryPage,
  ProofOfDelivery,
  StatusChange,
} from '../../courier/courier-delivery.models';
import { CourierDeliveryService } from '../../courier/courier-delivery.service';
import { AssignmentsPage } from './assignments-page';

const courier = {
  id: 'courier-1',
  fullName: 'Nimal Perera',
  categoryName: 'Motorcycle',
  status: 'AVAILABLE',
} as CourierProfile;
const assignment = {
  id: 'assignment-1',
  parcelReference: 'SWD-2026-000201',
  courierId: 'courier-1',
  courierName: 'Nimal Perera',
  categoryCode: 'MOTORCYCLE',
  assignedBy: 'Dispatcher One',
  status: 'OUT_FOR_DELIVERY',
  assignedAt: '2026-11-20T09:00:00Z',
  completedAt: null,
} as DeliveryAssignment;

const failed = { ...assignment, id: 'assignment-2', parcelReference: 'SWD-2026-000202', status: 'DELIVERY_FAILED' } as DeliveryAssignment;
const delivered = { ...assignment, id: 'assignment-3', parcelReference: 'SWD-2026-000203', status: 'DELIVERED' } as DeliveryAssignment;
const failedAttempt = {
  id: 'attempt-1',
  assignmentId: 'assignment-2',
  attemptNumber: 1,
  reason: 'RECIPIENT_UNAVAILABLE',
  notes: 'Neighbour said to call after 6pm',
  attemptedAt: '2026-11-20T11:00:00Z',
} as DeliveryAttempt;
const proof = {
  id: 'proof-1',
  assignmentId: 'assignment-3',
  recipientName: 'Kamala Silva',
  notes: null,
  deliveredAt: '2026-11-20T12:00:00Z',
} as ProofOfDelivery;

const history = [
  {
    id: 'change-1',
    fromStatus: null,
    toStatus: 'ASSIGNED',
    changedBy: 'dispatcher-user',
    changedByRole: 'DISPATCHER',
    changedAt: '2026-11-20T09:00:00Z',
  },
  {
    id: 'change-2',
    fromStatus: 'ASSIGNED',
    toStatus: 'PICKED_UP',
    changedBy: 'courier-user',
    changedByRole: 'COURIER',
    changedAt: '2026-11-20T10:00:00Z',
  },
  {
    id: 'change-3',
    fromStatus: 'PICKED_UP',
    toStatus: 'CANCELLED',
    changedBy: null,
    changedByRole: 'SYSTEM',
    changedAt: '2026-11-20T11:00:00Z',
  },
] as StatusChange[];

function page<T>(content: T[]): DeliveryPage<T> {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    size: 20,
    number: 0,
    first: true,
    last: true,
    empty: content.length === 0,
  };
}

describe('AssignmentsPage', () => {
  let fixture: ComponentFixture<AssignmentsPage>;
  let service: {
    listCouriers: ReturnType<typeof vi.fn>;
    listAssignments: ReturnType<typeof vi.fn>;
    assign: ReturnType<typeof vi.fn>;
    listAttempts: ReturnType<typeof vi.fn>;
    getProof: ReturnType<typeof vi.fn>;
    listHistory: ReturnType<typeof vi.fn>;
    getProofFile: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    service = {
      listCouriers: vi.fn().mockReturnValue(of(page([courier]))),
      listAssignments: vi.fn().mockReturnValue(of(page([assignment]))),
      assign: vi.fn().mockReturnValue(of(assignment)),
      listAttempts: vi.fn().mockReturnValue(of([failedAttempt])),
      getProof: vi.fn().mockReturnValue(of(proof)),
      listHistory: vi.fn().mockReturnValue(of(history)),
      getProofFile: vi.fn().mockReturnValue(of(new Blob(['x'], { type: 'image/jpeg' }))),
    };
    await TestBed.configureTestingModule({
      imports: [AssignmentsPage],
      providers: [
        { provide: CourierDeliveryService, useValue: service },
        { provide: AuthService, useValue: { user: signal({ fullName: 'Dispatcher One' }) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AssignmentsPage);
    fixture.detectChanges();
  });

  it('shows assignments with the status as text', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('SWD-2026-000201');
    expect(text).toContain('Out for delivery');
    expect(service.listCouriers).toHaveBeenCalledWith({ status: 'AVAILABLE', size: 100 });
  });

  it('assigns a parcel as the signed-in dispatcher with a normalised reference', () => {
    const component = fixture.componentInstance as any;
    component.assignForm.setValue({
      parcelReference: ' swd-2026-000300 ',
      courierId: 'courier-1',
      notes: '',
    });

    component.assign();

    expect(service.assign).toHaveBeenCalledWith({
      parcelReference: 'SWD-2026-000300',
      courierId: 'courier-1',
      assignedBy: 'Dispatcher One',
      notes: null,
    });
  });

  it.each([
    ['PARCEL_CANCELLED', 'cancelled by the customer'],
    ['PARCEL_TOO_HEAVY', 'heavier than this courier'],
  ])('explains a refusal coded %s in plain words, not with server text', (code, expected) => {
    service.assign.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 422, error: { code, detail: 'internal wording that must not show' } }),
      ),
    );
    const component = fixture.componentInstance as any;
    component.assignForm.setValue({ parcelReference: 'SWD-1', courierId: 'courier-1', notes: '' });

    component.assign();

    expect(component.error()).toContain(expected);
    expect(component.error()).not.toContain('internal wording');
  });

  it.each([
    [404, "couldn't find a parcel"],
    [409, 'already has an active courier assignment'],
    [422, 'cannot take a parcel right now'],
    [503, "couldn't be checked right now"],
  ])('explains an assignment failure with status %i in plain words', (status, expected) => {
    service.assign.mockReturnValue(throwError(() => new HttpErrorResponse({ status })));
    const component = fixture.componentInstance as any;
    component.assignForm.setValue({ parcelReference: 'SWD-1', courierId: 'courier-1', notes: '' });

    component.assign();

    expect(component.error()).toContain(expected);
  });

  describe('finished assignments', () => {
    beforeEach(() => {
      (fixture.componentInstance as any).assignments.set(page([assignment, failed, delivered]));
      fixture.detectChanges();
    });

    it('offers details and reassignment only where they make sense', () => {
      const buttons = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).map(
        (button) => button.textContent!.replace(/\s+/g, ' ').trim(),
      );
      expect(buttons.filter((text) => text.startsWith('View details'))).toHaveLength(3);
      expect(buttons.filter((text) => text.startsWith('Reassign'))).toHaveLength(1);
    });

    it('shows why a delivery failed in plain words', () => {
      const component = fixture.componentInstance as any;

      component.toggleDetails(failed);
      fixture.detectChanges();

      expect(service.listAttempts).toHaveBeenCalledWith('assignment-2');
      const text = fixture.nativeElement.textContent;
      expect(text).toContain('Why it failed');
      expect(text).toContain('Recipient unavailable');
      expect(text).toContain('Neighbour said to call after 6pm');
    });

    it('shows who received a delivered parcel', () => {
      const component = fixture.componentInstance as any;

      component.toggleDetails(delivered);
      fixture.detectChanges();

      expect(service.getProof).toHaveBeenCalledWith('assignment-3');
      expect(fixture.nativeElement.textContent).toContain('Kamala Silva');
    });

    describe('the uploaded proof file', () => {
      const withFile = {
        ...proof,
        hasFile: true,
        fileName: 'front-door.jpg',
        fileContentType: 'image/jpeg',
        fileSizeBytes: 2048,
      } as ProofOfDelivery;
      let tab: { location: { href: string }; opener: unknown; close: ReturnType<typeof vi.fn> };

      beforeEach(() => {
        service.getProof.mockReturnValue(of(withFile));
        tab = { location: { href: '' }, opener: {}, close: vi.fn() };
        vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
        (URL as any).createObjectURL = vi.fn().mockReturnValue('blob:proof-file');
        (URL as any).revokeObjectURL = vi.fn();
      });

      it('describes the file and offers to view it', () => {
        (fixture.componentInstance as any).toggleDetails(delivered);
        fixture.detectChanges();

        const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
        expect(text).toContain('Proof file');
        expect(text).toContain('front-door.jpg');
        expect(text).toContain('Image');
        expect(text).toContain('View proof file');
      });

      it('offers nothing to view when the courier uploaded no file', () => {
        service.getProof.mockReturnValue(of({ ...proof, hasFile: false } as ProofOfDelivery));

        (fixture.componentInstance as any).toggleDetails(delivered);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).not.toContain('View proof file');
      });

      it('fetches the file with the login and opens it in a tab that cannot reach back into the app', () => {
        const component = fixture.componentInstance as any;

        component.openProofFile(delivered);

        expect(service.getProofFile).toHaveBeenCalledWith('assignment-3');
        expect(window.open).toHaveBeenCalledWith('about:blank', '_blank');
        expect(tab.opener).toBeNull();
        expect(tab.location.href).toBe('blob:proof-file');
      });

      it('says so, and closes the empty tab, when the file cannot be opened', () => {
        service.getProofFile.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
        const component = fixture.componentInstance as any;

        component.openProofFile(delivered);

        expect(tab.close).toHaveBeenCalled();
        expect(component.proofFileError()).toContain("couldn't be opened");
      });
    });

    it('shows the cash collected on a Cash on Delivery parcel', () => {
      service.getProof.mockReturnValue(of({ ...proof, collectedAmount: 2500 }));
      const component = fixture.componentInstance as any;
      component.assignments.set(page([{ ...delivered, currency: 'LKR' } as DeliveryAssignment]));
      fixture.detectChanges();

      component.toggleDetails(delivered);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Cash collected');
      expect(fixture.nativeElement.textContent).toContain('LKR 2,500.00');
    });

    it('shows who moved an assignment through each status, naming the courier and the role', () => {
      const component = fixture.componentInstance as any;

      component.toggleDetails(assignment);
      fixture.detectChanges();

      expect(service.listHistory).toHaveBeenCalledWith('assignment-1');
      expect(service.listAttempts).not.toHaveBeenCalled();
      expect(service.getProof).not.toHaveBeenCalled();
      const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
      expect(text).toContain('Status history');
      expect(text).toContain('Assigned by Dispatcher');
      expect(text).toContain('Assigned → Picked up by Nimal Perera');
      expect(text).toContain('Picked up → Cancelled by Automatic');
    });

    it('shows a cancelled parcel as cancelled and offers no reassignment for it', () => {
      const cancelled = { ...assignment, id: 'assignment-4', status: 'CANCELLED' } as DeliveryAssignment;
      (fixture.componentInstance as any).assignments.set(page([cancelled]));
      fixture.detectChanges();

      const text = fixture.nativeElement.textContent;
      expect(text).toContain('Cancelled');
      const buttons = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).map((b) =>
        b.textContent!.replace(/\s+/g, ' ').trim(),
      );
      expect(buttons.some((label) => label.startsWith('Reassign'))).toBe(false);
    });

    it('closes the details when asked again', () => {
      const component = fixture.componentInstance as any;
      component.toggleDetails(failed);

      component.toggleDetails(failed);
      fixture.detectChanges();

      expect(component.detailsId()).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('Why it failed');
    });

    it('tells the dispatcher when the details cannot be loaded', () => {
      service.listAttempts.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      const component = fixture.componentInstance as any;

      component.toggleDetails(failed);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('went wrong');
    });

    it('reassigns a failed parcel by filling in the form and moving to the courier choice', () => {
      const component = fixture.componentInstance as any;

      component.reassign(failed);
      fixture.detectChanges();

      expect(component.assignForm.getRawValue()).toEqual({
        parcelReference: 'SWD-2026-000202',
        courierId: '',
        notes: '',
      });
      expect(document.activeElement?.id).toBe('assign-courier');
      expect(fixture.nativeElement.textContent).toContain('Choose a courier to try SWD-2026-000202 again.');
    });

    it('explains when nobody is available to take a reassigned parcel', () => {
      const component = fixture.componentInstance as any;
      component.couriers.set([]);
      fixture.detectChanges();

      component.reassign(failed);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('No couriers are available right now');
      expect(component.assignForm.getRawValue().parcelReference).toBe('');
    });
  });

  it('does not submit without a courier', () => {
    const component = fixture.componentInstance as any;
    component.assignForm.setValue({ parcelReference: 'SWD-1', courierId: '', notes: '' });
    component.assign();
    expect(service.assign).not.toHaveBeenCalled();
  });
});
