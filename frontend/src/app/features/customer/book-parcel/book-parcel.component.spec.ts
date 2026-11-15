import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { BookParcelComponent } from './book-parcel.component';
import { AuthService } from '../../../core/auth/auth.service';
import { CustomerProfileService } from '../services/customer-profile.service';

describe('Booking payment confirmation',()=>{
 const navigate=vi.fn();
 beforeEach(()=>{
  localStorage.clear(); navigate.mockClear();
  TestBed.configureTestingModule({imports:[BookParcelComponent],providers:[
   provideHttpClient(),provideHttpClientTesting(),
   {provide:Router,useValue:{navigate}},
   {provide:ActivatedRoute,useValue:{snapshot:{queryParams:{}}}},
   {provide:AuthService,useValue:{user:signal({id:'customer-test',fullName:'Sender',phoneNumber:'0771234567'})}},
   {provide:CustomerProfileService,useValue:{profile:signal({streetAddress:'Sender street',city:'Colombo'})}}
  ]});
 });
 afterEach(()=>{TestBed.inject(HttpTestingController).verify();localStorage.clear();});
 function setup(method:'ONLINE'|'COD'){
  const fixture=TestBed.createComponent(BookParcelComponent);
  fixture.componentInstance.ngOnInit();
  fixture.componentInstance.bookingForm.patchValue({weightKg:1,paymentMethod:method,deliveryAddress:{contactName:'Recipient',contactPhone:'0777654321',streetAddress:'Recipient street',city:'Kandy'}});
  return {component:fixture.componentInstance,http:TestBed.inject(HttpTestingController)};
 }
 const draft={id:'draft-123',trackingNumber:'TEST-123',paymentMethod:'ONLINE',status:'CHECKOUT_DRAFT',estimatedFee:500};
 it('keeps online creation unconfirmed and calls payment service only from the popup',()=>{
  const {component,http}=setup('ONLINE'); component.onSubmit();
  http.expectOne('/api/v1/bookings').flush(draft);
  expect(component.paymentOpen()).toBe(true); expect(navigate).not.toHaveBeenCalled();
  http.expectNone('/api/v1/payments');
  component.paymentAction();
  const request=http.expectOne('/api/v1/payments'); expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
  request.flush({id:'payment-123',status:'PENDING'});
  expect(navigate).not.toHaveBeenCalled();
  component.paymentAction();
  http.expectOne('/api/v1/bookings/draft-123/confirm-payment').flush({},{status:409,statusText:'Pending'});
  expect(navigate).not.toHaveBeenCalled(); expect(component.paymentMessage()).toContain('not been verified');
 });
 it('shows COD receipt immediately without an online payment request',()=>{
  const {component,http}=setup('COD'); component.onSubmit();
  http.expectOne('/api/v1/bookings').flush({...draft,paymentMethod:'COD',status:'BOOKED',receiptNumber:'RCP-TEST'});
  expect(navigate).toHaveBeenCalledWith(['/customer/receipt','draft-123'],{state:{newBooking:true}});
  http.expectNone('/api/v1/payments');
 });
 it('navigates only after backend confirmation and prevents duplicate clicks',()=>{
  const {component,http}=setup('ONLINE'); component.onSubmit();
  http.expectOne('/api/v1/bookings').flush(draft);
  component.paymentAction(); component.paymentAction();
  http.expectOne('/api/v1/payments').flush({id:'payment-123',status:'SUCCESS'});
  expect(navigate).not.toHaveBeenCalled();
  component.checkPayment();
  http.expectOne('/api/v1/bookings/draft-123/confirm-payment').flush({...draft,status:'BOOKED',receiptNumber:'RCP-TEST'});
  expect(navigate).toHaveBeenCalledOnce(); expect(component.paymentOpen()).toBe(false);
 });
 it('does not show a receipt when payment initiation fails',()=>{
  const {component,http}=setup('ONLINE'); component.onSubmit();
  http.expectOne('/api/v1/bookings').flush(draft);
  component.paymentAction();
  const first=http.expectOne('/api/v1/payments'); const key=first.request.headers.get('Idempotency-Key');
  first.flush({},{status:503,statusText:'Unavailable'});
  expect(navigate).not.toHaveBeenCalled(); component.paymentAction();
  const retry=http.expectOne('/api/v1/payments'); expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
  retry.flush({id:'payment-123',status:'PENDING'});
 });
});
