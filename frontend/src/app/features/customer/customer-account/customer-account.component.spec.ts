import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';
import { CustomerAccountComponent } from './customer-account.component';

describe('Customer profile page',()=>{
 it('renders actual account name and initials with empty extended profile fields',()=>{
  TestBed.configureTestingModule({imports:[CustomerAccountComponent],providers:[provideRouter([]),provideHttpClient(),provideHttpClientTesting(),
   {provide:AuthService,useValue:{user:signal({id:'customer-test',fullName:'Nimali Silva',email:'nimali@example.test',status:'ACTIVE'})}}
  ]});
  const fixture=TestBed.createComponent(CustomerAccountComponent);fixture.detectChanges();
  const http=TestBed.inject(HttpTestingController);
  http.expectOne('/api/v1/customer/profile').flush({customerId:'customer-test',fullName:null,email:null,phoneNumber:null,postalCode:null,city:null});
  http.expectOne('/api/v1/bookings/my').flush([]);fixture.detectChanges();
  const el=fixture.nativeElement as HTMLElement;
  expect(el.querySelector('.profile-hero__name')?.textContent).toContain('Nimali Silva');
  expect(el.querySelector('.profile-avatar-placeholder')?.textContent?.trim()).toBe('NS');
  expect(el.textContent).toContain('Not provided');expect(el.textContent).not.toContain('00100');
  expect(el.textContent).not.toContain('Verified');expect(el.textContent).not.toContain('Sri Lanka');
  fixture.componentInstance.openEditModal();fixture.detectChanges();
  expect(fixture.componentInstance.editForm.get('fullName')?.value).toBe('Nimali Silva');
  expect(fixture.componentInstance.editForm.get('postalCode')?.value).toBe('');http.verify();
 });
});
