import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  BookingReceiptDto,
  BookingResponseDto,
  paymentStatusLabel,
  statusLabel,
} from '../models/customer-booking.models';
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
  readonly paymentCompleted = signal<boolean>(false);
  readonly isNewBooking = signal<boolean>(false);
  readonly copiedRef = signal<boolean>(false);
  readonly isInitiatingPayment = signal<boolean>(false);
  readonly paymentMessage = signal<{ type: 'success' | 'error' | 'info'; text: string } | null>(null);

  readonly statusLabel = statusLabel;
  readonly paymentStatusLabel = paymentStatusLabel;

  /** The same receipt page serves customers (/customer/receipt) and merchants (/merchant/receipt). */
  readonly isMerchantView = this.router.url.startsWith('/merchant/');
  readonly bookingsLink = this.isMerchantView ? '/merchant/bookings' : '/customer/bookings';
  readonly newBookingLink = this.isMerchantView ? '/merchant/create-order' : '/customer/book';

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
        this.errorMessage.set('A confirmed booking receipt is not available. Complete online payment from the booking form first.');
        this.loading.set(false);
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

}
