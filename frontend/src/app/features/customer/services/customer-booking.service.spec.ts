import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { BOOKING_API, CustomerBookingService } from './customer-booking.service';
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

  it('should calculate estimated fee correctly for standard delivery', () => {
    const fee = service.calculateFee(2.0, 'NORMAL');
    // Base 350 + (2 * 150) = 650
    expect(fee.baseFee).toBe(350);
    expect(fee.weightFee).toBe(300);
    expect(fee.urgencySurcharge).toBe(0);
    expect(fee.totalFee).toBe(650);
  });

  it('should calculate estimated fee correctly for urgent delivery with surcharge', () => {
    const fee = service.calculateFee(3.5, 'URGENT');
    // Base 350 + (3.5 * 150 = 525) + 300 = 1175
    expect(fee.baseFee).toBe(350);
    expect(fee.weightFee).toBe(525);
    expect(fee.urgencySurcharge).toBe(300);
    expect(fee.totalFee).toBe(1175);
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
        estimatedFee: 650,
        createdAt: '2026-11-14T10:00:00Z',
      },
    ]);
  });
});

