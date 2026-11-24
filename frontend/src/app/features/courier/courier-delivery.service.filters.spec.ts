import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ASSIGNMENT_API, COURIER_CATEGORY_API, COURIER_PROFILE_API, CourierDeliveryService, DELIVERY_ISSUE_API } from './courier-delivery.service';

describe('CourierDeliveryService filters and workflow calls', () => {
  let service: CourierDeliveryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CourierDeliveryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends only the assignment filters that are set and trims the parcel reference', () => {
    service.listAssignments({ parcelReference: '  SWD-2026-000001 ', courierId: 'c1', status: 'ASSIGNED' }).subscribe();
    const filtered = http.expectOne((r) => r.url === ASSIGNMENT_API);
    expect(filtered.request.params.get('parcelReference')).toBe('SWD-2026-000001');
    expect(filtered.request.params.get('courierId')).toBe('c1');
    expect(filtered.request.params.get('status')).toBe('ASSIGNED');
    expect(filtered.request.params.get('sort')).toBe('createdAt,desc');
    filtered.flush({ content: [] });

    service.listAssignments({ parcelReference: '   ' }).subscribe();
    const bare = http.expectOne((r) => r.url === ASSIGNMENT_API);
    expect(bare.request.params.has('parcelReference')).toBe(false);
    expect(bare.request.params.has('courierId')).toBe(false);
    bare.flush({ content: [] });
  });

  it('drives the courier workflow endpoints', () => {
    service.assign({ parcelReference: 'SWD-2026-000001', courierId: 'c1' } as never).subscribe();
    expect(http.expectOne(ASSIGNMENT_API).request.method).toBe('POST');
    service.updateAssignmentStatus('a1', 'PICKED_UP').subscribe();
    const status = http.expectOne(`${ASSIGNMENT_API}/a1/status`);
    expect(status.request.method).toBe('PATCH');
    expect(status.request.body).toEqual({ status: 'PICKED_UP' });
    service.recordFailedAttempt('a1', { failureReason: 'RECIPIENT_UNAVAILABLE' } as never).subscribe();
    http.expectOne(`${ASSIGNMENT_API}/a1/attempts`).flush({});
    service.listAttempts('a1').subscribe();
    http.expectOne(`${ASSIGNMENT_API}/a1/attempts`).flush([]);
    service.listHistory('a1').subscribe();
    http.expectOne(`${ASSIGNMENT_API}/a1/history`).flush([]);
    service.getProof('a1').subscribe();
    http.expectOne(`${ASSIGNMENT_API}/a1/proof`).flush({});
  });

  it('lists couriers and categories with stable sorting and optional filters', () => {
    service.listCategories().subscribe();
    const categories = http.expectOne((r) => r.url === COURIER_CATEGORY_API);
    expect(categories.request.params.get('sort')).toBe('name');
    categories.flush({ content: [] });

    service.listCouriers({ status: 'AVAILABLE', categoryId: 'cat-1' }).subscribe();
    const couriers = http.expectOne((r) => r.url === COURIER_PROFILE_API);
    expect(couriers.request.params.get('status')).toBe('AVAILABLE');
    expect(couriers.request.params.get('categoryId')).toBe('cat-1');
    couriers.flush({ content: [] });

    service.getCourierByIdentity('u/1').subscribe();
    http.expectOne(`${COURIER_PROFILE_API}/by-identity/u%2F1`).flush({});
    service.changeCategoryStatus('cat-1', 'INACTIVE').subscribe();
    expect(http.expectOne(`${COURIER_CATEGORY_API}/cat-1/status`).request.body).toEqual({ status: 'INACTIVE' });
  });

  it('lists issues newest first', () => {
    service.listIssues({}).subscribe();
    const req = http.expectOne((r) => r.url === DELIVERY_ISSUE_API);
    expect(req.request.params.get('sort')).toBe('reportedAt,desc');
    req.flush({ content: [] });
  });
});
