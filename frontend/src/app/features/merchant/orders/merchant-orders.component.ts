import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CancelBookingComponent } from '../../customer/cancel-booking/cancel-booking.component';
import { BookingSummaryDto } from '../../customer/models/customer-booking.models';
import { CustomerBookingService } from '../../customer/services/customer-booking.service';

@Component({
  selector: 'app-merchant-orders',
  imports: [CommonModule, RouterLink, FormsModule, DecimalPipe, CancelBookingComponent],
  templateUrl: './merchant-orders.component.html',
  styleUrl: './merchant-orders.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MerchantOrdersComponent implements OnInit {
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
        console.error('Failed to load merchant booking history:', err);
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

  openCancelModal(bookingId: string): void {
    this.cancelTargetId.set(bookingId);
    this.isCancelModalOpen.set(true);
  }

  onCancelled(bookingId: string): void {
    this.isCancelModalOpen.set(false);
    this.loadHistory();
  }

  onCancelError(error: unknown): void {
    console.error('Cancellation error:', error);
    this.isCancelModalOpen.set(false);
  }
}
