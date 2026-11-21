import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import {
  CourierProfile,
  DeliveryAssignment,
  DeliveryPage,
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
  };

  beforeEach(async () => {
    service = {
      listCouriers: vi.fn().mockReturnValue(of(page([courier]))),
      listAssignments: vi.fn().mockReturnValue(of(page([assignment]))),
      assign: vi.fn().mockReturnValue(of(assignment)),
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

  it('does not submit without a courier', () => {
    const component = fixture.componentInstance as any;
    component.assignForm.setValue({ parcelReference: 'SWD-1', courierId: '', notes: '' });
    component.assign();
    expect(service.assign).not.toHaveBeenCalled();
  });
});
