import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MerchantOrderService, Order, Summary } from '../services/merchant-order.service';



@Component({
  selector: 'app-merchant-dashboard',
  templateUrl: './merchant-dashboard.component.html',
  styleUrls: ['./merchant-dashboard.component.scss'],
  standalone: true,
  imports: [CommonModule, RouterModule],
})
export class MerchantDashboardComponent {
  loading = signal(true);
  errorMessage = signal<string | null>(null);
  summary = signal<Summary>({ totalOrders: 0, pendingOrders: 0, deliveredOrders: 0 });
  recentOrders = signal<Order[]>([]);

  constructor(private service: MerchantOrderService) {
    this.loadData();
  }

  private loadData() {
    this.loading.set(true);
    this.service.getDashboardData().subscribe({
      next: (data) => {
        this.summary.set(data.summary);
        this.recentOrders.set(data.recentOrders);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set('Failed to load dashboard data');
        this.loading.set(false);
      },
    });
  }

  // Helper for displaying the merchant name – placeholder for real auth service
  merchantName(): string {
    return 'Merchant';
  }
}
