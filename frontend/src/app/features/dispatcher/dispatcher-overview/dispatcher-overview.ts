import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../../core/auth/problem-messages';
import {
  DeliveryCount,
  DispatcherOverviewData,
  ReportFilters,
} from '../../tracking/models/tracking.models';
import { TrackingApiService } from '../../tracking/services/tracking-api.service';

type OverviewState = 'loading' | 'success' | 'error';

interface OverviewPeriods {
  summary: ReportFilters;
  daily: ReportFilters;
  monthly: ReportFilters;
}

@Component({
  selector: 'app-dispatcher-overview',
  imports: [RouterLink],
  templateUrl: './dispatcher-overview.html',
  styleUrl: './dispatcher-overview.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DispatcherOverview implements OnInit {
  private readonly api = inject(TrackingApiService);
  private readonly periods = overviewPeriods();

  protected readonly state = signal<OverviewState>('loading');
  protected readonly data = signal<DispatcherOverviewData | null>(null);
  protected readonly error = signal('');
  protected readonly maximumStatusTotal = computed(() =>
    Math.max(0, ...(this.data()?.statusBreakdown.statuses.map((item) => item.total) ?? [])),
  );
  protected readonly dailyTotal = computed(() =>
    totalDelivered(this.data()?.dailyDeliveryCounts.counts ?? []),
  );
  protected readonly monthlyTotal = computed(() =>
    totalDelivered(this.data()?.monthlyDeliveryCounts.counts ?? []),
  );
  protected readonly summaryPeriod = `${formatDate(this.periods.summary.startDate!)}–${formatDate(
    this.periods.summary.endDate!,
  )}`;

  ngOnInit(): void {
    this.loadOverview();
  }

  protected retry(): void {
    this.loadOverview();
  }

  protected statusLabel(status: string): string {
    return status
      .toLowerCase()
      .split('_')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  protected statusClass(status: string): string {
    if (status === 'DELIVERED') return 'sd-badge--success';
    if (status === 'DELIVERY_FAILED' || status === 'FAILED') return 'sd-badge--danger';
    if (status === 'OUT_FOR_DELIVERY') return 'sd-badge--accent';
    if (['PICKED_UP', 'IN_TRANSIT', 'ASSIGNED', 'AT_WAREHOUSE'].includes(status)) {
      return 'sd-badge--info';
    }
    return '';
  }

  protected statusWidth(total: number): number {
    const maximum = this.maximumStatusTotal();
    return maximum === 0 ? 0 : (total / maximum) * 100;
  }

  protected formatDailyPeriod(period: string): string {
    return formatDate(period);
  }

  protected formatMonthlyPeriod(period: string): string {
    return new Intl.DateTimeFormat('en-LK', {
      year: 'numeric',
      month: 'long',
      timeZone: 'UTC',
    }).format(new Date(`${period}T00:00:00Z`));
  }

  private loadOverview(): void {
    this.state.set('loading');
    this.error.set('');
    this.api
      .getDispatcherOverview(this.periods.summary, this.periods.daily, this.periods.monthly)
      .subscribe({
        next: (data) => {
          this.data.set(data);
          this.state.set('success');
        },
        error: (error: unknown) => {
          this.state.set('error');
          this.error.set(
            errorMessage(error, {
              FORBIDDEN: 'You need the Dispatcher role to view the operations overview.',
              VALIDATION_FAILED: 'The reporting period could not be loaded. Please try again.',
            }),
          );
        },
      });
  }
}

function overviewPeriods(now = new Date()): OverviewPeriods {
  const year = now.getUTCFullYear();
  const month = now.getUTCMonth();
  const today = dateInputValue(year, month + 1, now.getUTCDate());
  const monthlyStart = new Date(Date.UTC(year, month - 11, 1));
  return {
    summary: { startDate: dateInputValue(year, month + 1, 1), endDate: today },
    daily: { startDate: dateInputValue(year, month + 1, 1), endDate: today },
    monthly: {
      startDate: dateInputValue(monthlyStart.getUTCFullYear(), monthlyStart.getUTCMonth() + 1, 1),
      endDate: today,
    },
  };
}

function totalDelivered(counts: DeliveryCount[]): number {
  return counts.reduce((total, item) => total + item.deliveredParcels, 0);
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('en-LK', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`));
}

function dateInputValue(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}
