import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { CourierProfile, DeliveryAssignment, DeliveryPage } from '../courier-delivery.models';
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

function page(content: DeliveryAssignment[]): DeliveryPage<DeliveryAssignment> {
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

describe('MyDeliveriesPage', () => {
  let service: {
    getCourierByIdentity: ReturnType<typeof vi.fn>;
    listAssignments: ReturnType<typeof vi.fn>;
    updateAssignmentStatus: ReturnType<typeof vi.fn>;
    recordProof: ReturnType<typeof vi.fn>;
    recordFailedAttempt: ReturnType<typeof vi.fn>;
    reportIssue: ReturnType<typeof vi.fn>;
  };

  async function create(assignments: DeliveryAssignment[]): Promise<ComponentFixture<MyDeliveriesPage>> {
    service = {
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
    component.proofForm.setValue({ recipientName: ' Kamala Silva ', notes: '' });

    component.submitDelivered(parcel);

    expect(service.recordProof).toHaveBeenCalledWith('assignment-1', {
      recipientName: 'Kamala Silva',
      notes: null,
      mediaUrl: null,
      deliveredAt: null,
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
