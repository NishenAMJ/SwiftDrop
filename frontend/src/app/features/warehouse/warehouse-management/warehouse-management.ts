import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/auth/problem-messages';
import { CreateWarehouseRequest, Warehouse, WarehouseStatus } from '../warehouse.models';
import { WarehouseService } from '../warehouse.service';

type WarehouseField =
  | 'code'
  | 'name'
  | 'addressLine1'
  | 'addressLine2'
  | 'city'
  | 'district'
  | 'postalCode'
  | 'contactName'
  | 'contactPhone'
  | 'contactEmail';

const STATUS_LABELS: Record<WarehouseStatus, string> = {
  ACTIVE: 'Active',
  MAINTENANCE: 'Maintenance',
  INACTIVE: 'Inactive',
};

@Component({
  selector: 'app-warehouse-management',
  imports: [ReactiveFormsModule],
  templateUrl: './warehouse-management.html',
  styleUrl: './warehouse-management.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WarehouseManagement implements OnInit {
  private readonly service = inject(WarehouseService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly changingStatusId = signal<string | null>(null);
  protected readonly submitted = signal(false);
  protected readonly showForm = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    code: [
      '',
      [Validators.required, Validators.maxLength(30), Validators.pattern(/^[A-Za-z0-9_-]+$/)],
    ],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    addressLine1: ['', [Validators.required, Validators.maxLength(160)]],
    addressLine2: ['', [Validators.maxLength(160)]],
    city: ['', [Validators.required, Validators.maxLength(80)]],
    district: ['', [Validators.required, Validators.maxLength(80)]],
    postalCode: ['', [Validators.maxLength(20)]],
    contactName: ['', [Validators.required, Validators.maxLength(120)]],
    contactPhone: ['', [Validators.required, Validators.maxLength(30)]],
    contactEmail: ['', [Validators.email, Validators.maxLength(160)]],
  });

  ngOnInit(): void {
    this.loadWarehouses();
  }

  protected beginCreate(): void {
    this.editingId.set(null);
    this.submitted.set(false);
    this.error.set(null);
    this.notice.set(null);
    this.form.reset();
    this.showForm.set(true);
  }

  protected beginEdit(warehouse: Warehouse): void {
    this.editingId.set(warehouse.id);
    this.submitted.set(false);
    this.error.set(null);
    this.notice.set(null);
    this.form.setValue({
      code: warehouse.code,
      name: warehouse.name,
      addressLine1: warehouse.addressLine1,
      addressLine2: warehouse.addressLine2 ?? '',
      city: warehouse.city,
      district: warehouse.district,
      postalCode: warehouse.postalCode ?? '',
      contactName: warehouse.contactName,
      contactPhone: warehouse.contactPhone,
      contactEmail: warehouse.contactEmail ?? '',
    });
    this.showForm.set(true);
  }

  protected cancelForm(): void {
    this.showForm.set(false);
    this.editingId.set(null);
    this.submitted.set(false);
    this.form.reset();
  }

  protected fieldError(field: WarehouseField): string | null {
    const control = this.form.controls[field];
    if (!(control.touched || this.submitted()) || !control.errors) return null;
    if (control.errors['required']) return 'This field is required.';
    if (control.errors['email']) return 'Enter a valid email address.';
    if (control.errors['pattern']) return 'Use letters, numbers, hyphens, or underscores only.';
    if (control.errors['maxlength']) {
      return `Use no more than ${control.errors['maxlength'].requiredLength} characters.`;
    }
    return 'Check this field.';
  }

  protected submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    this.notice.set(null);
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: CreateWarehouseRequest = {
      code: value.code.trim().toUpperCase(),
      name: value.name.trim(),
      addressLine1: value.addressLine1.trim(),
      addressLine2: this.optional(value.addressLine2),
      city: value.city.trim(),
      district: value.district.trim(),
      postalCode: this.optional(value.postalCode),
      contactName: value.contactName.trim(),
      contactPhone: value.contactPhone.trim(),
      contactEmail: this.optional(value.contactEmail),
    };
    const editingId = this.editingId();
    const operation = editingId
      ? this.service.update(editingId, {
          name: request.name,
          addressLine1: request.addressLine1,
          addressLine2: request.addressLine2,
          city: request.city,
          district: request.district,
          postalCode: request.postalCode,
          contactName: request.contactName,
          contactPhone: request.contactPhone,
          contactEmail: request.contactEmail,
        })
      : this.service.create(request);

    this.saving.set(true);
    operation.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (warehouse) => {
        this.upsert(warehouse);
        this.showForm.set(false);
        this.editingId.set(null);
        this.form.reset();
        this.submitted.set(false);
        this.notice.set(editingId ? 'Warehouse details updated.' : 'Warehouse created.');
      },
      error: (error: unknown) => {
        this.error.set(
          error instanceof HttpErrorResponse && error.status === 409
            ? 'A warehouse with this code already exists.'
            : errorMessage(error),
        );
      },
    });
  }

  protected changeStatus(warehouse: Warehouse, status: WarehouseStatus): void {
    if (warehouse.status === status || this.changingStatusId()) return;
    this.error.set(null);
    this.notice.set(null);
    this.changingStatusId.set(warehouse.id);
    this.service
      .changeStatus(warehouse.id, status)
      .pipe(finalize(() => this.changingStatusId.set(null)))
      .subscribe({
        next: (updated) => {
          this.upsert(updated);
          this.notice.set(
            `${updated.name} is now ${this.statusLabel(updated.status).toLowerCase()}.`,
          );
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected statusLabel(status: WarehouseStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusClass(status: WarehouseStatus): string {
    if (status === 'ACTIVE') return 'sd-badge--success';
    if (status === 'MAINTENANCE') return 'sd-badge--warning';
    return 'sd-badge--danger';
  }

  private loadWarehouses(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .list()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => this.warehouses.set(page.content),
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  private upsert(warehouse: Warehouse): void {
    this.warehouses.update((items) =>
      [...items.filter((item) => item.id !== warehouse.id), warehouse].sort((a, b) =>
        a.name.localeCompare(b.name),
      ),
    );
  }

  private optional(value: string): string | null {
    return value.trim() || null;
  }
}
