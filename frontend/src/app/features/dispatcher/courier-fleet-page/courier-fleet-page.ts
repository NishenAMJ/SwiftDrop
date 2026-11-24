import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/auth/problem-messages';
import { Courier as ApprovedCourier } from '../../courier/courier.models';
import {
  COURIER_STATUS_LABELS,
  CourierCategory,
  CourierProfile,
  CourierProfileStatus,
} from '../../courier/courier-delivery.models';
import { CourierDeliveryService } from '../../courier/courier-delivery.service';
import { DispatcherService } from '../dispatcher.service';

const STATUSES: CourierProfileStatus[] = ['AVAILABLE', 'OFF_DUTY', 'INACTIVE'];

/**
 * Delivery profiles for couriers. A profile is created from a courier whose application identity
 * has already approved; it adds the delivery category, phone and vehicle number.
 */
@Component({
  selector: 'app-courier-fleet-page',
  imports: [ReactiveFormsModule],
  templateUrl: './courier-fleet-page.html',
  styleUrl: './courier-fleet-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourierFleetPage implements OnInit {
  private readonly delivery = inject(CourierDeliveryService);
  private readonly dispatcher = inject(DispatcherService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly statuses = STATUSES;
  protected readonly statusLabels = COURIER_STATUS_LABELS;

  protected readonly categories = signal<CourierCategory[]>([]);
  protected readonly profiles = signal<CourierProfile[]>([]);
  private readonly approved = signal<ApprovedCourier[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly actionId = signal<string | null>(null);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly activeCategories = computed(() =>
    this.categories().filter((category) => category.status === 'ACTIVE'),
  );

  /** Approved couriers that do not have a delivery profile yet. */
  protected readonly unregistered = computed(() => {
    const registered = new Set(this.profiles().map((profile) => profile.identityUserId));
    return this.approved().filter((courier) => !registered.has(courier.userId));
  });

  protected readonly form = this.formBuilder.group({
    identityUserId: ['', Validators.required],
    phone: ['', [Validators.required, Validators.maxLength(30)]],
    vehicleNumber: ['', Validators.maxLength(30)],
    categoryId: ['', Validators.required],
  });

  ngOnInit(): void {
    this.reload();
  }

  protected onCourierPicked(): void {
    const picked = this.approved().find((c) => c.userId === this.form.controls.identityUserId.value);
    // Offer the registration number from the approved application as a starting point.
    this.form.controls.vehicleNumber.setValue(picked?.vehicleRegistrationNumber ?? '');
  }

  protected register(): void {
    this.submitted.set(true);
    this.clearMessages();
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const person = this.approved().find((c) => c.userId === value.identityUserId);
    if (!person) {
      this.error.set('Choose an approved courier from the list.');
      return;
    }
    this.saving.set(true);
    this.delivery
      .createCourier({
        identityUserId: person.userId,
        fullName: person.fullName,
        phone: value.phone.trim(),
        vehicleNumber: value.vehicleNumber.trim() || null,
        categoryId: value.categoryId,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (created) => {
          this.form.reset();
          this.submitted.set(false);
          this.profiles.update((list) => [...list, created]);
          this.notice.set(`${created.fullName} can now be assigned parcels.`);
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected changeStatus(profile: CourierProfile, status: CourierProfileStatus): void {
    if (this.actionId() || status === profile.status) return;
    this.clearMessages();
    this.actionId.set(profile.id);
    this.delivery
      .changeCourierStatus(profile.id, status)
      .pipe(finalize(() => this.actionId.set(null)))
      .subscribe({
        next: (updated) => {
          this.profiles.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
          this.notice.set(`${updated.fullName} is now ${COURIER_STATUS_LABELS[updated.status].toLowerCase()}.`);
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected statusClass(status: CourierProfileStatus): string {
    return status === 'AVAILABLE' ? 'sd-badge--success' : '';
  }

  protected fieldError(field: 'identityUserId' | 'phone' | 'vehicleNumber' | 'categoryId'): string | null {
    const control = this.form.controls[field];
    if (!(control.touched || this.submitted()) || !control.errors) return null;
    if (control.errors['required']) return 'This field is required.';
    const limit = control.errors['maxlength']?.requiredLength;
    return limit ? `Use no more than ${limit} characters.` : 'Check this field.';
  }

  private reload(): void {
    this.loading.set(true);
    let pending = 3;
    const done = () => {
      if (--pending === 0) this.loading.set(false);
    };
    const fail = (error: unknown) => {
      this.error.set(errorMessage(error));
      done();
    };
    this.delivery.listCategories().subscribe({
      next: (page) => {
        this.categories.set(page.content);
        done();
      },
      error: fail,
    });
    this.delivery.listCouriers({ size: 100 }).subscribe({
      next: (page) => {
        this.profiles.set(page.content);
        done();
      },
      error: fail,
    });
    this.dispatcher.listCouriers({ status: 'APPROVED', size: 100 }).subscribe({
      next: (page) => {
        this.approved.set(page.content);
        done();
      },
      error: fail,
    });
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
