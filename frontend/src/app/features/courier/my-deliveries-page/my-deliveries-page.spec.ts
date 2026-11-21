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
