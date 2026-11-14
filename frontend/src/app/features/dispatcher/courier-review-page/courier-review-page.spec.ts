import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Courier } from '../../courier/courier.models';
import { CourierReviewPage } from './courier-review-page';

const courier: Courier = {
  userId: 'u-1',
  fullName: 'Kamal Silva',
  nationalIdNumber: '199012345678',
  vehicleType: 'MOTORCYCLE',
  drivingLicenceNumber: 'B1234567',
  vehicleRegistrationNumber: 'WP BAA-1234',
  status: 'APPROVED',
  submittedAt: '2026-11-10T04:30:00Z',
  createdAt: '2026-11-10T04:30:00Z',
};

describe('CourierReviewPage', () => {
  let fixture: ComponentFixture<CourierReviewPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [CourierReviewPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CourierReviewPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('filters by vehicle and suspends a courier by user ID with a reason', async () => {
    http.expectOne((r) => r.url === '/api/v1/couriers').flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await fixture.whenStable();

    const vehicle = page.querySelector<HTMLSelectElement>('#courier-filter-vehicle')!;
    vehicle.value = 'MOTORCYCLE';
    vehicle.dispatchEvent(new Event('change'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const list = http.expectOne((r) => r.url === '/api/v1/couriers');
    expect(list.request.params.get('vehicleType')).toBe('MOTORCYCLE');
    list.flush({ content: [courier], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    await fixture.whenStable();

    expect(page.textContent).toContain('WP BAA-1234');
    [...page.querySelectorAll<HTMLButtonElement>('.review-item button')]
      .find((b) => b.textContent?.includes('Suspend'))!
      .click();
    await fixture.whenStable();

    const dialog = page.querySelector('dialog')!;
    expect(dialog.textContent).toContain('lose courier access within 15 minutes');
    const box = dialog.querySelector('textarea')!;
    box.value = 'Repeated failed deliveries';
    box.dispatchEvent(new Event('input'));
    dialog.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const decision = http.expectOne('/api/v1/couriers/u-1/suspend');
    expect(decision.request.body).toEqual({ reason: 'Repeated failed deliveries' });
    decision.flush({ ...courier, status: 'SUSPENDED' });
    http.expectOne((r) => r.url === '/api/v1/couriers').flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await fixture.whenStable();

    expect(page.querySelector('.sd-alert--success')?.textContent).toContain('Kamal Silva: suspended.');
  });
});
