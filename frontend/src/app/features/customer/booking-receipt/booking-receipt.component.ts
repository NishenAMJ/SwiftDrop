import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BookingReceiptDto, BookingResponseDto } from '../models/customer-booking.models';
import { CustomerBookingService } from '../services/customer-booking.service';

@Component({
  selector: 'app-booking-receipt',
  imports: [CommonModule, RouterLink, DecimalPipe],
  templateUrl: './booking-receipt.component.html',
  styleUrl: './booking-receipt.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookingReceiptComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly bookingService = inject(CustomerBookingService);

  readonly receipt = signal<BookingReceiptDto | null>(null);
  readonly parcel = signal<BookingResponseDto | null>(null);
  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);
  readonly isNewBooking = signal<boolean>(false);
  readonly copiedRef = signal<boolean>(false);

  ngOnInit(): void {
    const nav = this.router.getCurrentNavigation();
    const state = nav?.extras?.state || history.state;
    if (state?.newBooking) {
      this.isNewBooking.set(true);
    }

    const parcelId = this.route.snapshot.paramMap.get('id');
    if (!parcelId) {
      this.errorMessage.set('Invalid booking ID specified.');
      this.loading.set(false);
      return;
    }

    this.loadReceipt(parcelId);
  }

  loadReceipt(parcelId: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    // Try fetching official receipt
    this.bookingService.getReceiptByParcelId(parcelId).subscribe({
      next: (receiptData) => {
        this.receipt.set(receiptData);
        this.loading.set(false);
      },
      error: () => {
        // Fallback to parcel details if receipt record wasn't ready yet
        this.bookingService.getBookingById(parcelId).subscribe({
          next: (parcelData) => {
            this.parcel.set(parcelData);
            this.loading.set(false);
          },
          error: (err) => {
            console.error('Failed to load receipt:', err);
            this.errorMessage.set('Receipt could not be found for the specified parcel.');
            this.loading.set(false);
          },
        });
      },
    });
  }

  printReceipt(): void {
    window.print();
  }

  copyTracking(ref: string): void {
    navigator.clipboard?.writeText(ref);
    this.copiedRef.set(true);
    setTimeout(() => this.copiedRef.set(false), 2000);
  }

  // Financial calculation helpers
  getBaseFee(): number {
    return 350;
  }

  getWeightFee(weightKg: number | undefined): number {
    const w = weightKg || 0;
    return Math.round(w * 150 * 100) / 100;
  }

  getUrgencyFee(urgency: string | undefined): number {
    return urgency === 'URGENT' ? 300 : 0;
  }
}

