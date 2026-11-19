import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { problem } from '../../../testing/auth-fixtures';
import { CourierApplicationPage } from './courier-application-page';

describe('CourierApplicationPage', () => {
  let fixture: ComponentFixture<CourierApplicationPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [CourierApplicationPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CourierApplicationPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
    http
      .expectOne('/api/v1/couriers/me')
      .flush(problem('NOT_FOUND', { status: 404 }), { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function type(id: string, value: string) {
    const input = page.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  async function submit() {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('asks for no licence or registration when the vehicle is a bicycle', async () => {
    type('courier-nationalIdNumber', '199012345678');
    type('courier-vehicleType', 'BICYCLE');
    await fixture.whenStable();
    expect(page.querySelector('#courier-drivingLicenceNumber')).toBeNull();

    await submit();
    const request = http.expectOne('/api/v1/couriers');
    expect(request.request.body).toEqual({ nationalIdNumber: '199012345678', vehicleType: 'BICYCLE' });
    request.flush(
      {
        userId: 'u1',
        fullName: 'Nimal Perera',
        vehicleType: 'BICYCLE',
        status: 'PENDING',
        submittedAt: '2026-11-10T04:30:00Z',
        createdAt: '2026-11-10T04:30:00Z',
      },
      { status: 201, statusText: 'Created' },
    );
    await fixture.whenStable();
    expect(page.querySelector('.sd-badge')?.textContent).toContain('Pending approval');
  });

  it('requires a licence and registration for a motor vehicle', async () => {
    type('courier-nationalIdNumber', '199012345678');
    type('courier-vehicleType', 'MOTORCYCLE');
    await submit();

    http.expectNone('/api/v1/couriers');
    expect(page.querySelector('#courier-drivingLicenceNumber-error')?.textContent).toContain('driving licence');
    expect(page.querySelector('#courier-vehicleRegistrationNumber-error')?.textContent).toContain('registration');
  });

  it('shows the status instead of the form when an application already exists', async () => {
    type('courier-nationalIdNumber', '199012345678');
    type('courier-vehicleType', 'BICYCLE');
    await submit();

    http
      .expectOne('/api/v1/couriers')
      .flush(problem('APPLICATION_ALREADY_EXISTS', { status: 409 }), { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/v1/couriers/me').flush({
      userId: 'u1',
      fullName: 'Nimal Perera',
      vehicleType: 'BICYCLE',
      status: 'APPROVED',
      submittedAt: '2026-11-10T04:30:00Z',
      createdAt: '2026-11-10T04:30:00Z',
    });
    // Approved but the session has no COURIER role yet: it fetches the new roles.
    http.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(page.textContent).toContain('You already have a courier application');
    expect(page.querySelector('.sd-badge')?.textContent).toContain('Approved');
    expect(page.querySelector('form')).toBeNull();
  });
});
