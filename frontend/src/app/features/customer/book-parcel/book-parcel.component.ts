import { CommonModule, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, merge, switchMap, catchError, of, takeUntil, timer } from 'rxjs';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { AuthService } from '../../../core/auth/auth.service';
import {
  BookingCheckout,
  BookingResponseDto,
  CreateBookingRequest,
  DeliveryZone,
  EMPTY_FEE,
  FeeBreakdown,
  PaymentMethod,
  UrgencyType,
} from '../models/customer-booking.models';
import { CustomerBookingService } from '../services/customer-booking.service';
import { CustomerProfileService } from '../services/customer-profile.service';

/** Same rule as the backend AddressDto: local 0771234567 or international +94771234567. */
const PHONE_PATTERN = /^\+?[0-9][0-9 ()-]{8,23}$/;
/** How often and how long to wait for Payment & Settlement to confirm after returning from PayHere. */
const VERIFY_ATTEMPTS = 5;
const VERIFY_INTERVAL_MS = 3000;

/**
 * starting: payment not opened yet (or could not be started).
 * redirecting: on the way to PayHere.
 * verifying: back from PayHere, asking the backend whether it was paid (no buttons).
 * not-confirmed: no successful payment found.
 */
type PaymentStep = 'starting' | 'redirecting' | 'verifying' | 'not-confirmed';

/**
 * Book a parcel (Member 3).
 *
 * Online payment flow: the form is saved as an unpaid checkout, the customer is sent to the
 * secure payment page of Payment & Settlement, and on return the booking is confirmed only when
 * the payment is verified. Only then is the receipt shown. COD bookings are confirmed at once.
 */
