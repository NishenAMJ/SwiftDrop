import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ApplicationStatus, STATUS_LABELS } from '../../models/application';

const BADGE_CLASS: Record<ApplicationStatus, string> = {
  PENDING: 'sd-badge--warning',
  APPROVED: 'sd-badge--success',
  REJECTED: 'sd-badge--danger',
  SUSPENDED: 'sd-badge--danger',
};

/**
 * Approval status of a merchant or courier application, always as words (frontend/style.md
 * section 9). Used by applicants and dispatchers so both see the same labels.
 */
@Component({
  selector: 'app-application-status-badge',
  template: `<span [class]="badgeClass()">{{ label() }}</span>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplicationStatusBadge {
  readonly status = input.required<ApplicationStatus>();

  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
  protected readonly badgeClass = computed(() => `sd-badge ${BADGE_CLASS[this.status()]}`);
}
