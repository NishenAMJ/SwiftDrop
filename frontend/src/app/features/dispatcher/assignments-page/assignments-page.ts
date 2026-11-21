import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/auth/problem-messages';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import {
  ASSIGNMENT_STATUS_LABELS,
  AssignmentStatus,
  CourierProfile,
  DeliveryAssignment,
  DeliveryAttempt,
  DeliveryPage,
  EMPTY_DELIVERY_PAGE,
  FAILURE_REASON_LABELS,
  ProofOfDelivery,
  assignmentStatusClass,
} from '../../courier/courier-delivery.models';
import { CourierDeliveryService } from '../../courier/courier-delivery.service';

const STATUS_OPTIONS = Object.keys(ASSIGNMENT_STATUS_LABELS) as AssignmentStatus[];

@Component({
  selector: 'app-assignments-page',
  imports: [ColomboDatePipe, ReactiveFormsModule],
  templateUrl: './assignments-page.html',
  styleUrl: './assignments-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssignmentsPage implements OnInit {
  private readonly service = inject(CourierDeliveryService);
  private readonly auth = inject(AuthService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly statusLabels = ASSIGNMENT_STATUS_LABELS;
  protected readonly failureLabels = FAILURE_REASON_LABELS;

  private readonly courierSelect = viewChild<ElementRef<HTMLSelectElement>>('courierSelect');

  /** The assignment whose attempts or proof are open, with what was loaded for it. */
  protected readonly detailsId = signal<string | null>(null);
  protected readonly detailsLoading = signal(false);
  protected readonly detailsError = signal<string | null>(null);
  protected readonly attempts = signal<DeliveryAttempt[]>([]);
  protected readonly proof = signal<ProofOfDelivery | null>(null);

  protected readonly couriers = signal<CourierProfile[]>([]);
  protected readonly assignments = signal<DeliveryPage<DeliveryAssignment>>(EMPTY_DELIVERY_PAGE);
  protected readonly loading = signal(true);
  protected readonly loadingCouriers = signal(true);
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly assignForm = this.formBuilder.group({
    parcelReference: ['', [Validators.required, Validators.maxLength(50)]],
    courierId: ['', Validators.required],
    notes: ['', Validators.maxLength(500)],
  });

  protected readonly filters = this.formBuilder.group({
    parcelReference: ['', Validators.maxLength(50)],
    status: ['' as AssignmentStatus | ''],
  });

  ngOnInit(): void {
    this.service
      .listCouriers({ status: 'AVAILABLE', size: 100 })
      .pipe(finalize(() => this.loadingCouriers.set(false)))
      .subscribe({
        next: (page) => this.couriers.set(page.content),
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
    this.load(0);
  }

  protected assign(): void {
    this.submitted.set(true);
    this.clearMessages();
    if (this.assignForm.invalid || this.saving()) {
      this.assignForm.markAllAsTouched();
      return;
    }
    const value = this.assignForm.getRawValue();
    this.saving.set(true);
    this.service
      .assign({
        parcelReference: value.parcelReference.trim().toUpperCase(),
        courierId: value.courierId,
        // The signed-in dispatcher is recorded as the person who assigned the parcel.
        assignedBy: this.auth.user()?.fullName ?? 'Dispatcher',
        notes: value.notes.trim() || null,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (created) => {
          this.assignForm.reset();
          this.submitted.set(false);
          this.notice.set(`${created.parcelReference} assigned to ${created.courierName}.`);
          this.load(0, false);
        },
        error: (error: unknown) => this.error.set(this.assignFailure(error)),
      });
  }

  /** Plain-language reasons for the ways assigning a parcel can fail; raw server text is never shown. */
  private assignFailure(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 404:
          return "We couldn't find a parcel with that reference. Check the tracking number and try again.";
        case 409:
          return 'This parcel already has an active courier assignment.';
        case 422:
          return 'This courier cannot take a parcel right now. Check that they are available and in an active category.';
        case 503:
          return "The parcel couldn't be checked right now. Please try again in a moment.";
      }
    }
    return errorMessage(error);
  }

  /** Opens or closes what happened to a finished assignment: why it failed, or who received it. */
  protected toggleDetails(assignment: DeliveryAssignment): void {
    if (this.detailsId() === assignment.id) {
      this.detailsId.set(null);
      return;
    }
    this.detailsId.set(assignment.id);
    this.detailsError.set(null);
    this.attempts.set([]);
    this.proof.set(null);
    this.detailsLoading.set(true);

    if (assignment.status === 'DELIVERY_FAILED') {
      this.service
        .listAttempts(assignment.id)
        .pipe(finalize(() => this.detailsLoading.set(false)))
        .subscribe({
          next: (attempts) => this.attempts.set(attempts),
          error: (error: unknown) => this.detailsError.set(errorMessage(error)),
        });
    } else {
      this.service
        .getProof(assignment.id)
        .pipe(finalize(() => this.detailsLoading.set(false)))
        .subscribe({
          next: (proof) => this.proof.set(proof),
          error: (error: unknown) => this.detailsError.set(errorMessage(error)),
        });
    }
  }

  /** Starts a new assignment for a parcel whose delivery failed: fills in the parcel and moves to the courier. */
  protected reassign(assignment: DeliveryAssignment): void {
    this.clearMessages();
    const select = this.courierSelect();
    if (!select) {
      this.error.set('No couriers are available right now. Set a courier to Available on the Couriers page.');
      return;
    }
    this.assignForm.reset({ parcelReference: assignment.parcelReference, courierId: '', notes: '' });
    this.submitted.set(false);
    this.notice.set(`Choose a courier to try ${assignment.parcelReference} again.`);
    select.nativeElement.focus();
  }

  protected applyFilters(): void {
    this.load(0);
  }

  protected clearFilters(): void {
    this.filters.reset();
    this.load(0);
  }

  protected previousPage(): void {
    const page = this.assignments();
    if (!page.first) this.load(page.number - 1);
  }

  protected nextPage(): void {
    const page = this.assignments();
    if (!page.last) this.load(page.number + 1);
  }

  protected statusClass(status: AssignmentStatus): string {
    return assignmentStatusClass(status);
  }

  protected assignError(field: 'parcelReference' | 'courierId' | 'notes'): string | null {
    const control = this.assignForm.controls[field];
    if (!(control.touched || this.submitted()) || !control.errors) return null;
    if (control.errors['required']) return 'This field is required.';
    const limit = control.errors['maxlength']?.requiredLength;
    return limit ? `Use no more than ${limit} characters.` : 'Check this field.';
  }

  private load(page: number, showLoading = true): void {
    if (showLoading) this.loading.set(true);
    const value = this.filters.getRawValue();
    this.service
      .listAssignments({
        parcelReference: value.parcelReference.trim().toUpperCase(),
        status: value.status,
        page,
        size: 20,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => this.assignments.set(response),
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
