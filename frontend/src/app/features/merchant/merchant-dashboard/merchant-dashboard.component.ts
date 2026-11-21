import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import {
  BookingResponseDto,
  BookingSummaryDto,
  FeeBreakdown,
  UrgencyType,
} from '../../customer/models/customer-booking.models';
import { CustomerBookingService } from '../../customer/services/customer-booking.service';

@Component({
  selector: 'app-merchant-dashboard',
  imports: [CommonModule, RouterLink, FormsModule, DecimalPipe],
  templateUrl: './merchant-dashboard.component.html',
  styleUrl: './merchant-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MerchantDashboardComponent implements OnInit {
  private readonly bookingService = inject(CustomerBookingService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly user = this.authService.user;
  readonly bookings = signal<BookingSummaryDto[]>([]);
  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);

  // Quick Tracker state
  readonly trackQuery = signal<string>('');
  readonly trackingLoading = signal<boolean>(false);
  readonly trackResult = signal<BookingResponseDto | null>(null);
  readonly trackError = signal<string | null>(null);

  // Quick Estimator state
  readonly estimateWeight = signal<number | null>(null);
  readonly estimateUrgency = signal<UrgencyType>('NORMAL');
  readonly feeBreakdown = signal<FeeBreakdown>({
    baseFee: 0,
    weightFee: 0,
    urgencySurcharge: 0,
    totalFee: 0,
  });

  // Copy state helper
  readonly copiedRef = signal<string | null>(null);

  merchantName(): string {
    return this.user()?.fullName || 'Merchant';
  }

  ngOnInit(): void {
    this.loadBookings();
    this.updateEstimate();
  }

  loadBookings(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.bookingService.getMyBookings().subscribe({
      next: (data) => {
        this.bookings.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load merchant bookings:', err);
        this.errorMessage.set('Could not fetch recent bookings. Please try again.');
        this.loading.set(false);
      },
    });
  }

  // Dashboard KPI metrics computed from bookings list
  get totalCount(): number {
    return this.bookings().length;
  }

  get activeCount(): number {
    return this.bookings().filter((b) =>
      ['BOOKED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'].includes(
        b.status?.toUpperCase(),
      ),
    ).length;
  }

  get urgentCount(): number {
    return this.bookings().filter((b) => b.urgency?.toUpperCase() === 'URGENT').length;
  }

  get deliveredCount(): number {
    return this.bookings().filter((b) => b.status?.toUpperCase() === 'DELIVERED').length;
  }

  get recentBookings(): BookingSummaryDto[] {
    return this.bookings().slice(0, 5);
  }

  onTrackSubmit(): void {
    const query = this.trackQuery().trim();
    if (!query) return;

    this.trackingLoading.set(true);
    this.trackError.set(null);
    this.trackResult.set(null);

    this.bookingService.getBookingByTrackingNumber(query).subscribe({
      next: (parcel) => {
        this.trackResult.set(parcel);
        this.trackingLoading.set(false);
      },
      error: () => {
        this.trackError.set(`No parcel found matching reference "${query}".`);
        this.trackingLoading.set(false);
      },
    });
  }

  /** Real estimate from the backend pricing module (no local formula). */
  updateEstimate(): void {
    const weight = Number(this.estimateWeight()) || 0;
    if (weight <= 0) {
      this.feeBreakdown.set({ baseFee: 0, weightFee: 0, urgencySurcharge: 0, totalFee: 0 });
      return;
    }
    this.bookingService.getFeeQuote(weight, this.estimateUrgency()).subscribe({
      next: (fee) => this.feeBreakdown.set(fee),
      error: () => this.feeBreakdown.set({ baseFee: 0, weightFee: 0, urgencySurcharge: 0, totalFee: 0 }),
    });
  }

  setEstimateUrgency(val: UrgencyType): void {
    this.estimateUrgency.set(val);
    this.updateEstimate();
  }

  bookWithEstimate(): void {
    this.router.navigate(['/merchant/create-order'], {
      queryParams: {
        weight: this.estimateWeight(),
        urgency: this.estimateUrgency(),
      },
    });
  }

  copyTracking(trackingNumber: string): void {
    navigator.clipboard?.writeText(trackingNumber);
    this.copiedRef.set(trackingNumber);
    setTimeout(() => {
      if (this.copiedRef() === trackingNumber) {
        this.copiedRef.set(null);
      }
    }, 2000);
  }
}
