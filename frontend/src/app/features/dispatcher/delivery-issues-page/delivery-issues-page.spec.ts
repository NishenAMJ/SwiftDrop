import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { DeliveryIssue, DeliveryPage } from '../../courier/courier-delivery.models';
import { CourierDeliveryService } from '../../courier/courier-delivery.service';
import { DeliveryIssuesPage } from './delivery-issues-page';

const open = {
  id: 'issue-1',
  courierId: 'courier-1',
  courierName: 'Nimal Perera',
  assignmentId: 'assignment-1',
  parcelReference: 'SWD-2026-000206',
  issueType: 'DAMAGED_PARCEL',
  description: 'Box crushed during pickup',
  status: 'OPEN',
  reportedAt: '2026-11-20T09:00:00Z',
  reviewedBy: null,
  resolutionNotes: null,
  reviewedAt: null,
} as DeliveryIssue;
const issuePage: DeliveryPage<DeliveryIssue> = {
  content: [open],
  totalElements: 1,
  totalPages: 1,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: false,
};

describe('DeliveryIssuesPage', () => {
  let fixture: ComponentFixture<DeliveryIssuesPage>;
  let service: { listIssues: ReturnType<typeof vi.fn>; reviewIssue: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    service = {
      listIssues: vi.fn().mockReturnValue(of(issuePage)),
      reviewIssue: vi.fn().mockReturnValue(of({ ...open, status: 'RESOLVED' })),
    };
    await TestBed.configureTestingModule({
      imports: [DeliveryIssuesPage],
      providers: [
        { provide: CourierDeliveryService, useValue: service },
        { provide: AuthService, useValue: { user: signal({ fullName: 'Dispatcher One' }) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DeliveryIssuesPage);
    fixture.detectChanges();
  });

  it('starts with open issues and shows the type and status as text', () => {
    expect(service.listIssues).toHaveBeenCalledWith({ status: 'OPEN', page: 0, size: 20 });
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Damaged parcel');
    expect(text).toContain('Open');
    expect(text).toContain('Box crushed during pickup');
  });

  it('records the outcome as the signed-in dispatcher', () => {
    const component = fixture.componentInstance as any;
    component.beginReview(open);
    component.reviewForm.setValue({ status: 'RESOLVED', resolutionNotes: ' Customer informed ' });

    component.saveReview(open);

    expect(service.reviewIssue).toHaveBeenCalledWith('issue-1', {
      status: 'RESOLVED',
      reviewedBy: 'Dispatcher One',
      resolutionNotes: 'Customer informed',
    });
    expect(component.reviewingId()).toBeNull();
  });

  it('requires an outcome before saving', () => {
    const component = fixture.componentInstance as any;
    component.beginReview(open);
    component.saveReview(open);
    expect(service.reviewIssue).not.toHaveBeenCalled();
  });

  it('only offers closing outcomes once an issue is under review', () => {
    const component = fixture.componentInstance as any;
    expect(component.decisionsFor(open)).toEqual(['UNDER_REVIEW', 'RESOLVED', 'DISMISSED']);
    expect(component.decisionsFor({ ...open, status: 'UNDER_REVIEW' })).toEqual(['RESOLVED', 'DISMISSED']);
  });
});
