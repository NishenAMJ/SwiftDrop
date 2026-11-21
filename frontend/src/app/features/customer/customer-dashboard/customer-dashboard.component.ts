import { CommonModule, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import {
  ACTIVE_STATUSES,
  BookingResponseDto,
  BookingSummaryDto,
  EMPTY_FEE,
  FeeBreakdown,
  UrgencyType,
  statusBadgeClass,
  statusLabel,
} from '../models/customer-booking.models';
import { CustomerBookingService } from '../services/customer-booking.service';
import { CustomerProfileService } from '../services/customer-profile.service';

@Component({
  selector: 'app-customer-dashboard',
  imports: [CommonModule, RouterLink, FormsModule, DecimalPipe],
  templateUrl: './customer-dashboard.component.html',
  styleUrl: './customer-dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomerDashboardComponent implements OnInit {
  private readonly bookingService = inject(CustomerBookingService);
  private readonly authService = inject(AuthService);
  private readonly profileService = inject(CustomerProfileService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly estimate$ = new Subject<void>();

  readonly user = this.authService.user;
  readonly profile = this.profileService.profile;
  readonly customerName = this.profileService.customerName;
  readonly bookings = signal<BookingSummaryDto[]>([]);
  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);

  readonly statusLabel = statusLabel;
  readonly statusBadgeClass = statusBadgeClass;

  // Quick Tracker state (the customer's own bookings; anyone else uses public tracking)
  readonly trackQuery = signal<string>('');
  readonly trackingLoading = signal<boolean>(false);
  readonly trackResult = signal<BookingResponseDto | null>(null);
  readonly trackError = signal<string | null>(null);

  // Quick Estimator state (prices come from the backend pricing module)
  readonly estimateWeight = signal<number | null>(null);
  readonly estimateUrgency = signal<UrgencyType>('NORMAL');
  readonly feeBreakdown = signal<FeeBreakdown>(EMPTY_FEE);
  readonly estimateLoading = signal(false);
  readonly estimateError = signal<string | null>(null);

  // Copy state helper
  readonly copiedRef = signal<string | null>(null);

  ngOnInit(): void {
    this.loadBookings();
    this.estimate$
      .pipe(
        debounceTime(300),
        switchMap(() => {
          const weight = Number(this.estimateWeight()) || 0;
          if (weight <= 0) return of(EMPTY_FEE);
          this.estimateLoading.set(true);
          this.estimateError.set(null);
          return this.bookingService.getFeeQuote(weight, this.estimateUrgency()).pipe(
            catchError((err: unknown) => {
              const body = err instanceof HttpErrorResponse ? (err.error as { code?: string; message?: string }) : null;
              this.estimateError.set(body?.code && body.message ? body.message : 'Estimate not available right now');
              return of(EMPTY_FEE);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((fee) => {
        this.estimateLoading.set(false);
        this.feeBreakdown.set(fee);
      });
  }

  loadBookings(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.bookingService.getMyBookings().subscribe({
      next: (data) => {
        this.bookings.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not fetch recent bookings. Please try again.');
        this.loading.set(false);
      },
    });
  }

  // Dashboard KPI metrics computed from bookings list (statuses follow Delivery Operations events)
  get totalCount(): number {
    return this.bookings().length;
  }

  get activeCount(): number {
    return this.bookings().filter((b) => ACTIVE_STATUSES.includes(b.status?.toUpperCase())).length;
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
        this.trackError.set(
          `"${query}" is not one of your bookings. For a parcel someone sent to you, use public tracking.`,
        );
        this.trackingLoading.set(false);
      },
    });
  }

  updateEstimate(): void {
    this.estimate$.next();
  }

  setEstimateUrgency(val: UrgencyType): void {
    this.estimateUrgency.set(val);
    this.updateEstimate();
  }

  bookWithEstimate(): void {
    this.router.navigate(['/customer/book'], {
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
