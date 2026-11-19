import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthUser } from '../../../core/auth/auth.models';
import { CustomerProfileService, PROFILE_API, profileInitials } from './customer-profile.service';

describe('CustomerProfileService', () => {
  const identity = (id='customer-a',fullName='Jane Silva'): AuthUser => ({id,fullName,email:id+'@example.test',phoneNumber:'0771234567',roles:['CUSTOMER'],status:'ACTIVE',authProviders:['PASSWORD'],mustChangePassword:false,createdAt:'2026-01-01T00:00:00Z'});
  let user=signal<AuthUser | null>(null);
  let service: CustomerProfileService;
  let http: HttpTestingController;
  beforeEach(()=>{
    localStorage.clear(); user=signal<AuthUser | null>(identity());
    localStorage.setItem('swiftdrop_customer_profile_v2',JSON.stringify({fullName:'Old Demo Account',city:'Demo City'}));
    TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting(),{provide:AuthService,useValue:{user}}]});
    service=TestBed.inject(CustomerProfileService); http=TestBed.inject(HttpTestingController);
  });
  afterEach(()=>{http.verify();localStorage.clear();});
  it('uses the signed-in DB identity when extended profile fields are empty',()=>{
    http.expectOne(PROFILE_API).flush({customerId:'customer-a',fullName:null,email:null,phoneNumber:null,city:null});
    expect(service.customerName()).toBe('Jane Silva'); expect(service.customerInitials()).toBe('JS');
    expect(service.customerEmail()).toBe('customer-a@example.test');
    expect(service.profile().city).toBe(''); expect(service.profile().postalCode).toBe('');
    expect(localStorage.getItem('swiftdrop_customer_profile_v2')).toBeNull();
  });
  it('updates the header and initials using saved real profile values',()=>{
    http.expectOne(PROFILE_API).flush({customerId:'customer-a',fullName:'Jane Silva',email:'jane@example.test'});
    service.updateProfile({fullName:'Jane Perera',email:'jane@example.test',avatarBase64:'data:image/png;base64,test'}).subscribe();
    http.expectOne(PROFILE_API).flush({customerId:'customer-a',fullName:'Jane Perera',email:'jane@example.test',avatarBase64:'data:image/png;base64,test'});
    expect(service.customerName()).toBe('Jane Perera'); expect(service.customerInitials()).toBe('JP');
    expect(service.customerAvatar()).toBe('data:image/png;base64,test');
  });
  it('does not report a failed save as persisted data',()=>{
    http.expectOne(PROFILE_API).flush({customerId:'customer-a',fullName:'Jane Silva',email:'jane@example.test'});
    const failed=vi.fn();service.updateProfile({fullName:'Unsaved Name',email:'jane@example.test'}).subscribe({error:failed});
    http.expectOne(PROFILE_API).flush({},{status:500,statusText:'Failed'});
    expect(failed).toHaveBeenCalledOnce();expect(service.customerName()).toBe('Jane Silva');
  });
  it('clears previous customer details on account switch and logout',()=>{
    http.expectOne(PROFILE_API).flush({customerId:'customer-a',fullName:'Jane Silva',email:'jane@example.test',city:'Kandy'});
    user.set(identity('customer-b','Amal Fernando'));expect(service.profile().city).toBe('');
    expect(service.customerName()).toBe('Amal Fernando');TestBed.tick();
    http.expectOne(PROFILE_API).flush({customerId:'customer-b',fullName:null,email:null});
    expect(service.customerInitials()).toBe('AF');user.set(null);TestBed.tick();
    expect(service.customerName()).toBe('');expect(service.customerInitials()).toBe('');
  });
  it('shows a loading error without inventing missing contact details',()=>{
    http.expectOne(PROFILE_API).flush({},{status:503,statusText:'Unavailable'});
    expect(service.error()).toContain('Could not load'); expect(service.profile().streetAddress).toBe('');
    expect(service.customerName()).toBe('Jane Silva');
  });
  it('handles empty and single-part initials safely',()=>{
    http.expectOne(PROFILE_API).flush({customerId:'customer-a',fullName:null,email:null});
    expect(profileInitials(null)).toBe('');expect(profileInitials('  ')).toBe('');
    expect(profileInitials('Amal')).toBe('AM');expect(profileInitials(' Jane   Mary Silva ')).toBe('JS');
  });
});
