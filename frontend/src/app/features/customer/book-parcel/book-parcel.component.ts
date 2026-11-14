import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { CreateBookingRequest, FeeBreakdown, PaymentMethod, UrgencyType } from '../models/customer-booking.models';
import { CustomerBookingService } from '../services/customer-booking.service';
import { CustomerProfileService } from '../services/customer-profile.service';

@Component({
  selector: 'app-book-parcel',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, DecimalPipe],
  templateUrl: './book-parcel.component.html',
  styleUrl: './book-parcel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookParcelComponent implements OnInit {
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
    baseFee: 350,
    weightFee: 150,
    urgencySurcharge: 0,
    totalFee: 500,
  });

  ngOnInit(): void {
    const user = this.authService.user();
    const profile = this.profileService.profile();
    const queryParams = this.route.snapshot.queryParams;

    const initialWeight = queryParams['weight'] ? parseFloat(queryParams['weight']) : 1.0;
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
        zoneCode: [''],
      }),
    });

    this.recalculateFee();

    // Recalculate fees when weight or urgency changes
    this.bookingForm.get('weightKg')?.valueChanges.subscribe(() => this.recalculateFee());
    this.bookingForm.get('urgency')?.valueChanges.subscribe(() => this.recalculateFee());
  }

  get selectedUrgency(): UrgencyType {
    return this.bookingForm.get('urgency')?.value || 'NORMAL';
  }

  setUrgency(urgency: UrgencyType): void {
    this.bookingForm.get('urgency')?.setValue(urgency);
    this.recalculateFee();
  }

  get selectedPaymentMethod(): PaymentMethod {
    return this.bookingForm.get('paymentMethod')?.value || 'ONLINE';
  }

  setPaymentMethod(method: PaymentMethod): void {
    this.bookingForm.get('paymentMethod')?.setValue(method);
  }

  recalculateFee(): void {
    const weight = Number(this.bookingForm.get('weightKg')?.value) || 0;
    const urgency = (this.bookingForm.get('urgency')?.value || 'NORMAL') as UrgencyType;
    const breakdown = this.bookingService.calculateFee(weight, urgency);
    this.feeBreakdown.set(breakdown);
  }

  onSubmit(): void {
    if (this.bookingForm.invalid) {
      this.bookingForm.markAllAsTouched();
      this.errorMessage.set('Please fill in all required fields marked in red.');
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const formVal = this.bookingForm.value;
    const request: CreateBookingRequest = {
      description: formVal.description || 'General Parcel Delivery',
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

    this.bookingService.createBooking(request).subscribe({
      next: (response) => {
        this.submitting.set(false);
        // Navigate directly to the newly generated receipt!
        this.router.navigate(['/customer/receipt', response.id], {
          state: { newBooking: true, trackingNumber: response.trackingNumber },
        });
      },
      error: (err) => {
        console.error('Booking creation failed:', err);
        this.submitting.set(false);
        this.errorMessage.set(
          err.error?.message ||
            err.error?.detail ||
            'Unable to process booking at this time. Please check your network and try again.',
        );
      },
    });
  }

  // Field validation helpers
  isFieldInvalid(path: string): boolean {
    const ctrl = this.bookingForm.get(path);
    return !!(ctrl && ctrl.invalid && (ctrl.dirty || ctrl.touched));
  }
}

