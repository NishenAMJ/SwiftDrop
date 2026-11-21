import { CommonModule, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, merge, of, switchMap, takeUntil } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { DeliveryZone, EMPTY_FEE, FeeBreakdown } from '../../customer/models/customer-booking.models';
import { CustomerBookingService } from '../../customer/services/customer-booking.service';
import { CustomerProfileService } from '../../customer/services/customer-profile.service';
import {
  CreateMerchantOrderRequest,
  MerchantOrderService,
  MerchantUrgency,
} from '../services/merchant-order.service';

/**
 * Merchant Create Order. The order is saved as a normal SwiftDrop booking (SWD tracking number,
 * receipt, tracking) with the merchant attached. The fee shown is the backend quote from the
 * pricing module, the same calculation used when the order is saved.
 *
 * Dashboard orders are cash on delivery: the courier collects the COD amount from the recipient.
 * Online payment for merchant orders is available through the external merchant API.
 */
@Component({
  selector: 'app-create-order',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, DecimalPipe],
  templateUrl: './create-order.component.html',
  styleUrl: './create-order.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateOrderComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly orderService = inject(MerchantOrderService);
  private readonly bookingService = inject(CustomerBookingService);
  private readonly authService = inject(AuthService);
  private readonly profileService = inject(CustomerProfileService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy$ = new Subject<void>();

  bookingForm!: FormGroup;
  readonly submitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly zones = signal<DeliveryZone[]>([]);
  readonly zonesError = signal<string | null>(null);
  readonly feeBreakdown = signal<FeeBreakdown>(EMPTY_FEE);
  readonly quoteLoading = signal<boolean>(false);
  readonly quoteError = signal<string | null>(null);

  ngOnInit(): void {
    const user = this.authService.user();
    const profile = this.profileService.profile();
    const query = this.route.snapshot.queryParams;
    const requestedWeight = Number(query['weight']);
    const initialUrgency: MerchantUrgency = query['urgency'] === 'URGENT' ? 'URGENT' : 'NORMAL';

    this.bookingForm = this.fb.group({
      merchantReference: ['', [Validators.maxLength(100), Validators.pattern(/^[A-Za-z0-9._/-]*$/)]],
      description: ['', [Validators.maxLength(255)]],
      weightKg: [
        requestedWeight > 0 ? requestedWeight : 1.0,
        [Validators.required, Validators.min(0.01), Validators.max(50)],
      ],
      urgency: [initialUrgency, [Validators.required]],
      urgentNote: ['', [Validators.maxLength(255)]],
      codAmount: [null, [Validators.required, Validators.min(0.01)]],
      pickupAddress: this.fb.group({
        contactName: [user?.fullName || profile.fullName || '', [Validators.required, Validators.maxLength(100)]],
        contactPhone: [user?.phoneNumber || profile.phoneNumber || '', [Validators.required, Validators.maxLength(25)]],
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
        zoneCode: ['', [Validators.required]],
      }),
    });

    this.loadZones();
    this.watchQuote();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get selectedUrgency(): MerchantUrgency {
    return this.bookingForm.get('urgency')?.value || 'NORMAL';
  }

  setUrgency(urgency: MerchantUrgency): void {
    this.bookingForm.get('urgency')?.setValue(urgency);
  }

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

  zoneName(code: string | null | undefined): string {
    if (!code) return 'Standard zone';
    return this.zones().find((z) => z.code === code)?.name ?? code;
  }

  /** Re-price from the backend whenever weight, urgency or the delivery zone changes. */
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

  onSubmit(): void {
    if (this.bookingForm.invalid) {
      this.bookingForm.markAllAsTouched();
      this.errorMessage.set('Please fill in all required fields marked in red.');
      return;
    }
    if (this.submitting()) return;

    this.submitting.set(true);
    this.errorMessage.set(null);

    const v = this.bookingForm.value;
    const urgent = v.urgency === 'URGENT';
    const request: CreateMerchantOrderRequest = {
      merchantReference: v.merchantReference?.trim() || undefined,
      description: v.description?.trim() || undefined,
      weightKg: Number(v.weightKg),
      urgency: v.urgency,
      urgentNote: urgent ? v.urgentNote?.trim() || undefined : undefined,
      paymentMethod: 'COD',
      codAmount: Number(v.codAmount),
      pickupAddress: {
        contactName: v.pickupAddress.contactName.trim(),
        contactPhone: v.pickupAddress.contactPhone.trim(),
        streetAddress: v.pickupAddress.streetAddress.trim(),
        city: v.pickupAddress.city.trim(),
        postalCode: v.pickupAddress.postalCode?.trim() || undefined,
        zoneCode: v.pickupAddress.zoneCode || undefined,
      },
      deliveryAddress: {
        contactName: v.deliveryAddress.contactName.trim(),
        contactPhone: v.deliveryAddress.contactPhone.trim(),
        streetAddress: v.deliveryAddress.streetAddress.trim(),
        city: v.deliveryAddress.city.trim(),
        postalCode: v.deliveryAddress.postalCode?.trim() || undefined,
        zoneCode: v.deliveryAddress.zoneCode,
      },
    };

    this.orderService
      .createOrder(request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (order) => {
          this.submitting.set(false);
          this.router.navigate(['/merchant/receipt', order.id], { state: { newBooking: true } });
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(this.messageFor(err, 'The order could not be created right now. Please try again.'));
        },
      });
  }

  /** Backend errors carry a customer-safe message; anything else gets a generic text. */
  private messageFor(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse && err.status >= 400 && err.status < 500) {
      const body = err.error as { message?: string; fieldErrors?: Record<string, string> } | null;
      if (body?.fieldErrors) {
        const first = Object.values(body.fieldErrors)[0];
        if (first) return first;
      }
      if (err.status === 409) return body?.message || 'An order with this reference already exists.';
      if (body?.message) return body.message;
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
