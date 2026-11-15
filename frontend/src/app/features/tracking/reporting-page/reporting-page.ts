import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../../core/auth/problem-messages';
import { DeliveryInterval, ReportDashboardData, ReportFilters } from '../models/tracking.models';
import { TrackingApiService } from '../services/tracking-api.service';

type ReportState = 'loading' | 'success' | 'error';

@Component({
  selector: 'app-reporting-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reporting-page.html',
  styleUrl: './reporting-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportingPage implements OnInit {
  private readonly api = inject(TrackingApiService);

  protected readonly filterForm = new FormGroup({
    startDate: new FormControl(firstDayOfCurrentMonth(), { nonNullable: true }),
    endDate: new FormControl(today(), { nonNullable: true }),
    interval: new FormControl<DeliveryInterval>('daily', { nonNullable: true }),
  });
  protected readonly state = signal<ReportState>('loading');
  protected readonly data = signal<ReportDashboardData | null>(null);
  protected readonly error = signal('');
  protected readonly dateRangeError = signal('');
  protected readonly maximumStatusTotal = computed(() =>
    Math.max(0, ...(this.data()?.statusBreakdown.statuses.map((item) => item.total) ?? [])),
  );

  ngOnInit(): void {
    this.loadReports();
  }

  protected applyFilters(): void {
    this.loadReports();
  }

  protected retry(): void {
    this.loadReports();
  }

  protected statusLabel(status: string): string {
    return status
      .toLowerCase()
      .split('_')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  protected intervalLabel(): string {
    return this.filterForm.controls.interval.value === 'daily' ? 'Daily' : 'Monthly';
  }

  protected formatPeriod(value: string): string {
    const date = new Date(`${value}T00:00:00Z`);
    return new Intl.DateTimeFormat('en-LK', {
      year: 'numeric',
      month: this.filterForm.controls.interval.value === 'monthly' ? 'long' : 'short',
      day: this.filterForm.controls.interval.value === 'monthly' ? undefined : 'numeric',
      timeZone: 'UTC',
    }).format(date);
  }

  protected statusWidth(total: number): number {
    const maximum = this.maximumStatusTotal();
    return maximum === 0 ? 0 : (total / maximum) * 100;
  }

  private loadReports(): void {
    const { startDate, endDate, interval } = this.filterForm.getRawValue();
    if (startDate && endDate && startDate > endDate) {
      this.dateRangeError.set('Start date must be on or before end date.');
      return;
    }

    this.dateRangeError.set('');
    this.state.set('loading');
    this.error.set('');

    const filters: ReportFilters = {
      startDate: startDate || undefined,
      endDate: endDate || undefined,
    };
    this.api.getReports(filters, interval).subscribe({
      next: (data) => {
        this.data.set(data);
        this.state.set('success');
      },
      error: (error: unknown) => {
        this.state.set('error');
        this.error.set(
          errorMessage(error, {
            FORBIDDEN: 'You need the Dispatcher role to view tracking reports.',
            VALIDATION_FAILED: 'Check the report dates and try again.',
          }),
        );
      },
    });
  }
}

function today(): string {
  const now = new Date();
  return dateInputValue(now.getUTCFullYear(), now.getUTCMonth() + 1, now.getUTCDate());
}

function firstDayOfCurrentMonth(): string {
  const now = new Date();
  return dateInputValue(now.getUTCFullYear(), now.getUTCMonth() + 1, 1);
}

function dateInputValue(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}
