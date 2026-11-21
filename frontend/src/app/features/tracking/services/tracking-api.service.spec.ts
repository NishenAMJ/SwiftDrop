import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TrackingApiService } from './tracking-api.service';

describe('TrackingApiService', () => {
  let service: TrackingApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TrackingApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the public summary and history using the encoded tracking number', () => {
    service.getPublicTracking('SWD-2026-100001').subscribe((result) => {
      expect(result.summary.currentStatus).toBe('IN_TRANSIT');
      expect(result.history.events).toHaveLength(1);
    });

    http.expectOne('/api/v1/public/tracking/SWD-2026-100001').flush({
      trackingNumber: 'SWD-2026-100001',
      currentStatus: 'IN_TRANSIT',
      publicDescription: 'Parcel is moving',
      safeLocation: 'Western Route',
      latestUpdateTime: '2026-11-14T10:00:00Z',
    });
    http.expectOne('/api/v1/public/tracking/SWD-2026-100001/history').flush({
      trackingNumber: 'SWD-2026-100001',
      events: [
        {
          status: 'IN_TRANSIT',
          publicDescription: 'Parcel is moving',
          safeLocation: 'Western Route',
          eventTime: '2026-11-14T10:00:00Z',
        },
      ],
    });
  });

  it('loads all dispatcher reports with the selected dates and interval', () => {
    service
      .getReports({ startDate: '2026-11-01', endDate: '2026-11-30' }, 'monthly')
      .subscribe((reports) => expect(reports.summary.totalParcelsBooked).toBe(4));

    const requests = http.match(
      (request) => request.url.startsWith('/api/v1/reports/') && request.method === 'GET',
    );
    expect(requests.map((request) => request.request.url).sort()).toEqual([
      '/api/v1/reports/deliveries/monthly',
      '/api/v1/reports/status-breakdown',
      '/api/v1/reports/summary',
    ]);
    for (const request of requests) {
      expect(request.request.params.get('startDate')).toBe('2026-11-01');
      expect(request.request.params.get('endDate')).toBe('2026-11-30');
    }

    requests
      .find((request) => request.request.url.endsWith('/summary'))!
      .flush({
        startDate: '2026-11-01',
        endDate: '2026-11-30',
        totalParcelsBooked: 4,
        parcelsCurrentlyInTransit: 1,
        deliveredParcels: 2,
        failedDeliveries: 1,
        deliverySuccessRate: 66.67,
      });
    requests
      .find((request) => request.request.url.endsWith('/status-breakdown'))!
      .flush({
        startDate: '2026-11-01',
        endDate: '2026-11-30',
        statuses: [],
      });
    requests
      .find((request) => request.request.url.includes('/deliveries/'))!
      .flush({
        startDate: '2026-11-01',
        endDate: '2026-11-30',
        counts: [],
      });
  });

  it('loads the dispatcher overview using distinct summary, daily and monthly periods', () => {
    service
      .getDispatcherOverview(
        { startDate: '2026-11-01', endDate: '2026-11-21' },
        { startDate: '2026-11-01', endDate: '2026-11-21' },
        { startDate: '2025-12-01', endDate: '2026-11-21' },
      )
      .subscribe((overview) => expect(overview.monthlyDeliveryCounts.counts).toHaveLength(1));

    const requests = http.match(
      (request) => request.url.startsWith('/api/v1/reports/') && request.method === 'GET',
    );
    expect(requests.map((request) => request.request.url).sort()).toEqual([
      '/api/v1/reports/deliveries/daily',
      '/api/v1/reports/deliveries/monthly',
      '/api/v1/reports/status-breakdown',
      '/api/v1/reports/summary',
    ]);

    const monthly = requests.find((request) => request.request.url.endsWith('/monthly'))!;
    expect(monthly.request.params.get('startDate')).toBe('2025-12-01');
    const daily = requests.find((request) => request.request.url.endsWith('/daily'))!;
    expect(daily.request.params.get('startDate')).toBe('2026-11-01');

    requests
      .find((request) => request.request.url.endsWith('/summary'))!
      .flush({
        startDate: '2026-11-01',
        endDate: '2026-11-21',
        totalParcelsBooked: 1,
        parcelsCurrentlyInTransit: 0,
        deliveredParcels: 1,
        failedDeliveries: 0,
        deliverySuccessRate: 100,
      });
    requests
      .find((request) => request.request.url.endsWith('/status-breakdown'))!
      .flush({
        startDate: '2026-11-01',
        endDate: '2026-11-21',
        statuses: [],
      });
    daily.flush({ startDate: '2026-11-01', endDate: '2026-11-21', counts: [] });
    monthly.flush({
      startDate: '2025-12-01',
      endDate: '2026-11-21',
      counts: [{ period: '2026-11-01', deliveredParcels: 1 }],
    });
  });
});
