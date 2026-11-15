import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';

import { Page } from '../../../core/auth/auth.models';
import { errorMessage } from '../../../core/auth/problem-messages';
import { ApplicationStatusBadge } from '../../../shared/components/application-status-badge/application-status-badge';
import { ConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { Pagination } from '../../../shared/components/pagination/pagination';
import {
  ApplicationDecision,
  ApplicationStatus,
  DECISIONS_BY_STATUS,
  DECISION_LABELS,
  STATUS_LABELS,
} from '../../../shared/models/application';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import { Courier, VEHICLE_TYPE_LABELS, VehicleType } from '../../courier/courier.models';
import { DECISION_COPY, DecisionFlow } from '../decision-flow';
import { DispatcherService } from '../dispatcher.service';

const PAGE_SIZE = 20;

/**
 * The dispatcher's courier applications: pending ones first, filterable by status and vehicle.
 * Approving a courier lets delivery-operations-service create their delivery profile.
 */
@Component({
  selector: 'app-courier-review-page',
  imports: [ReactiveFormsModule, ApplicationStatusBadge, ConfirmDialog, Pagination, ColomboDatePipe],
  templateUrl: './courier-review-page.html',
  styleUrl: './courier-review-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourierReviewPage implements OnInit {
  private readonly dispatcher = inject(DispatcherService);
  private readonly injector = inject(Injector);

  protected readonly statuses = Object.entries(STATUS_LABELS) as [ApplicationStatus, string][];
  protected readonly vehicleTypes = Object.entries(VEHICLE_TYPE_LABELS) as [VehicleType, string][];
  protected readonly vehicleLabels = VEHICLE_TYPE_LABELS;
  protected readonly copy = DECISION_COPY;
  protected readonly decisionLabels = DECISION_LABELS;
  protected readonly decisionsFor = (status: ApplicationStatus) => DECISIONS_BY_STATUS[status];

  protected readonly filters = inject(NonNullableFormBuilder).group({
    status: ['PENDING' as ApplicationStatus | ''],
    vehicleType: ['' as VehicleType | ''],
  });

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly result = signal<Page<Courier> | null>(null);
  protected readonly page = signal(0);
  protected readonly notice = signal<{ text: string; tone: 'success' | 'info' } | null>(null);

  private readonly resultsHeading = viewChild<ElementRef<HTMLElement>>('resultsHeading');

  protected readonly decision = new DecisionFlow<Courier>(
    (courier, decision, body) => this.dispatcher.decideCourier(courier.userId, decision, body),
    ({ message, stale }) => {
      this.notice.set({ text: message, tone: stale ? 'info' : 'success' });
      this.load({ focusResults: true });
    },
  );

  ngOnInit(): void {
    this.load();
  }

  protected search(): void {
    this.notice.set(null);
    this.page.set(0);
    this.load({ focusResults: true });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    this.load({ focusResults: true });
  }

  protected ask(courier: Courier, decision: ApplicationDecision): void {
    this.decision.ask(courier, courier.fullName, decision);
  }

  protected load(options: { focusResults?: boolean } = {}): void {
    const { status, vehicleType } = this.filters.getRawValue();
    this.loading.set(true);
    this.loadError.set(null);
    this.dispatcher
      .listCouriers({
        status: status || undefined,
        vehicleType: vehicleType || undefined,
        page: this.page(),
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (result) => {
          if (result.content.length === 0 && this.page() > 0 && result.totalPages > 0) {
            this.page.set(result.totalPages - 1);
            this.load(options);
            return;
          }
          this.result.set(result);
          this.loading.set(false);
          if (options.focusResults) {
            afterNextRender(() => this.resultsHeading()?.nativeElement.focus(), { injector: this.injector });
          }
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.loadError.set(errorMessage(error));
        },
      });
  }
}
