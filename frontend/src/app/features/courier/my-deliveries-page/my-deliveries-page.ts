import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, finalize } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/auth/problem-messages';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import {
  ASSIGNMENT_STATUS_LABELS,
  AssignmentStatus,
  CourierProfile,
  DeliveryAssignment,
  DeliveryIssue,
  FAILURE_REASON_LABELS,
  FailureReason,
  ISSUE_STATUS_LABELS,
  ISSUE_TYPE_LABELS,
  IssueStatus,
  IssueType,
  assignmentStatusClass,
  issueStatusClass,
  nextStep,
} from '../courier-delivery.models';
import { CourierDeliveryService } from '../courier-delivery.service';

type PanelKind = 'delivered' | 'failed' | 'issue';

const FAILURE_REASONS = Object.keys(FAILURE_REASON_LABELS) as FailureReason[];
const ISSUE_TYPES = Object.keys(ISSUE_TYPE_LABELS) as IssueType[];
const FINISHED: AssignmentStatus[] = ['DELIVERED', 'DELIVERY_FAILED', 'CANCELLED'];

/** A courier's own parcels: move each one forward, then record proof or a failed attempt. */
@Component({
  selector: 'app-my-deliveries-page',
  imports: [ColomboDatePipe, DecimalPipe, ReactiveFormsModule],
  templateUrl: './my-deliveries-page.html',
  styleUrl: './my-deliveries-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyDeliveriesPage implements OnInit {
  private readonly service = inject(CourierDeliveryService);
  private readonly auth = inject(AuthService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly statusLabels = ASSIGNMENT_STATUS_LABELS;
  protected readonly failureReasons = FAILURE_REASONS;
  protected readonly failureLabels = FAILURE_REASON_LABELS;
  protected readonly issueTypes = ISSUE_TYPES;
  protected readonly issueLabels = ISSUE_TYPE_LABELS;
  protected readonly issueStatusLabels = ISSUE_STATUS_LABELS;

  /** Problems this courier has reported, newest first, so they can see whether anyone has acted on them. */
  protected readonly issues = signal<DeliveryIssue[]>([]);
  protected readonly issuesFailed = signal(false);

  protected readonly profile = signal<CourierProfile | null>(null);
  protected readonly assignments = signal<DeliveryAssignment[]>([]);
  protected readonly loading = signal(true);
  /** True when the signed-in courier has no delivery profile yet (set up by a dispatcher). */
  protected readonly notSetUp = signal(false);
  protected readonly busyId = signal<string | null>(null);
  protected readonly panel = signal<{ id: string; kind: PanelKind } | null>(null);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly active = computed(() =>
    this.assignments().filter((assignment) => !FINISHED.includes(assignment.status)),
  );
  protected readonly finished = computed(() =>
    this.assignments().filter((assignment) => FINISHED.includes(assignment.status)),
  );

  protected readonly proofForm = this.formBuilder.group({
    recipientName: ['', [Validators.required, Validators.maxLength(120)]],
    notes: ['', Validators.maxLength(500)],
    collectedAmount: [null as number | null],
  });

  protected readonly failedForm = this.formBuilder.group({
    reason: ['' as FailureReason | '', Validators.required],
    notes: ['', Validators.maxLength(500)],
  });

  protected readonly issueForm = this.formBuilder.group({
    issueType: ['' as IssueType | '', Validators.required],
    description: ['', [Validators.required, Validators.maxLength(1000)]],
  });

  ngOnInit(): void {
    const user = this.auth.user();
    if (!user) {
      this.loading.set(false);
      return;
    }
    this.service.getCourierByIdentity(user.id).subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.loadAssignments(profile.id);
        this.loadIssues(profile.id);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.notSetUp.set(true);
        } else {
          this.error.set(errorMessage(error));
        }
      },
    });
  }

  protected step(assignment: DeliveryAssignment): { to: AssignmentStatus; label: string } | null {
    return nextStep(assignment.status);
  }

  protected advance(assignment: DeliveryAssignment): void {
    const next = nextStep(assignment.status);
    if (!next || this.busyId()) return;
    this.clearMessages();
    this.busyId.set(assignment.id);
    this.service
      .updateAssignmentStatus(assignment.id, next.to)
      .pipe(finalize(() => this.busyId.set(null)))
      .subscribe({
        next: (updated) => {
          this.replace(updated);
          this.notice.set(`${updated.parcelReference} is now ${this.statusLabels[updated.status].toLowerCase()}.`);
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected openPanel(assignment: DeliveryAssignment, kind: PanelKind): void {
    this.panel.set({ id: assignment.id, kind });
    this.submitted.set(false);
    this.clearMessages();
    this.proofForm.reset();
    this.failedForm.reset();
    this.issueForm.reset();
    // Cash on Delivery parcels need the amount handed over; no other parcel takes one.
    const amount = this.proofForm.controls.collectedAmount;
    if (kind === 'delivered' && assignment.codAmount != null) {
      amount.setValidators([Validators.required, Validators.min(0.01), Validators.max(9999999999.99)]);
    } else {
      amount.clearValidators();
    }
    amount.updateValueAndValidity();
  }

  protected closePanel(): void {
    this.panel.set(null);
    this.submitted.set(false);
  }

  protected isPanelOpen(assignment: DeliveryAssignment, kind: PanelKind): boolean {
    const open = this.panel();
    return open?.id === assignment.id && open.kind === kind;
  }

  protected submitDelivered(assignment: DeliveryAssignment): void {
    if (!this.guard(assignment, 'delivered', this.proofForm.invalid)) return;
    const value = this.proofForm.getRawValue();
    this.run(
      assignment,
      this.service.recordProof(assignment.id, {
        recipientName: value.recipientName.trim(),
        notes: value.notes.trim() || null,
        mediaUrl: null,
        deliveredAt: null,
        collectedAmount: assignment.codAmount != null ? value.collectedAmount : null,
      }),
      `${assignment.parcelReference} marked as delivered.`,
    );
  }

  protected submitFailed(assignment: DeliveryAssignment): void {
    if (!this.guard(assignment, 'failed', this.failedForm.invalid)) return;
    const value = this.failedForm.getRawValue();
    this.run(
      assignment,
      this.service.recordFailedAttempt(assignment.id, {
        reason: value.reason as FailureReason,
        notes: value.notes.trim() || null,
        attemptedAt: null,
      }),
      `${assignment.parcelReference} marked as failed. A dispatcher will arrange the next step.`,
    );
  }

  protected submitIssue(assignment: DeliveryAssignment): void {
    if (!this.guard(assignment, 'issue', this.issueForm.invalid)) return;
    const value = this.issueForm.getRawValue();
    this.run(
      assignment,
      this.service.reportIssue({
        courierId: assignment.courierId,
        assignmentId: assignment.id,
        parcelReference: null,
        issueType: value.issueType as IssueType,
        description: value.description.trim(),
      }),
      'Problem reported. A dispatcher will review it.',
    );
  }

  /** A phone number a handset can dial: digits and a leading plus only. */
  protected telHref(phone: string): string {
    return `tel:${phone.replace(/(?!^\+)[^\d]/g, '')}`;
  }

  protected mapsHref(address: string): string {
    return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`;
  }

  protected statusClass(status: AssignmentStatus): string {
    return assignmentStatusClass(status);
  }

  protected fieldError(
    form: 'proof' | 'failed' | 'issue',
    field: string,
  ): string | null {
    const group: FormGroup =
      form === 'proof' ? this.proofForm : form === 'failed' ? this.failedForm : this.issueForm;
    const control = group.get(field);
    if (!control || !(control.touched || this.submitted()) || !control.errors) return null;
    if (field === 'collectedAmount') {
      return control.errors['required'] ? 'Enter the cash you collected.' : 'Enter an amount greater than zero.';
    }
    if (control.errors['required']) {
      return field === 'reason' || field === 'issueType' ? 'Choose an option.' : 'This field is required.';
    }
    const limit = control.errors['maxlength']?.requiredLength;
    return limit ? `Use no more than ${limit} characters.` : 'Check this field.';
  }

  private guard(assignment: DeliveryAssignment, kind: PanelKind, invalid: boolean): boolean {
    this.submitted.set(true);
    this.clearMessages();
    if (!this.isPanelOpen(assignment, kind) || this.busyId()) return false;
    if (invalid) {
      this.proofForm.markAllAsTouched();
      this.failedForm.markAllAsTouched();
      this.issueForm.markAllAsTouched();
      return false;
    }
    return true;
  }

  private run(
    assignment: DeliveryAssignment,
    operation: Observable<unknown>,
    notice: string,
  ): void {
    this.busyId.set(assignment.id);
    operation.pipe(finalize(() => this.busyId.set(null))).subscribe({
      next: () => {
        this.closePanel();
        this.notice.set(notice);
        const profile = this.profile();
        if (profile) {
          this.loadAssignments(profile.id, false);
          this.loadIssues(profile.id);
        }
      },
      error: (error: unknown) => this.error.set(errorMessage(error)),
    });
  }

  private loadAssignments(courierId: string, showLoading = true): void {
    if (showLoading) this.loading.set(true);
    this.service
      .listAssignments({ courierId, size: 50 })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => this.assignments.set(page.content),
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected issueClass(status: IssueStatus): string {
    return issueStatusClass(status);
  }

  private loadIssues(courierId: string): void {
    this.service.listIssues({ courierId, size: 10 }).subscribe({
      next: (page) => {
        this.issues.set(page.content);
        this.issuesFailed.set(false);
      },
      // The rest of the page still works, so say so quietly instead of blocking the courier.
      error: () => this.issuesFailed.set(true),
    });
  }

  private replace(updated: DeliveryAssignment): void {
    this.assignments.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
