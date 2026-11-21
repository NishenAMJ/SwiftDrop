import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { BOOKING_API, CustomerBookingService, PRICING_API, ZONES_API } from './customer-booking.service';
import { CreateBookingRequest } from '../models/customer-booking.models';

describe('CustomerBookingService', () => {
  let service: CustomerBookingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CustomerBookingService],
    });
    service = TestBed.inject(CustomerBookingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('gets the fee estimate from the backend pricing module, not a local formula', () => {
    let total = 0;
    let zoneCharge = 0;
    service.getFeeQuote(2, 'URGENT', 'COLOMBO').subscribe((fee) => {
      total = fee.totalFee;
      zoneCharge = fee.zoneCharge ?? 0;
    });

    const req = httpMock.expectOne(PRICING_API);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ weightKg: 2, zoneCode: 'COLOMBO', isUrgent: true });
    req.flush({
      totalDeliveryFee: 750,
      baseFee: 300,
      weightCharge: 100,
      weightKg: 2,
      ratePerKg: 50,
      zoneCharge: 150,
      zoneCode: 'COLOMBO',
      urgentSurcharge: 200,
      urgentSurchargeApplied: true,
      currency: 'LKR',
      appliedRuleCode: 'STANDARD_DEFAULT',
      breakdownDescription: '',
    });
    expect(total).toBe(750);
    expect(zoneCharge).toBe(150);
  });

  it('loads only active delivery zones', () => {
    service.getActiveZones().subscribe();
    const req = httpMock.expectOne((r) => r.url === ZONES_API);
    expect(req.request.params.get('status')).toBe('ACTIVE');
    req.flush([]);
  });

  it('should post booking to API', () => {
    const request: CreateBookingRequest = {
      description: 'Books',
      weightKg: 1.0,
      urgency: 'NORMAL',
      pickupAddress: {
        contactName: 'Alice',
        contactPhone: '0711111111',
        streetAddress: '123 Galle Rd',
        city: 'Colombo',
      },
      deliveryAddress: {
        contactName: 'Bob',
        contactPhone: '0722222222',
        streetAddress: '456 Peradeniya Rd',
        city: 'Kandy',
        zoneCode: 'KANDY',
      },
    };

    service.createBooking(request).subscribe((res) => {
      expect(res.trackingNumber).toBe('SWD-2026-000001');
    });

    const req = httpMock.expectOne(BOOKING_API);
    expect(req.request.method).toBe('POST');
    req.flush({ id: '123', trackingNumber: 'SWD-2026-000001' });
  });

  it('should fetch customer bookings from /api/v1/bookings/my', () => {
    service.getMyBookings().subscribe((bookings) => {
      expect(bookings.length).toBe(1);
      expect(bookings[0].trackingNumber).toBe('SWD-2026-000001');
    });

    const req = httpMock.expectOne(`${BOOKING_API}/my`);
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: '123',
        trackingNumber: 'SWD-2026-000001',
        weightKg: 2.0,
        urgency: 'NORMAL',
        status: 'BOOKED',
        estimatedFee: 550,
        createdAt: '2026-11-14T10:00:00Z',
      },
    ]);
  });

  it('discards an unpaid checkout', () => {
    service.discardDraft('draft-1').subscribe();
    const req = httpMock.expectOne(`${BOOKING_API}/draft-1/discard`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
