import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EMPTY, of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { TrackingApiService } from '../services/tracking-api.service';
import { TrackingLiveService } from '../services/tracking-live.service';
import { PublicTrackingPage } from './public-tracking-page';

const trackingResult = {
  summary: {
    trackingNumber: 'SWD-2026-100001',
    currentStatus: 'IN_TRANSIT',
    publicDescription: 'Parcel is moving to the destination area',
    safeLocation: 'Western Route',
    latestUpdateTime: '2026-11-14T10:00:00Z',
  },
  history: {
    trackingNumber: 'SWD-2026-100001',
    events: [
      {
        status: 'BOOKED',
        publicDescription: 'Parcel booking confirmed',
        safeLocation: 'Colombo Hub',
        eventTime: '2026-11-14T09:00:00Z',
      },
      {
        status: 'IN_TRANSIT',
        publicDescription: 'Parcel is moving to the destination area',
        safeLocation: 'Western Route',
        eventTime: '2026-11-14T10:00:00Z',
      },
    ],
  },
};

describe('PublicTrackingPage', () => {
  let fixture: ComponentFixture<PublicTrackingPage>;
  let api: { getPublicTracking: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    api = { getPublicTracking: vi.fn().mockReturnValue(of(trackingResult)) };
    await TestBed.configureTestingModule({
      imports: [PublicTrackingPage],
      providers: [
        provideRouter([]),
        { provide: TrackingApiService, useValue: api },
        { provide: TrackingLiveService, useValue: { connect: () => EMPTY } },
        {
          provide: AuthService,
          useValue: { isSignedIn: signal(false), user: signal(null), token: () => null },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PublicTrackingPage);
    fixture.detectChanges();
  });

  it('validates the tracking number before searching', () => {
    submitTrackingNumber('not-valid');
    expect(api.getPublicTracking).not.toHaveBeenCalled();
    expect(text()).toContain('Enter a tracking number such as SWD-2026-100001.');
  });

  it('renders only privacy-safe status and newest-first history', () => {
    submitTrackingNumber('swd-2026-100001');

    expect(api.getPublicTracking).toHaveBeenCalledWith('SWD-2026-100001');
    expect(text()).toContain('Parcel is moving to the destination area');
    expect(text()).toContain('Western Route');
    expect(text()).not.toContain('customerName');
    const timelineHeadings = [...fixture.nativeElement.querySelectorAll('.timeline h3')].map(
      (element: Element) => element.textContent?.trim(),
    );
    expect(timelineHeadings).toEqual(['In Transit', 'Booked']);
  });

  it('shows a safe unknown-number state for a 404', () => {
    api.getPublicTracking.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { code: 'NOT_FOUND', detail: 'internal text should not be displayed' },
          }),
      ),
    );
    submitTrackingNumber('SWD-2026-999999');

    expect(text()).toContain('Tracking number not found');
    expect(text()).not.toContain('internal text should not be displayed');
  });

  function submitTrackingNumber(value: string): void {
    const page = fixture.nativeElement as HTMLElement;
    const input = page.querySelector<HTMLInputElement>('#tracking-number')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    page.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