@Component({
  selector: 'app-book-parcel',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, DecimalPipe, ConfirmDialog],
  templateUrl: './book-parcel.component.html',
  styleUrl: './book-parcel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookParcelComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly fb = inject(FormBuilder);
  private readonly bookingService = inject(CustomerBookingService);
  private readonly authService = inject(AuthService);
  private readonly profileService = inject(CustomerProfileService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  bookingForm!: FormGroup;
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly zones = signal<DeliveryZone[]>([]);
  readonly zonesError = signal<string | null>(null);

  readonly feeBreakdown = signal<FeeBreakdown>(EMPTY_FEE);
  readonly quoteLoading = signal(false);
  readonly quoteError = signal<string | null>(null);

  // Online checkout
  readonly paymentOpen = signal(false);
  readonly draft = signal<BookingResponseDto | null>(null);
  readonly checkout = signal<BookingCheckout | null>(null);
  readonly paymentStep = signal<PaymentStep>('starting');
  readonly paymentMessage = signal<string | null>(null);
  private paymentKey = '';

  private get storageKey(): string {
    return 'swiftdrop:booking-checkout:' + this.authService.user()?.id;
  }

  constructor() {
    // Fill the sender section from the saved profile once it has loaded.
    effect(() => {
      const profile = this.profileService.profile();
      const pickup = this.bookingForm?.get('pickupAddress');
      if (!pickup || this.draft()) return;
      const values = {
        contactName: profile.fullName,
        contactPhone: profile.phoneNumber,
        streetAddress: profile.streetAddress,
        city: profile.city,
        postalCode: profile.postalCode,
      };
      for (const [key, value] of Object.entries(values)) {
        const control = pickup.get(key);
        if (control?.pristine && !control.disabled && value) control.setValue(value, { emitEvent: false });
      }
    });
  }

  ngOnInit(): void {
    const user = this.authService.user();
    const profile = this.profileService.profile();
    const queryParams = this.route.snapshot.queryParams;

    const requestedWeight = Number(queryParams['weight']);
    const initialWeight = Number.isFinite(requestedWeight) && requestedWeight > 0 ? requestedWeight : null;
    const initialUrgency: UrgencyType = queryParams['urgency'] === 'URGENT' ? 'URGENT' : 'NORMAL';

    this.bookingForm = this.fb.group({
      description: ['', [Validators.maxLength(255)]],
      weightKg: [initialWeight, [Validators.required, Validators.min(0.01), Validators.max(1000)]],
      urgency: [initialUrgency, [Validators.required]],
      paymentMethod: ['ONLINE', [Validators.required]],

      pickupAddress: this.fb.group({
        contactName: [profile.fullName || user?.fullName || '', [Validators.required, Validators.maxLength(100)]],
        contactPhone: [
          profile.phoneNumber || user?.phoneNumber || '',
          [Validators.required, Validators.maxLength(25), Validators.pattern(PHONE_PATTERN)],
        ],
        streetAddress: [profile.streetAddress || '', [Validators.required, Validators.maxLength(255)]],
        city: [profile.city || '', [Validators.required, Validators.maxLength(100)]],
        postalCode: [profile.postalCode || '', [Validators.maxLength(20)]],
        zoneCode: [''],
      }),

      deliveryAddress: this.fb.group({
        contactName: ['', [Validators.required, Validators.maxLength(100)]],
        contactPhone: ['', [Validators.required, Validators.maxLength(25), Validators.pattern(PHONE_PATTERN)]],
        streetAddress: ['', [Validators.required, Validators.maxLength(255)]],
        city: ['', [Validators.required, Validators.maxLength(100)]],
        postalCode: ['', [Validators.maxLength(20)]],
        zoneCode: ['', [Validators.required]],
      }),
    });

    this.loadZones();
    this.watchQuote();
    this.restoreCheckout();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ---------------------------------------------------------------- zones and pricing

  loadZones(): void {
    this.zonesError.set(null);
    this.bookingService
      .getActiveZones()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (zones) => this.zones.set(zones),
        error: () => this.zonesError.set('Delivery zones could not be loaded. Please retry.'),
      });
  }

  /** Re-price whenever weight, urgency or the delivery zone changes (debounced). */
  private watchQuote(): void {
    const form = this.bookingForm;
    merge(
      form.get('weightKg')!.valueChanges,
      form.get('urgency')!.valueChanges,
      form.get('deliveryAddress.zoneCode')!.valueChanges,
      of(null),
    )
      .pipe(
        debounceTime(300),
        switchMap(() => {
          const weight = Number(form.get('weightKg')?.value) || 0;
          if (weight <= 0) {
            this.quoteLoading.set(false);
            this.quoteError.set(null);
            return of(EMPTY_FEE);
          }
          this.quoteLoading.set(true);
          this.quoteError.set(null);
          return this.bookingService
            .getFeeQuote(weight, this.selectedUrgency, form.get('deliveryAddress.zoneCode')?.value || null)
            .pipe(
              catchError((err: unknown) => {
                this.quoteError.set(this.messageFor(err, 'The delivery fee could not be calculated right now.'));
                return of(EMPTY_FEE);
              }),
            );
        }),
        takeUntil(this.destroy$),
      )
      .subscribe((fee) => {
        this.quoteLoading.set(false);
        this.feeBreakdown.set(fee);
      });
  }

  zoneName(code: string | null | undefined): string {
    if (!code) return 'Standard zone';
    return this.zones().find((z) => z.code === code)?.name ?? code;
  }

  get selectedUrgency(): UrgencyType {
    return this.bookingForm.get('urgency')?.value || 'NORMAL';
  }

  setUrgency(urgency: UrgencyType): void {
    if (this.draft()) return;
    this.bookingForm.get('urgency')?.setValue(urgency);
  }

  get selectedPaymentMethod(): PaymentMethod {
    return this.bookingForm.get('paymentMethod')?.value || 'ONLINE';
  }

  setPaymentMethod(method: PaymentMethod): void {
    if (this.draft()) return;
    this.bookingForm.get('paymentMethod')?.setValue(method);
  }

  // ---------------------------------------------------------------- submit

  onSubmit(): void {
    if (this.submitting()) return;
    if (this.draft()) {
      this.paymentOpen.set(true);
      return;
    }
    if (this.bookingForm.invalid) {
      this.bookingForm.markAllAsTouched();
      this.errorMessage.set('Please fill in all required fields marked in red.');
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const formVal = this.bookingForm.getRawValue();
    const request: CreateBookingRequest = {
      description: formVal.description?.trim() || undefined,
      weightKg: Number(formVal.weightKg),
      urgency: formVal.urgency as UrgencyType,
      paymentMethod: (formVal.paymentMethod as PaymentMethod) || 'ONLINE',
      pickupAddress: this.addressFrom(formVal.pickupAddress),
      deliveryAddress: this.addressFrom(formVal.deliveryAddress),
    };

    this.bookingService
      .createBooking(request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          if (response.paymentMethod === 'ONLINE') {
            this.draft.set(response);
            this.bookingForm.disable();
            this.paymentKey = crypto.randomUUID();
            this.persistCheckout();
            this.paymentOpen.set(true);
            // Go straight to the payment page; the customer already pressed "Book a delivery".
            this.startPayment();
          } else {
            this.router.navigate(['/customer/receipt', response.id], { state: { newBooking: true } });
          }
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(this.messageFor(err, 'Your booking could not be created. Please try again.'));
        },
      });
  }

  private addressFrom(value: Record<string, string | null | undefined>) {
    return {
      contactName: (value['contactName'] ?? '').trim(),
      contactPhone: (value['contactPhone'] ?? '').trim(),
      streetAddress: (value['streetAddress'] ?? '').trim(),
      city: (value['city'] ?? '').trim(),
      postalCode: value['postalCode']?.trim() || undefined,
      zoneCode: value['zoneCode']?.trim() || undefined,
    };
  }

  // ---------------------------------------------------------------- online payment

  /** The dialog's main button: "Check again" after a failed check, otherwise start the payment. */
  paymentAction(): void {
    if (this.submitting() || !this.draft()) return;
    if (this.paymentStep() === 'not-confirmed') this.verifyPayment(1);
    else if (this.paymentStep() === 'starting') this.payNow();
  }

  get paymentActionLabel(): string {
    return this.paymentStep() === 'not-confirmed' ? 'Check again' : 'Continue to secure payment';
  }

  get paymentTitle(): string {
    switch (this.paymentStep()) {
      case 'not-confirmed':
        return 'Payment not completed';
      case 'verifying':
        return 'Checking your payment';
      case 'redirecting':
        return 'Opening secure payment';
      default:
        return 'Complete your payment';
    }
  }

  /** Only show footer buttons when the customer has a decision to make. */
  get paymentHasActions(): boolean {
    return this.paymentStep() === 'starting' || this.paymentStep() === 'not-confirmed';
  }

  /** Pay (again) for the same booking. The saved payment is reused, so nobody is charged twice. */
  payNow(): void {
    if (this.submitting() || !this.draft()) return;
    if (this.checkout()?.checkout) {
      this.paymentStep.set('redirecting');
      this.paymentMessage.set('Redirecting you to the secure payment page...');
      this.openGateway();
    } else {
      this.startPayment();
    }
  }

  /**
   * Pressing Back on the PayHere page can restore this page from the browser cache, still showing
   * the state from before the redirect. Check the payment again in that case.
   */
  @HostListener('window:pageshow', ['$event'])
  onPageShow(event: PageTransitionEvent): void {
    if (event.persisted && this.draft() && this.checkout()) this.verifyPayment(1);
  }

  /** Create (or reuse, with the same idempotency key) the payment and open the payment page. */
  startPayment(): void {
    const booking = this.draft();
    if (!booking || this.submitting()) return;
    this.submitting.set(true);
    this.paymentStep.set('starting');
    this.paymentMessage.set('Preparing secure payment...');
    this.bookingService
      .initiatePayment(booking.id, this.paymentKey)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (payment) => {
          this.checkout.set(payment);
          this.persistCheckout();
          this.submitting.set(false);
          if (payment.checkout) {
            this.paymentStep.set('redirecting');
            this.paymentMessage.set('Redirecting you to the secure payment page...');
            this.openGateway();
          } else {
            // Nothing to open (for example the payment is already settled): just check it.
            this.verifyPayment(1);
          }
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.paymentStep.set('starting');
          this.paymentMessage.set(
            this.messageFor(err, 'The payment service is not available right now. Your booking is not confirmed yet.') +
              ' Retry this payment; do not create a second booking.',
          );
        },
      });
  }

  /** Opens PayHere in this tab. PayHere brings the customer back to /payments/return. */
  openGateway(): void {
    const checkout = this.checkout()?.checkout;
    if (!checkout || checkout.provider !== 'PAYHERE' || checkout.method !== 'POST') return;
    let url: URL;
    try {
      url = new URL(checkout.checkoutUrl);
    } catch {
      this.paymentMessage.set('Secure checkout is unavailable.');
      return;
    }
    if (
      url.protocol !== 'https:' ||
      !['sandbox.payhere.lk', 'www.payhere.lk'].includes(url.hostname) ||
      url.pathname !== '/pay/checkout'
    ) {
      this.paymentMessage.set('Secure checkout could not be opened. Contact support.');
      return;
    }
    this.persistCheckout();
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = url.href;
    form.target = '_self';
    for (const [name, value] of Object.entries(checkout.fields)) {
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = name;
      input.value = value;
      form.appendChild(input);
    }
    document.body.appendChild(form);
    form.submit();
    form.remove();
  }

  /**
   * Asks the backend to verify the payment. Payment & Settlement may need a few seconds to
   * receive PayHere's confirmation, so it retries a few times before giving up.
   */
  verifyPayment(attempt = 1): void {
    const booking = this.draft();
    const payment = this.checkout();
    if (!booking || !payment) return;
    this.submitting.set(true);
    this.paymentStep.set('verifying');
    this.paymentMessage.set('Checking your payment with the payment service...');
    this.bookingService
      .confirmBookingPayment(booking.id, payment.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (confirmed) => {
          this.submitting.set(false);
          if (confirmed.status === 'BOOKED' && confirmed.receiptNumber) {
            this.clearCheckout();
            this.paymentOpen.set(false);
            this.router.navigate(['/customer/receipt', confirmed.id], { state: { newBooking: true } });
          } else {
            this.notConfirmed();
          }
        },
        error: () => {
          if (attempt < VERIFY_ATTEMPTS) {
            timer(VERIFY_INTERVAL_MS)
              .pipe(takeUntil(this.destroy$))
              .subscribe(() => this.verifyPayment(attempt + 1));
            return;
          }
          this.submitting.set(false);
          this.notConfirmed();
        },
      });
  }

  private notConfirmed(): void {
    this.paymentStep.set('not-confirmed');
    this.paymentMessage.set(
      'Your payment was not completed, so this booking is not confirmed yet. If you did pay, wait a minute and ' +
        'choose Check again. Otherwise choose Pay now, resume later from this page, or discard the booking.',
    );
  }

  /** Give up this unpaid checkout and start again with the form. */
  discardCheckout(): void {
    const booking = this.draft();
    if (!booking || this.submitting()) return;
    this.submitting.set(true);
    this.bookingService
      .discardDraft(booking.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.resetAfterDiscard(),
        error: (err: unknown) => {
          this.submitting.set(false);
          if (err instanceof HttpErrorResponse && err.status === 409) {
            // Already confirmed (a payment arrived meanwhile): check it instead of discarding.
            this.verifyPayment(VERIFY_ATTEMPTS);
            return;
          }
          this.paymentMessage.set('This checkout could not be discarded. Please try again.');
        },
      });
  }

  closePaymentDialog(): void {
    if (this.submitting()) return;
    this.paymentOpen.set(false);
  }

  private resetAfterDiscard(): void {
    this.submitting.set(false);
    this.clearCheckout();
    this.draft.set(null);
    this.checkout.set(null);
    this.paymentOpen.set(false);
    this.paymentMessage.set(null);
    this.bookingForm.enable();
  }

  private persistCheckout(): void {
    try {
      localStorage.setItem(
        this.storageKey,
        JSON.stringify({ id: this.draft()?.id, key: this.paymentKey, paymentId: this.checkout()?.id }),
      );
    } catch {
      /* storage unavailable: the checkout can still be completed in this tab */
    }
  }

  private clearCheckout(): void {
    try {
      localStorage.removeItem(this.storageKey);
    } catch {
      /* ignore */
    }
  }

  /**
   * After PayHere sends the customer back (/payments/return or /payments/cancel both land here),
   * continue the saved checkout and verify the payment automatically.
   */
  private restoreCheckout(): void {
    let pending: { id?: string; key?: string; paymentId?: string } | null = null;
    try {
      pending = JSON.parse(localStorage.getItem(this.storageKey) || 'null');
    } catch {
      pending = null;
    }
    if (!pending?.id || !pending?.key) return;
    this.paymentKey = pending.key;
    const paymentId = pending.paymentId;
    this.bookingService
      .getBookingById(pending.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (booking) => {
          if (booking.paymentMethod !== 'ONLINE') {
            this.clearCheckout();
            return;
          }
          if (booking.status === 'BOOKED' && booking.receiptNumber) {
            // Already confirmed by the payment.succeeded event.
            this.clearCheckout();
            this.router.navigate(['/customer/receipt', booking.id], { state: { newBooking: true } });
            return;
          }
          if (booking.status !== 'CHECKOUT_DRAFT' && booking.status !== 'EXPIRED') {
            this.clearCheckout();
            return;
          }
          this.draft.set(booking);
          this.bookingForm.disable();
          this.paymentOpen.set(true);
          if (!paymentId) {
            this.paymentStep.set('starting');
            return;
          }
          this.checkout.set({ id: paymentId, status: 'PENDING' });
          this.bookingService
            .getCheckout(paymentId)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
              next: (checkout) => this.checkout.set({ id: paymentId, status: 'PENDING', checkout }),
              error: () => undefined,
            });
          this.verifyPayment(1);
        },
        error: () => {
          this.clearCheckout();
          this.errorMessage.set('Your previous checkout could not be restored. Check My Bookings before booking again.');
        },
      });
  }

  // ---------------------------------------------------------------- helpers

  /** Our backend returns customer-safe messages with a code; anything else gets a generic text. */
  private messageFor(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse && err.status >= 400 && err.status < 500) {
      const body = err.error as { code?: string; message?: string; fieldErrors?: Record<string, string> } | null;
      if (body?.fieldErrors) {
        const first = Object.values(body.fieldErrors)[0];
        if (first) return first;
      }
      if (body?.code && body.message) return body.message;
    }
    if (err instanceof HttpErrorResponse && err.status === 0) {
      return 'You appear to be offline. Check your connection and try again.';
    }
    return fallback;
  }

  isFieldInvalid(path: string): boolean {
    const ctrl = this.bookingForm.get(path);
    return !!(ctrl && ctrl.invalid && (ctrl.dirty || ctrl.touched));
  }
}
