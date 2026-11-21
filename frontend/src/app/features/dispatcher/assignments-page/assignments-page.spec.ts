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
  };

  beforeEach(async () => {
    service = {
      listCouriers: vi.fn().mockReturnValue(of(page([courier]))),
      listAssignments: vi.fn().mockReturnValue(of(page([assignment]))),
      assign: vi.fn().mockReturnValue(of(assignment)),
      listAttempts: vi.fn().mockReturnValue(of([failedAttempt])),
      getProof: vi.fn().mockReturnValue(of(proof)),
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
      expect(buttons.filter((text) => text.startsWith('View details'))).toHaveLength(2);
      expect(buttons.filter((text) => text.startsWith('Reassign'))).toHaveLength(1);
      expect(fixture.nativeElement.textContent).toContain('In progress');
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
