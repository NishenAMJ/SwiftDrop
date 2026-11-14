import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CustomerProfileService, PROFILE_API } from './customer-profile.service';
import { UpdateProfileRequest } from '../models/customer-profile.models';

describe('CustomerProfileService', () => {
  let service: CustomerProfileService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CustomerProfileService],
    });
    service = TestBed.inject(CustomerProfileService);
    httpMock = TestBed.inject(HttpTestingController);

    // Handle the initial refreshProfile call in constructor
    const initReq = httpMock.match(PROFILE_API);
    if (initReq.length > 0) {
      initReq[0].flush({
        customerId: '11111111-1111-1111-1111-111111111111',
        fullName: 'SwiftDrop Customer',
        email: 'customer@swiftdrop.lk',
        phoneNumber: '+94 77 000 0000',
      });
    }
  });

  it('should initialize with default or loaded profile', () => {
    expect(service.customerName()).toBe('SwiftDrop Customer');
    expect(service.customerEmail()).toBe('customer@swiftdrop.lk');
    expect(service.customerInitials()).toBe('SC');
  });

  it('should update profile and persistent avatar in database', () => {
    const updateReq: UpdateProfileRequest = {
      fullName: 'Customer Updated',
      email: 'customer.new@gmail.com',
      phoneNumber: '+94 77 999 8888',
      avatarBase64: 'data:image/png;base64,iVBORw0KGgo...',
    };

    service.updateProfile(updateReq).subscribe((res) => {
      expect(res.fullName).toBe('Customer Updated');
      expect(res.email).toBe('customer.new@gmail.com');
      expect(res.avatarBase64).toContain('data:image/png;base64');
    });

    const req = httpMock.expectOne(PROFILE_API);
    expect(req.request.method).toBe('PUT');
    req.flush({
      customerId: '11111111-1111-1111-1111-111111111111',
      ...updateReq,
    });

    expect(service.customerName()).toBe('Customer Updated');
    expect(service.customerAvatar()).toBe('data:image/png;base64,iVBORw0KGgo...');
  });
});

