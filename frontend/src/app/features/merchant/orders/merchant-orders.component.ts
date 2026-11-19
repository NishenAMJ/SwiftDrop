import { Component, ChangeDetectionStrategy, signal, computed, inject } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MerchantOrderService, Order } from '../services/merchant-order.service';

/**
 * Merchant Orders page – lists all orders for the logged‑in merchant.
 * Mirrors the UI/UX of the Customer booking‑history component.
 */
@Component({
  selector: 'app-merchant-orders',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, ReactiveFormsModule, DecimalPipe],
  templateUrl: './merchant-orders.component.html',
  styleUrl: './merchant-orders.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MerchantOrdersComponent {
  private readonly orderService = inject(MerchantOrderService);
  private readonly fb = inject(FormBuilder);

  // State signals
  readonly orders = signal<Order[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  // Filters
  readonly searchCtrl = this.fb.control('');
  readonly statusCtrl = this.fb.control('ALL');

  // Derived filtered list
  readonly filteredOrders = computed(() => {
    const term = this.searchCtrl.value?.toLowerCase() ?? '';
    const status = this.statusCtrl.value;
    return this.orders()
      .filter((o) => {
        const matchesTerm =
          o.id.toLowerCase().includes(term) ||
          (o.deliveryAddress && o.deliveryAddress.toLowerCase().includes(term)) ||
          (o.recipientName && o.recipientName.toLowerCase().includes(term));
        const matchesStatus = status === 'ALL' || o.status === status;
        return matchesTerm && matchesStatus;
      })
      .sort((a, b) => a.id.localeCompare(b.id));
  });

  constructor() {
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.orderService.getOrders().subscribe({
      next: (data) => this.orders.set(data),
      error: () => this.errorMessage.set('Failed to load orders'),
      complete: () => this.loading.set(false),
    });
  }
}
