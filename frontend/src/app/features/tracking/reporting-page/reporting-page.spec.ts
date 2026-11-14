import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { TrackingApiService } from '../services/tracking-api.service';
import { ReportingPage } from './reporting-page';

const reportData = {
  summary: {
    startDate: '2026-11-01',
    endDate: '2026-11-30',
    totalParcelsBooked: 12,
    parcelsCurrentlyInTransit: 3,
    deliveredParcels: 8,
    failedDeliveries: 1,
    deliverySuccessRate: 88.89,
  },
  statusBreakdown: {
    startDate: '2026-11-01',
    endDate: '2026-11-30',
    statuses: [
      { status: 'DELIVERED', total: 8 },
      { status: 'IN_TRANSIT', total: 3 },
      { status: 'DELIVERY_FAILED', total: 1 },
    ],
  },
  deliveryCounts: {
    startDate: '2026-11-01',
    endDate: '2026-11-30',
    counts: [{ period: '2026-11-14', deliveredParcels: 5 }],
  },
};

describe('ReportingPage', () => {
  let fixture: ComponentFixture<ReportingPage>;
  let api: { getReports: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    api = { getReports: vi.fn().mockReturnValue(of(reportData)) };
    await TestBed.configureTestingModule({
      imports: [ReportingPage],
      providers: [provideRouter([]), { provide: TrackingApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(ReportingPage);
    fixture.detectChanges();
  });

  it('renders summary cards and accessible report tables', () => {
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Total parcels booked');
    expect(page.textContent).toContain('88.89%');
    expect(page.querySelectorAll('table')).toHaveLength(2);
    expect(page.querySelectorAll('caption')).toHaveLength(2);
    expect(page.textContent).toContain('Delivery Failed');
  });

  it('validates that the start date is not after the end date', () => {
    const page = fixture.nativeElement as HTMLElement;
    const inputs = page.querySelectorAll<HTMLInputElement>('input[type="date"]');
    inputs[0].value = '2026-11-30';
    inputs[0].dispatchEvent(new Event('input'));
    inputs[1].value = '2026-11-01';
    inputs[1].dispatchEvent(new Event('input'));
    page.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Start date must be on or before end date.',
    );
    expect(api.getReports).toHaveBeenCalledTimes(1);
  });
});
