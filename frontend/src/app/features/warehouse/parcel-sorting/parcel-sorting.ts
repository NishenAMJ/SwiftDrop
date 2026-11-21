import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/auth/problem-messages';
import { SortingRecord, Warehouse, WarehouseParcel } from '../warehouse.models';
import { WarehouseService } from '../warehouse.service';

type SortingField =
  'parcelReference' | 'warehouseId' | 'destination' | 'nextWarehouseId' | 'sortedAt' | 'notes';

@Component({
  selector: 'app-parcel-sorting',
  imports: [ReactiveFormsModule],
  templateUrl: './parcel-sorting.html',
  styleUrl: './parcel-sorting.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParcelSorting implements OnInit {
  private readonly service = inject(WarehouseService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly parcel = signal<WarehouseParcel | null>(null);
  protected readonly record = signal<SortingRecord | null>(null);
  protected readonly loadingWarehouses = signal(true);
  protected readonly lookingUp = signal(false);
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    parcelReference: ['', [Validators.required, Validators.maxLength(50)]],
    warehouseId: ['', Validators.required],
    destination: ['', [Validators.required, Validators.maxLength(160)]],
    nextWarehouseId: [''],
    sortedAt: [''],
    notes: ['', Validators.maxLength(500)],
  });

  ngOnInit(): void {
    this.service
      .list()
      .pipe(finalize(() => this.loadingWarehouses.set(false)))
      .subscribe({
        next: (page) =>
          this.warehouses.set(page.content.filter((item) => item.status === 'ACTIVE')),
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected fieldError(field: SortingField): string | null {
    const control = this.form.controls[field];
    if (!(control.touched || this.submitted()) || !control.errors) return null;
    if (control.errors['required']) return 'This field is required.';
    if (control.errors['maxlength']) {
      return `Use no more than ${control.errors['maxlength'].requiredLength} characters.`;
    }
    return 'Check this field.';
  }

  protected lookupParcel(): void {
    const reference = this.form.controls.parcelReference.value.trim();
    this.form.controls.parcelReference.markAsTouched();
    if (!reference || this.lookingUp()) return;
    this.error.set(null);
    this.parcel.set(null);
    this.record.set(null);
    this.lookingUp.set(true);
    this.service
      .getParcel(reference.toUpperCase())
      .pipe(finalize(() => this.lookingUp.set(false)))
      .subscribe({
        next: (parcel) => {
          this.parcel.set(parcel);
          this.form.controls.parcelReference.setValue(parcel.parcelReference);
          this.form.controls.warehouseId.setValue(parcel.warehouseId);
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    this.record.set(null);
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (value.nextWarehouseId && value.nextWarehouseId === value.warehouseId) {
      this.error.set('The next warehouse must be different from the current warehouse.');
      return;
    }
    this.saving.set(true);
    this.service
      .recordSorting({
        parcelReference: value.parcelReference.trim().toUpperCase(),
        warehouseId: value.warehouseId,
        nextWarehouseId: value.nextWarehouseId || null,
        destination: value.destination.trim(),
        notes: value.notes.trim() || null,
        sortedAt: value.sortedAt ? new Date(value.sortedAt).toISOString() : null,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (record) => {
          this.record.set(record);
          this.parcel.update((parcel) => (parcel ? { ...parcel, state: 'SORTED' } : parcel));
          this.submitted.set(false);
          this.form.controls.destination.reset();
          this.form.controls.nextWarehouseId.reset();
          this.form.controls.sortedAt.reset();
          this.form.controls.notes.reset();
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }
}
