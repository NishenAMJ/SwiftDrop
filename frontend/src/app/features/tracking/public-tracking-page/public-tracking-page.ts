import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/auth/problem-messages';
import { TrackingResult } from '../models/tracking.models';
import { TrackingApiService } from '../services/tracking-api.service';
import { TrackingLiveService } from '../services/tracking-live.service';

type SearchState = 'idle' | 'loading' | 'success' | 'not-found' | 'error';
type LiveState = 'unavailable' | 'connecting' | 'connected' | 'reconnecting';

const TRACKING_PATTERN = /^SWD-[0-9]{4}-[0-9]{6,}$/;

@Component({
  selector: 'app-public-tracking-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './public-tracking-page.html',
  styleUrl: './public-tracking-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicTrackingPage implements OnDestroy {
  private readonly api = inject(TrackingApiService);
  private readonly live = inject(TrackingLiveService);
  private readonly auth = inject(AuthService);
  private liveSubscription?: Subscription;

  protected readonly isSignedIn = this.auth.isSignedIn;
  protected readonly isDispatcher = () => this.auth.user()?.roles.includes('DISPATCHER') ?? false;
  protected readonly searchForm = new FormGroup({
    trackingNumber: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(TRACKING_PATTERN)],
    }),
  });
  protected readonly state = signal<SearchState>('idle');
  protected readonly result = signal<TrackingResult | null>(null);
  protected readonly historyNewestFirst = computed(() =>
    [...(this.result()?.history.events ?? [])].reverse(),
  );
  protected readonly message = signal('');
  protected readonly liveState = signal<LiveState>('unavailable');

  protected search(): void {
    const control = this.searchForm.controls.trackingNumber;
    control.setValue(control.value.trim().toUpperCase());
    control.markAsTouched();
    if (this.searchForm.invalid) {
      return;
    }

    this.stopLiveUpdates();
    this.state.set('loading');
    this.message.set('');
    this.result.set(null);

    this.api.getPublicTracking(control.value).subscribe({
      next: (result) => {
        this.result.set(result);
        this.state.set('success');
        this.startLiveUpdates(control.value);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.state.set('not-found');
          this.message.set('We could not find a parcel with that tracking number.');
          return;
        }
        this.state.set('error');
        this.message.set(
          errorMessage(error, {
            VALIDATION_FAILED: 'Check the tracking number and try again.',
          }),
        );
      },
    });
  }

  protected statusLabel(status: string): string {
    return status
      .toLowerCase()
      .split('_')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  protected badgeClass(status: string): string {
    if (status === 'DELIVERED') return 'sd-badge--success';
    if (status === 'DELIVERY_FAILED' || status === 'FAILED') return 'sd-badge--danger';
    if (status === 'OUT_FOR_DELIVERY') return 'sd-badge--accent';
    if (['PICKED_UP', 'IN_TRANSIT', 'ASSIGNED', 'AT_WAREHOUSE'].includes(status)) {
      return 'sd-badge--info';
    }
    return '';
  }

  protected formatDate(value: string): string {
    return new Intl.DateTimeFormat('en-LK', {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'Asia/Colombo',
    }).format(new Date(value));
  }

  ngOnDestroy(): void {
    this.stopLiveUpdates();
  }

  private startLiveUpdates(trackingNumber: string): void {
    if (!this.auth.isSignedIn()) {
      this.liveState.set('unavailable');
      return;
    }

    this.liveState.set('connecting');
    this.liveSubscription = this.live.connect(trackingNumber).subscribe((event) => {
      if (event.type === 'connected') {
        this.liveState.set('connected');
      } else if (event.type === 'reconnecting') {
        this.liveState.set('reconnecting');
      } else {
        this.refreshFromLiveUpdate(trackingNumber);
      }
    });
  }

  private refreshFromLiveUpdate(trackingNumber: string): void {
    this.api.getPublicTracking(trackingNumber).subscribe({
      next: (result) => this.result.set(result),
      error: () => {
        // Keep the last safe state visible; the stream will reconnect and try again.
        this.liveState.set('reconnecting');
      },
    });
  }

  private stopLiveUpdates(): void {
    this.liveSubscription?.unsubscribe();
    this.liveSubscription = undefined;
    this.liveState.set('unavailable');
  }
}
