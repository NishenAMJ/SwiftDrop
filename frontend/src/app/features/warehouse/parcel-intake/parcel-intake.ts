import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/auth/problem-messages';
import { Warehouse, WarehouseParcel } from '../warehouse.models';
import { WarehouseService } from '../warehouse.service';

type IntakeField = 'parcelReference' | 'warehouseId' | 'arrivedAt' | 'notes';

@Component({
  selector: 'app-parcel-intake',
  imports: [ReactiveFormsModule],
  templateUrl: './parcel-intake.html',
  styleUrl: './parcel-intake.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParcelIntake implements OnInit {
  private readonly service = inject(WarehouseService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly loadingWarehouses = signal(true);
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly recordedParcel = signal<WarehouseParcel | null>(null);

  protected readonly form = this.formBuilder.group({
    parcelReference: ['', [Validators.required, Validators.maxLength(50)]],
    warehouseId: ['', Validators.required],
    arrivedAt: [''],
    notes: ['', Validators.maxLength(500)],
  });

  ngOnInit(): void {
    this.service
      .list()
      .pipe(finalize(() => this.loadingWarehouses.set(false)))
      .subscribe({
        next: (page) => {
          const active = page.content.filter((warehouse) => warehouse.status === 'ACTIVE');
          this.warehouses.set(active);
          if (active.length === 1) this.form.controls.warehouseId.setValue(active[0].id);
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected fieldError(field: IntakeField): string | null {
    const control = this.form.controls[field];
    if (!(control.touched || this.submitted()) || !control.errors) return null;
    if (control.errors['required']) return 'This field is required.';
    if (control.errors['maxlength']) {
      return `Use no more than ${control.errors['maxlength'].requiredLength} characters.`;
    }
    return 'Check this field.';
  }

  protected submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    this.recordedParcel.set(null);
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.service
      .recordArrival({
        parcelReference: value.parcelReference.trim().toUpperCase(),
        warehouseId: value.warehouseId,
        arrivedAt: value.arrivedAt ? new Date(value.arrivedAt).toISOString() : null,
        notes: value.notes.trim() || null,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (parcel) => {
          this.recordedParcel.set(parcel);
          this.submitted.set(false);
          this.form.controls.parcelReference.reset();
          this.form.controls.arrivedAt.reset();
          this.form.controls.notes.reset();
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }
}
