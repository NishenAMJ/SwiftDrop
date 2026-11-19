import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/auth/problem-messages';
import { PageResponse, ParcelTransfer, TransferStatus, Warehouse } from '../warehouse.models';
import { WarehouseService } from '../warehouse.service';

const EMPTY_PAGE: PageResponse<ParcelTransfer> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 20,
  number: 0,
  first: true,
  last: true,
  empty: true,
};

const STATUS_LABELS: Record<TransferStatus, string> = {
  CREATED: 'Created',
  IN_TRANSIT: 'In transit',
  RECEIVED: 'Received',
  CANCELLED: 'Cancelled',
};

@Component({
  selector: 'app-transfer-management',
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './transfer-management.html',
  styleUrl: './transfer-management.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransferManagement implements OnInit {
  private readonly service = inject(WarehouseService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly transfers = signal<PageResponse<ParcelTransfer>>(EMPTY_PAGE);
  protected readonly loading = signal(true);
  protected readonly loadingWarehouses = signal(true);
  protected readonly creating = signal(false);
  protected readonly actionId = signal<string | null>(null);
  protected readonly receivingId = signal<string | null>(null);
  protected readonly confirmingCancelId = signal<string | null>(null);
  protected readonly createSubmitted = signal(false);
  protected readonly receiveSubmitted = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly filters = this.formBuilder.group({
    parcelReference: ['', Validators.maxLength(50)],
    status: ['' as TransferStatus | ''],
  });

  protected readonly createForm = this.formBuilder.group({
    parcelReference: ['', [Validators.required, Validators.maxLength(50)]],
    sourceWarehouseId: ['', Validators.required],
    destinationWarehouseId: ['', Validators.required],
  });

  protected readonly receiveForm = this.formBuilder.group({
    receivedBy: ['', [Validators.required, Validators.maxLength(120)]],
    receivedAt: [''],
    notes: ['', Validators.maxLength(500)],
  });

  ngOnInit(): void {
    this.service
      .list()
      .pipe(finalize(() => this.loadingWarehouses.set(false)))
      .subscribe({
        next: (page) =>
          this.warehouses.set(page.content.filter((warehouse) => warehouse.status === 'ACTIVE')),
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
    this.loadTransfers(0);
  }

  protected applyFilters(): void {
    this.loadTransfers(0);
  }

  protected clearFilters(): void {
    this.filters.reset();
    this.loadTransfers(0);
  }

  protected previousPage(): void {
    const page = this.transfers();
    if (!page.first) this.loadTransfers(page.number - 1);
  }

  protected nextPage(): void {
    const page = this.transfers();
    if (!page.last) this.loadTransfers(page.number + 1);
  }

  protected createTransfer(): void {
    this.createSubmitted.set(true);
    this.clearMessages();
    if (this.createForm.invalid || this.creating()) {
      this.createForm.markAllAsTouched();
      return;
    }
    const value = this.createForm.getRawValue();
    if (value.sourceWarehouseId === value.destinationWarehouseId) {
      this.error.set('Choose a destination warehouse different from the source warehouse.');
      return;
    }
    this.creating.set(true);
    this.service
      .createTransfer({
        parcelReference: value.parcelReference.trim().toUpperCase(),
        sourceWarehouseId: value.sourceWarehouseId,
        destinationWarehouseId: value.destinationWarehouseId,
      })
      .pipe(finalize(() => this.creating.set(false)))
      .subscribe({
        next: () => {
          this.createForm.reset();
          this.createSubmitted.set(false);
          this.notice.set(
            'Transfer created. Dispatch it when the parcel leaves the source warehouse.',
          );
          this.loadTransfers(0, false);
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected dispatch(transfer: ParcelTransfer): void {
    if (transfer.status !== 'CREATED') return;
    this.runAction(
      transfer.id,
      this.service.dispatchTransfer(transfer.id),
      'Transfer marked as in transit.',
    );
  }

  protected requestCancel(transfer: ParcelTransfer): void {
    if (transfer.status !== 'CREATED') return;
    this.confirmingCancelId.set(transfer.id);
    this.receivingId.set(null);
  }

  protected dismissCancel(): void {
    this.confirmingCancelId.set(null);
  }

  protected cancel(transfer: ParcelTransfer): void {
    if (this.confirmingCancelId() !== transfer.id) return;
    this.runAction(transfer.id, this.service.cancelTransfer(transfer.id), 'Transfer cancelled.');
    this.confirmingCancelId.set(null);
  }

  protected beginReceive(transfer: ParcelTransfer): void {
    if (transfer.status !== 'IN_TRANSIT') return;
    this.receivingId.set(transfer.id);
    this.confirmingCancelId.set(null);
    this.receiveSubmitted.set(false);
    this.receiveForm.reset();
    this.clearMessages();
  }

  protected dismissReceive(): void {
    this.receivingId.set(null);
    this.receiveSubmitted.set(false);
    this.receiveForm.reset();
  }

  protected receive(transfer: ParcelTransfer): void {
    this.receiveSubmitted.set(true);
    this.clearMessages();
    if (this.receivingId() !== transfer.id || this.receiveForm.invalid || this.actionId()) {
      this.receiveForm.markAllAsTouched();
      return;
    }
    const value = this.receiveForm.getRawValue();
    this.runAction(
      transfer.id,
      this.service.receiveTransfer(transfer.id, {
        receivedBy: value.receivedBy.trim(),
        receivedAt: value.receivedAt ? new Date(value.receivedAt).toISOString() : null,
        notes: value.notes.trim() || null,
      }),
      'Transfer received at the destination warehouse.',
      () => this.dismissReceive(),
    );
  }

  protected statusLabel(status: TransferStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusClass(status: TransferStatus): string {
    if (status === 'RECEIVED') return 'sd-badge--success';
    if (status === 'IN_TRANSIT') return 'sd-badge--info';
    if (status === 'CREATED') return 'sd-badge--warning';
    return 'sd-badge--danger';
  }

  protected createError(
    field: 'parcelReference' | 'sourceWarehouseId' | 'destinationWarehouseId',
  ): string | null {
    const control = this.createForm.controls[field];
    if (!(control.touched || this.createSubmitted()) || !control.errors) return null;
    if (control.errors['required']) return 'This field is required.';
    return 'Use no more than 50 characters.';
  }

  protected receiveError(field: 'receivedBy' | 'notes'): string | null {
    const control = this.receiveForm.controls[field];
    if (!(control.touched || this.receiveSubmitted()) || !control.errors) return null;
    if (control.errors['required']) return 'This field is required.';
    const limit = control.errors['maxlength']?.requiredLength;
    return limit ? `Use no more than ${limit} characters.` : 'Check this field.';
  }

  private loadTransfers(page: number, showLoading = true): void {
    if (showLoading) this.loading.set(true);
    this.error.set(null);
    const value = this.filters.getRawValue();
    this.service
      .listTransfers({
        parcelReference: value.parcelReference.trim().toUpperCase(),
        status: value.status,
        page,
        size: 20,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => this.transfers.set(response),
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  private runAction(
    transferId: string,
    operation: ReturnType<WarehouseService['dispatchTransfer']>,
    notice: string,
    afterSuccess?: () => void,
  ): void {
    if (this.actionId()) return;
    this.clearMessages();
    this.actionId.set(transferId);
    operation.pipe(finalize(() => this.actionId.set(null))).subscribe({
      next: (updated) => {
        this.replaceTransfer(updated);
        afterSuccess?.();
        this.notice.set(notice);
      },
      error: (error: unknown) => this.error.set(errorMessage(error)),
    });
  }

  private replaceTransfer(updated: ParcelTransfer): void {
    this.transfers.update((page) => ({
      ...page,
      content: page.content.map((transfer) => (transfer.id === updated.id ? updated : transfer)),
    }));
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
