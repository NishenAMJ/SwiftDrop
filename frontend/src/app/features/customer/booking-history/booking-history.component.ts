import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BookingSummaryDto } from '../models/customer-booking.models';
import { CustomerBookingService } from '../services/customer-booking.service';
import { CancelBookingComponent } from '../cancel-booking/cancel-booking.component';

@Component({
  selector: 'app-booking-history',
  imports: [CommonModule, RouterLink, FormsModule, DecimalPipe, CancelBookingComponent],
  templateUrl: './booking-history.component.html',
  styleUrl: './booking-history.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookingHistoryComponent implements OnInit {
  private readonly bookingService = inject(CustomerBookingService);
  // State for cancel modal
  readonly isCancelModalOpen = signal<boolean>(false);
  readonly cancelTargetId = signal<string>('');

  readonly bookings = signal<BookingSummaryDto[]>([]);
  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);

  // Filters
  readonly searchTerm = signal<string>('');
  readonly selectedUrgency = signal<string>('ALL');
  readonly selectedStatus = signal<string>('ALL');

  // Clipboard copy state
  readonly copiedRef = signal<string | null>(null);

  readonly filteredBookings = computed(() => {
    let list = this.bookings();
    const query = this.searchTerm().trim().toLowerCase();
    const urgency = this.selectedUrgency();
    const status = this.selectedStatus();

    if (query) {
      list = list.filter(
        (b) =>
          b.trackingNumber.toLowerCase().includes(query) ||
          (b.recipientName && b.recipientName.toLowerCase().includes(query)) ||
          (b.destinationCity && b.destinationCity.toLowerCase().includes(query)) ||
          (b.description && b.description.toLowerCase().includes(query)),
      );
    }

    if (urgency !== 'ALL') {
      list = list.filter((b) => b.urgency?.toUpperCase() === urgency);
    }

    if (status !== 'ALL') {
      list = list.filter((b) => b.status?.toUpperCase() === status);
    }

    return list;
  });

  ngOnInit(): void {
    this.loadHistory();
  }

  loadHistory(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.bookingService.getMyBookings().subscribe({
      next: (data) => {
        this.bookings.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load booking history:', err);
        this.errorMessage.set('Could not fetch your booking history. Please try again.');
        this.loading.set(false);
      },
    });
  }

  setUrgencyFilter(filter: string): void {
    this.selectedUrgency.set(filter);
  }

  setStatusFilter(filter: string): void {
    this.selectedStatus.set(filter);
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

  // Open cancel modal for a specific booking ID
  openCancelModal(bookingId: string): void {
    this.cancelTargetId.set(bookingId);
    this.isCancelModalOpen.set(true);
  }

  // Handle successful cancellation
  onCancelled(bookingId: string): void {
    this.isCancelModalOpen.set(false);
    this.loadHistory();
  }

  // Handle cancellation errors (optional UI feedback)
  onCancelError(error: any): void {
    console.error('Cancellation error:', error);
    this.isCancelModalOpen.set(false);
    // Could display a toast or alert here – omitted for brevity
  }
}
