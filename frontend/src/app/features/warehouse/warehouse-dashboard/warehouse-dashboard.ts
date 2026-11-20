import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import {
  TransferStatus,
  Warehouse,
  WarehouseDashboardData,
  WarehouseParcelState,
  WarehouseStatus,
} from '../warehouse.models';
import { WarehouseService } from '../warehouse.service';

const PARCEL_STATE_LABELS: Record<WarehouseParcelState, string> = {
  ARRIVED: 'Arrived',
  SORTED: 'Sorted',
  IN_TRANSFER: 'In transfer',
  RECEIVED: 'Received',
};

const TRANSFER_STATUS_LABELS: Record<TransferStatus, string> = {
  CREATED: 'Created',
  IN_TRANSIT: 'In transit',
  RECEIVED: 'Received',
  CANCELLED: 'Cancelled',
};

const WAREHOUSE_STATUS_LABELS: Record<WarehouseStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  MAINTENANCE: 'Maintenance',
};

@Component({
  selector: 'app-warehouse-dashboard',
  imports: [DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './warehouse-dashboard.html',
  styleUrl: './warehouse-dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WarehouseDashboard implements OnInit {
  private readonly warehouseService = inject(WarehouseService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly warehouseControl = new FormControl('', { nonNullable: true });
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly dashboard = signal<WarehouseDashboardData | null>(null);
  protected readonly loadingWarehouses = signal(true);
  protected readonly loadingDashboard = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  constructor() {
    this.warehouseControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((warehouseId) => {
        if (warehouseId) {
          this.loadDashboard(warehouseId);
        }
      });
  }

  ngOnInit(): void {
    this.loadWarehouses();
  }

  protected refresh(): void {
    const warehouseId = this.warehouseControl.value;
    if (warehouseId) {
      this.loadDashboard(warehouseId);
    } else {
      this.loadWarehouses();
    }
  }

  protected parcelStateLabel(state: WarehouseParcelState): string {
    return PARCEL_STATE_LABELS[state];
  }

  protected transferStatusLabel(status: TransferStatus): string {
    return TRANSFER_STATUS_LABELS[status];
  }

  protected warehouseStatusLabel(status: WarehouseStatus): string {
    return WAREHOUSE_STATUS_LABELS[status];
  }

  protected statusBadgeClass(
    status: WarehouseParcelState | TransferStatus | WarehouseStatus,
  ): string {
    switch (status) {
      case 'ACTIVE':
      case 'RECEIVED':
        return 'sd-badge--success';
      case 'ARRIVED':
      case 'CREATED':
      case 'MAINTENANCE':
        return 'sd-badge--warning';
      case 'CANCELLED':
      case 'INACTIVE':
        return 'sd-badge--danger';
      default:
        return 'sd-badge--info';
    }
  }

  private loadWarehouses(): void {
    this.loadingWarehouses.set(true);
    this.errorMessage.set(null);
    this.warehouseService
      .list()
      .pipe(finalize(() => this.loadingWarehouses.set(false)))
      .subscribe({
        next: (page) => {
          this.warehouses.set(page.content);
          if (page.content.length > 0) {
            this.warehouseControl.setValue(page.content[0].id);
          }
        },
        error: () => {
          this.warehouses.set([]);
          this.dashboard.set(null);
          this.errorMessage.set('We could not load the warehouses. Please try again.');
        },
      });
  }

  private loadDashboard(warehouseId: string): void {
    this.loadingDashboard.set(true);
    this.errorMessage.set(null);
    this.warehouseService
      .dashboard(warehouseId)
      .pipe(finalize(() => this.loadingDashboard.set(false)))
      .subscribe({
        next: (dashboard) => this.dashboard.set(dashboard),
        error: () => {
          this.dashboard.set(null);
          this.errorMessage.set('We could not load this warehouse dashboard. Please try again.');
        },
      });
  }
}
