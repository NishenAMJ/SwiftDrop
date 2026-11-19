import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CancelBookingComponent } from './cancel-booking.component';
describe('CancelBookingComponent',()=>{
 beforeEach(()=>TestBed.configureTestingModule({imports:[CancelBookingComponent],providers:[provideHttpClient(),provideHttpClientTesting()]}));
 afterEach(()=>TestBed.inject(HttpTestingController).verify());
 function setup(){
  const fixture=TestBed.createComponent(CancelBookingComponent);
  fixture.componentRef.setInput('bookingId','booking-123'); fixture.detectChanges();
  return {fixture,http:TestBed.inject(HttpTestingController)};
 }
 it('loads authoritative eligibility and shows its restriction without a confirmation action',()=>{
  const {fixture,http}=setup();
  expect(fixture.nativeElement.textContent).toContain('Checking cancellation eligibility');
  const request=http.expectOne('/api/v1/bookings/booking-123/cancellation-eligibility');
  expect(request.request.method).toBe('GET');
  request.flush({eligible:false,reason:'Pickup schedule cannot be verified.',booking:null}); fixture.detectChanges();
  expect(fixture.nativeElement.textContent).toContain('Pickup schedule cannot be verified.');
  expect(fixture.nativeElement.querySelector('dialog')).not.toBeNull();
  fixture.componentInstance.advance(); fixture.detectChanges();
  expect(fixture.nativeElement.textContent).toContain('Cancellation is permanent');
  const submit=fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
  expect(submit.disabled).toBe(true);
  fixture.componentInstance.advance();
  http.expectNone(request=>request.method==='POST');
 });
 it('calls cancellation only after explicit confirmation and never assumes completion',()=>{
  const {fixture,http}=setup();
  http.expectOne('/api/v1/bookings/booking-123/cancellation-eligibility').flush({
   eligible:true,reason:null,cancellationFee:100,booking:{id:'booking-123',trackingNumber:'TEST-123',paymentMethod:'COD',estimatedFee:500,status:'BOOKED',weightKg:2}
  }); fixture.detectChanges();
  fixture.componentInstance.advance(); fixture.detectChanges();
  expect(fixture.nativeElement.textContent).toContain('Confirm and continue to payment');
  http.expectNone(request=>request.method==='POST');
  fixture.componentInstance.advance(); fixture.componentInstance.advance();
  const request=http.expectOne('/api/v1/bookings/booking-123/cancel');
  expect(request.request.method).toBe('POST');
  expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
  expect(request.request.body).toEqual({});
  request.flush({cancellationStatus:'PENDING'}); fixture.detectChanges();
  expect(fixture.nativeElement.textContent).toContain('Waiting for verified payment instructions');
  expect(fixture.nativeElement.textContent).toContain('not yet cancelled');
 });
 it('closes the confirmation popup using the top-right close button',()=>{
  const {fixture,http}=setup();
  http.expectOne('/api/v1/bookings/booking-123/cancellation-eligibility').flush({eligible:false,reason:'Unavailable',booking:null});
  fixture.componentInstance.advance(); fixture.detectChanges();
  let closed=false; fixture.componentInstance.closed.subscribe(()=>closed=true);
  (fixture.nativeElement.querySelector('button[aria-label="Close dialog"]') as HTMLButtonElement).click();
  expect(closed).toBe(true);
 });
 it('handles a network error without exposing the server response',()=>{
  const {fixture,http}=setup();
  http.expectOne('/api/v1/bookings/booking-123/cancellation-eligibility').flush({message:'private internal detail'},{status:503,statusText:'Unavailable'});
  fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Please retry');
  expect(fixture.nativeElement.textContent).not.toContain('private internal detail');
 });
});
