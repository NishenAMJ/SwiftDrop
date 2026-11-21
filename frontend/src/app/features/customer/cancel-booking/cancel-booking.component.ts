import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, takeUntil } from 'rxjs';
import { CancellationEligibility, CancellationInitiation, statusLabel } from '../models/customer-booking.models';
import { CustomerBookingService } from '../services/customer-booking.service';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';

/**
 * Cancel a booking that no courier has been scheduled for yet (status Booked).
 * COD: nothing is charged. Online: the payment minus any cancellation fee is refunded.
 */
@Component({
  selector: 'app-cancel-booking',
  standalone: true,
  imports: [CommonModule, ConfirmDialog],
  templateUrl: './cancel-booking.component.html',
  styleUrl: './cancel-booking.component.scss',
})
export class CancelBookingComponent implements OnInit, OnDestroy {
  @Input({ required: true }) bookingId!: string;
  @Output() cancelled = new EventEmitter<string>();
  @Output() closed = new EventEmitter<void>();
  private readonly service = inject(CustomerBookingService);
  private readonly destroy$ = new Subject<void>();
  private requestKey = '';
  readonly eligibility = signal<CancellationEligibility | null>(null);
  readonly result = signal<CancellationInitiation | null>(null);
  readonly step = signal<'details' | 'confirmation' | 'done'>('details');
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly statusLabel = statusLabel;
  readonly booking = computed(() => this.eligibility()?.booking ?? null);
  readonly isOnline = computed(() => this.booking()?.paymentMethod === 'ONLINE');
  readonly canConfirm = computed(() => {
    const e = this.eligibility();
    if (e?.eligible !== true || e.cancellationFee == null || e.cancellationFee < 0) return false;
    return !this.isOnline() || (e.originalPaidAmount != null && e.refundableAmount != null && e.refundableAmount >= 0);
  });
  readonly title = computed(() =>
    this.step() === 'details' ? 'Cancel booking' : this.step() === 'confirmation' ? 'Confirm cancellation' : 'Booking cancelled',
  );
  readonly confirmLabel = computed(() =>
    this.step() === 'details'
      ? 'Review cancellation'
      : this.step() === 'confirmation'
        ? this.isOnline()
          ? 'Cancel and request refund'
          : 'Cancel booking'
        : 'Close',
  );
  readonly doneMessage = computed(() => {
    const r = this.result();
    if (!r) return '';
    if (r.cancellationStatus === 'REFUND_PENDING') {
      return `Your booking ${r.trackingNumber ?? ''} is cancelled. A refund of LKR ${(r.refundAmount ?? 0).toFixed(2)} has been requested to your original payment method. Refunds can take a few working days to appear.`;
    }
    if (r.cancellationStatus === 'REFUNDED') {
      return `Your booking ${r.trackingNumber ?? ''} is cancelled and the refund has been completed.`;
    }
    return `Your booking ${r.trackingNumber ?? ''} is cancelled. Nothing was charged.`;
  });

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    if (this.loading() && this.eligibility()) return;
    this.loading.set(true);
    this.error.set(null);
    this.eligibility.set(null);
    this.service
      .getCancellationEligibility(this.bookingId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (result) => {
          this.eligibility.set(result);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Could not check whether this booking can be cancelled. Please retry.');
          this.loading.set(false);
        },
      });
  }

  advance(): void {
    if (this.loading() || this.submitting()) return;
    if (this.step() === 'details') {
      if (this.eligibility()) this.step.set('confirmation');
      return;
    }
    if (this.step() === 'done') {
      this.cancelled.emit(this.bookingId);
      return;
    }
    if (!this.canConfirm()) return;
    this.submitting.set(true);
    this.error.set(null);
    // Same key across retries and reloads, so a repeated click cannot cause a second action.
    const storageKey = 'swiftdrop:cancellation:' + this.bookingId;
    try {
      this.requestKey = localStorage.getItem(storageKey) || crypto.randomUUID();
      localStorage.setItem(storageKey, this.requestKey);
    } catch {
      this.requestKey ||= crypto.randomUUID();
    }
    this.service
      .initiateCancellation(this.bookingId, this.requestKey)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (result) => {
          this.submitting.set(false);
          try {
            localStorage.removeItem(storageKey);
          } catch {
            /* ignore */
          }
          this.result.set(result);
          this.step.set('done');
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          const body = err instanceof HttpErrorResponse ? (err.error as { code?: string; message?: string } | null) : null;
          this.error.set(
            body?.code === 'CANCELLATION_NOT_ALLOWED' && body.message
              ? body.message
              : 'The booking could not be cancelled. Check again before retrying.',
          );
        },
      });
  }

  back(): void {
    if (this.submitting()) return;
    if (this.step() === 'confirmation') {
      this.step.set('details');
      this.error.set(null);
    } else if (this.step() === 'done') {
      this.cancelled.emit(this.bookingId);
    } else {
      this.closed.emit();
    }
  }
}
