import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, OnDestroy, effect, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { BookingResponseDto, BookingCheckout } from '../models/customer-booking.models';
import { AuthService } from '../../../core/auth/auth.service';
import { CreateBookingRequest, FeeBreakdown, PaymentMethod, UrgencyType } from '../models/customer-booking.models';
import { CustomerBookingService } from '../services/customer-booking.service';
import { CustomerProfileService } from '../services/customer-profile.service';

@Component({
  selector: 'app-book-parcel',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, DecimalPipe, ConfirmDialog],
  templateUrl: './book-parcel.component.html',
  styleUrl: './book-parcel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookParcelComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  readonly paymentOpen = signal(false);
  readonly draft = signal<BookingResponseDto | null>(null);
  readonly checkout = signal<BookingCheckout | null>(null);
  readonly paymentMessage = signal<string | null>(null);
  private paymentKey = "";
  private get storageKey(): string { return "swiftdrop:booking-checkout:" + this.authService.user()?.id; }
  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
  private readonly fb = inject(FormBuilder);
  private readonly bookingService = inject(CustomerBookingService);
  private readonly authService = inject(AuthService);
  private readonly profileService = inject(CustomerProfileService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  bookingForm!: FormGroup;
  readonly submitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly feeBreakdown = signal<FeeBreakdown>({
    baseFee: 0,
    weightFee: 0,
    urgencySurcharge: 0,
    totalFee: 0,
  });

  constructor() {
    effect(() => {
      const profile = this.profileService.profile();
      const pickup = this.bookingForm?.get('pickupAddress');
      if (!pickup || this.draft()) return;
      for (const [key, value] of Object.entries({contactName:profile.fullName,contactPhone:profile.phoneNumber,streetAddress:profile.streetAddress,city:profile.city,postalCode:profile.postalCode})) {
        const control = pickup.get(key);
        if (control?.pristine && !control.disabled && value) control.setValue(value, {emitEvent:false});
      }
    });
  }

  ngOnInit(): void {
    const user = this.authService.user();
    const profile = this.profileService.profile();
    const queryParams = this.route.snapshot.queryParams;

    const requestedWeight = Number(queryParams['weight']);
    const initialWeight = Number.isFinite(requestedWeight) && requestedWeight > 0 ? requestedWeight : null;
    const initialUrgency: UrgencyType =
      queryParams['urgency'] === 'URGENT' ? 'URGENT' : 'NORMAL';

    this.bookingForm = this.fb.group({
      description: ['', [Validators.maxLength(255)]],
      weightKg: [
        initialWeight,
        [Validators.required, Validators.min(0.01), Validators.max(500)],
      ],
      urgency: [initialUrgency, [Validators.required]],
      paymentMethod: ['ONLINE', [Validators.required]],

      pickupAddress: this.fb.group({
        contactName: [profile.fullName || user?.fullName || '', [Validators.required, Validators.maxLength(100)]],
        contactPhone: [profile.phoneNumber || user?.phoneNumber || '', [Validators.required, Validators.maxLength(25)]],
        streetAddress: [profile.streetAddress || '', [Validators.required, Validators.maxLength(255)]],
        city: [profile.city || '', [Validators.required, Validators.maxLength(100)]],
        postalCode: [profile.postalCode || '', [Validators.maxLength(20)]],
        zoneCode: [''],
      }),

      deliveryAddress: this.fb.group({
        contactName: ['', [Validators.required, Validators.maxLength(100)]],
        contactPhone: ['', [Validators.required, Validators.maxLength(25)]],
        streetAddress: ['', [Validators.required, Validators.maxLength(255)]],
        city: ['', [Validators.required, Validators.maxLength(100)]],
        postalCode: ['', [Validators.maxLength(20)]],
        zoneCode: [''],
      }),
    });

    this.restoreCheckout();
    this.recalculateFee();

    // Recalculate fees when weight or urgency changes
    this.bookingForm.get('weightKg')?.valueChanges.subscribe(() => this.recalculateFee());
    this.bookingForm.get('urgency')?.valueChanges.subscribe(() => this.recalculateFee());
  }

  get selectedUrgency(): UrgencyType {
    return this.bookingForm.get('urgency')?.value || 'NORMAL';
  }

  setUrgency(urgency: UrgencyType): void {
    if(this.draft()) return;
    this.bookingForm.get('urgency')?.setValue(urgency);
    this.recalculateFee();
  }

  get selectedPaymentMethod(): PaymentMethod {
    return this.bookingForm.get('paymentMethod')?.value || 'ONLINE';
  }

  setPaymentMethod(method: PaymentMethod): void {
    if(this.draft()) return;
    this.bookingForm.get('paymentMethod')?.setValue(method);
  }

  recalculateFee(): void {
    const weight = Number(this.bookingForm.get('weightKg')?.value) || 0;
    const urgency = (this.bookingForm.get('urgency')?.value || 'NORMAL') as UrgencyType;
    const breakdown = this.bookingService.calculateFee(weight, urgency);
    this.feeBreakdown.set(breakdown);
  }

  onSubmit(): void {
    if (this.submitting()) return;
    if (this.draft()) { this.paymentOpen.set(true); return; }
    if (this.bookingForm.invalid) {
      this.bookingForm.markAllAsTouched();
      this.errorMessage.set('Please fill in all required fields marked in red.');
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const formVal = this.bookingForm.value;
    const request: CreateBookingRequest = {
      description: formVal.description?.trim() || undefined,
      weightKg: Number(formVal.weightKg),
      urgency: formVal.urgency as UrgencyType,
      paymentMethod: (formVal.paymentMethod as PaymentMethod) || 'ONLINE',
      pickupAddress: {
        contactName: formVal.pickupAddress.contactName.trim(),
        contactPhone: formVal.pickupAddress.contactPhone.trim(),
        streetAddress: formVal.pickupAddress.streetAddress.trim(),
        city: formVal.pickupAddress.city.trim(),
        postalCode: formVal.pickupAddress.postalCode?.trim() || undefined,
        zoneCode: formVal.pickupAddress.zoneCode?.trim() || undefined,
      },
      deliveryAddress: {
        contactName: formVal.deliveryAddress.contactName.trim(),
        contactPhone: formVal.deliveryAddress.contactPhone.trim(),
        streetAddress: formVal.deliveryAddress.streetAddress.trim(),
        city: formVal.deliveryAddress.city.trim(),
        postalCode: formVal.deliveryAddress.postalCode?.trim() || undefined,
        zoneCode: formVal.deliveryAddress.zoneCode?.trim() || undefined,
      },
    };

    this.bookingService.createBooking(request).pipe(takeUntil(this.destroy$)).subscribe({
      next: (response) => {
        this.submitting.set(false);
        if (response.paymentMethod === 'ONLINE') {
          this.draft.set(response); this.bookingForm.disable(); this.paymentOpen.set(true); this.paymentKey=crypto.randomUUID();
          this.persistCheckout();
        } else {
          this.router.navigate(['/customer/receipt', response.id], { state: { newBooking: true } });
        }
      },
      error: (err) => {
        console.error('Booking creation failed:', err);
        this.submitting.set(false);
        this.errorMessage.set(
          'Unable to process booking at this time. Please check your network and try again.',
        );
      },
    });
  }

  private persistCheckout(): void {
    try { localStorage.setItem(this.storageKey, JSON.stringify({id:this.draft()?.id,key:this.paymentKey,paymentId:this.checkout()?.id})); } catch {}
  }
  private restoreCheckout(): void {
    try {
      const pending=JSON.parse(localStorage.getItem(this.storageKey) || 'null');
      if (!pending?.id || !pending?.key) return;
      this.paymentKey=pending.key;
      this.bookingService.getBookingById(pending.id).pipe(takeUntil(this.destroy$)).subscribe({
        next: draft=>{
          if(draft.paymentMethod!=='ONLINE') return;
          this.draft.set(draft); this.bookingForm.disable(); this.paymentOpen.set(true);
          if(pending.paymentId) {
            this.checkout.set({id:pending.paymentId,status:'PENDING'});
            this.bookingService.getCheckout(pending.paymentId).pipe(takeUntil(this.destroy$)).subscribe({
              next: checkout=>this.checkout.set({id:pending.paymentId,status:'PENDING',checkout}),
              error:()=>this.paymentMessage.set('Check the saved payment status before trying to pay again.')
            });
          }
        }, error:()=>this.errorMessage.set('Could not restore your checkout. Please retry before creating another booking.')
      });
    } catch {}
  }
  paymentAction(): void {
    if(this.submitting() || !this.draft()) return;
    if(this.checkout()) { this.checkPayment(); return; }
    this.submitting.set(true); this.paymentMessage.set(null);
    this.bookingService.initiatePayment(this.draft()!.id,this.paymentKey).pipe(takeUntil(this.destroy$)).subscribe({
      next: payment=>{
        this.checkout.set(payment); this.persistCheckout(); this.submitting.set(false);
        this.paymentMessage.set('Checkout is ready. Complete payment, then check payment status. A receipt is issued only after verification.');
      },
      error:()=>{ this.submitting.set(false); this.paymentMessage.set('Payment service is unavailable. Your booking is not confirmed. Retry this checkout; do not create a second booking.'); }
    });
  }
  openGateway(): void {
    const checkout=this.checkout()?.checkout;
    if(!checkout || checkout.provider!=='PAYHERE' || checkout.method!=='POST') return;
    let url: URL;
    try { url=new URL(checkout.checkoutUrl); } catch { this.paymentMessage.set('Secure checkout is unavailable.'); return; }
    if(url.protocol!=='https:' || !['sandbox.payhere.lk','www.payhere.lk'].includes(url.hostname) || url.pathname!=='/pay/checkout') {
      this.paymentMessage.set('Secure checkout could not be opened. Contact support.'); return;
    }
    const form=document.createElement('form'); form.method='POST'; form.action=url.href; form.target='_blank'; form.rel='noopener';
    for(const [name,value] of Object.entries(checkout.fields)) { const input=document.createElement('input'); input.type='hidden'; input.name=name; input.value=value; form.appendChild(input); }
    document.body.appendChild(form); form.submit(); form.remove();
  }
  checkPayment(): void {
    if(this.submitting() || !this.draft() || !this.checkout()) return;
    this.submitting.set(true);
    this.bookingService.confirmBookingPayment(this.draft()!.id,this.checkout()!.id).pipe(takeUntil(this.destroy$)).subscribe({
      next: booking=>{
        this.submitting.set(false);
        if(booking.status!=='BOOKED' || !booking.receiptNumber) { this.paymentMessage.set('Payment has not been confirmed yet.'); return; }
        try { localStorage.removeItem(this.storageKey); } catch {}
        this.paymentOpen.set(false);
        this.router.navigate(['/customer/receipt',booking.id],{state:{newBooking:true}});
      },
      error:()=>{ this.submitting.set(false); this.paymentMessage.set('Successful payment has not been verified yet. If you have paid, wait and check again. Your checkout is saved; do not pay twice.'); }
    });
  }

  // Field validation helpers
  isFieldInvalid(path: string): boolean {
    const ctrl = this.bookingForm.get(path);
    return !!(ctrl && ctrl.invalid && (ctrl.dirty || ctrl.touched));
  }
}

