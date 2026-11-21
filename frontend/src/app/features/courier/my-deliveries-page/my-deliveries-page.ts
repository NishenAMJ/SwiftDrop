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
  FAILURE_REASON_LABELS,
  FailureReason,
  ISSUE_TYPE_LABELS,
  IssueType,
  assignmentStatusClass,
  nextStep,
} from '../courier-delivery.models';
import { CourierDeliveryService } from '../courier-delivery.service';

type PanelKind = 'delivered' | 'failed' | 'issue';

const FAILURE_REASONS = Object.keys(FAILURE_REASON_LABELS) as FailureReason[];
const ISSUE_TYPES = Object.keys(ISSUE_TYPE_LABELS) as IssueType[];
const FINISHED: AssignmentStatus[] = ['DELIVERED', 'DELIVERY_FAILED'];

/** A courier's own parcels: move each one forward, then record proof or a failed attempt. */
@Component({
  selector: 'app-my-deliveries-page',
  imports: [ColomboDatePipe, ReactiveFormsModule],
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
        if (profile) this.loadAssignments(profile.id, false);
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

  private replace(updated: DeliveryAssignment): void {
    this.assignments.update((list) => list.map((a) => (a.id === updated.id ? updated : a)));
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
