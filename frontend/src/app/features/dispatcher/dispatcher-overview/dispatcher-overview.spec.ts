import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { DispatcherOverviewData } from '../../tracking/models/tracking.models';
import { TrackingApiService } from '../../tracking/services/tracking-api.service';
import { DispatcherOverview } from './dispatcher-overview';

const overviewData: DispatcherOverviewData = {
  summary: {
    startDate: '2026-11-01',
    endDate: '2026-11-21',
    totalParcelsBooked: 25,
    parcelsCurrentlyInTransit: 6,
    deliveredParcels: 16,
    failedDeliveries: 3,
    deliverySuccessRate: 84.21,
  },
  statusBreakdown: {
    startDate: '2026-11-01',
    endDate: '2026-11-21',
    statuses: [
      { status: 'DELIVERED', total: 16 },
      { status: 'IN_TRANSIT', total: 6 },
      { status: 'DELIVERY_FAILED', total: 3 },
    ],
  },
  dailyDeliveryCounts: {
    startDate: '2026-11-01',
    endDate: '2026-11-21',
    counts: [
      { period: '2026-11-20', deliveredParcels: 7 },
      { period: '2026-11-21', deliveredParcels: 9 },
    ],
  },
  monthlyDeliveryCounts: {
    startDate: '2025-12-01',
    endDate: '2026-11-21',
    counts: [
      { period: '2026-10-01', deliveredParcels: 40 },
      { period: '2026-11-01', deliveredParcels: 16 },
    ],
  },
};

describe('DispatcherOverview', () => {
  let fixture: ComponentFixture<DispatcherOverview>;
  let api: { getDispatcherOverview: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    api = { getDispatcherOverview: vi.fn().mockReturnValue(of(overviewData)) };
    await TestBed.configureTestingModule({
      imports: [DispatcherOverview],
      providers: [provideRouter([]), { provide: TrackingApiService, useValue: api }],
    }).compileComponents();
  });

  function createPage(): HTMLElement {
    fixture = TestBed.createComponent(DispatcherOverview);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows delivery totals, status, daily and monthly summaries', () => {
    const page = createPage();

    expect(page.querySelector('h1')?.textContent).toContain('Delivery overview');
    expect(page.textContent).toContain('Total parcels booked');
    expect(page.textContent).toContain('84.21%');
    expect(page.textContent).toContain('Delivery Failed');
    expect(page.textContent).toContain('16 delivered');
    expect(page.textContent).toContain('56 delivered');
    expect(page.querySelectorAll('table')).toHaveLength(3);
    expect(page.querySelectorAll('caption')).toHaveLength(3);
    expect(page.querySelector('a[href="/dispatcher/merchants"]')).not.toBeNull();
    expect(page.querySelector('a[href="/tracking/reports"]')).not.toBeNull();
  });

  it('shows a safe error and retries the overview request', () => {
    api.getDispatcherOverview.mockReturnValueOnce(
      throwError(() => new Error('private database exception')),
    );
    const page = createPage();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(
      "That didn't work. Please try again.",
    );
    expect(page.textContent).not.toContain('private database exception');

    api.getDispatcherOverview.mockReturnValueOnce(of(overviewData));
    const retry = [...page.querySelectorAll('button')].find((button) =>
      button.textContent?.includes('Try again'),
    )!;
    retry.click();
    fixture.detectChanges();

    expect(api.getDispatcherOverview).toHaveBeenCalledTimes(2);
    expect(page.textContent).toContain('Total parcels booked');
  });
});
