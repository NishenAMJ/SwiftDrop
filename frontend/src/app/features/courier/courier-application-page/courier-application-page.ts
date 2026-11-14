import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage, readProblem } from '../../../core/auth/problem-messages';
import { ApplicationStatusBadge } from '../../../shared/components/application-status-badge/application-status-badge';
import {
  FieldMessages,
  applyServerErrors,
  fieldErrorMessage,
  focusFirstInvalid,
} from '../../../shared/forms/form-errors';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import {
  Courier,
  CourierApplicationRequest,
  VEHICLE_TYPE_LABELS,
  VehicleType,
  needsVehicleDocuments,
} from '../courier.models';
import { CourierService } from '../courier.service';

type Field = 'nationalIdNumber' | 'vehicleType' | 'drivingLicenceNumber' | 'vehicleRegistrationNumber';

const MESSAGES: Record<Field, FieldMessages> = {
  nationalIdNumber: { required: 'Enter your national identity card number.' },
  vehicleType: { required: 'Choose the vehicle you will deliver with.' },
  drivingLicenceNumber: { required: 'Enter your driving licence number.' },
  vehicleRegistrationNumber: { required: "Enter the vehicle's registration number." },
};

const SUBMIT_ERRORS: Record<string, string> = {
  FORBIDDEN: 'Only customer accounts can apply to become a courier.',
};

const DOCUMENT_VALIDATORS = [Validators.required, Validators.maxLength(20), Validators.pattern(/\S/)];

type LoadState = 'loading' | 'ready' | 'error';

/**
 * A customer applies to deliver for SwiftDrop, follows the decision, and resubmits after a
 * rejection. Identity only keeps what is needed to approve a courier; delivery work itself is
 * handled by delivery-operations-service.
 */
@Component({
  selector: 'app-courier-application-page',
  imports: [ReactiveFormsModule, ApplicationStatusBadge, ColomboDatePipe],
  templateUrl: './courier-application-page.html',
  styleUrl: './courier-application-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourierApplicationPage implements OnInit {
  private readonly couriers = inject(CourierService);
  private readonly auth = inject(AuthService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly vehicleTypes = Object.entries(VEHICLE_TYPE_LABELS) as [VehicleType, string][];
  protected readonly vehicleLabels = VEHICLE_TYPE_LABELS;

  protected readonly state = signal<LoadState>('loading');
  protected readonly courier = signal<Courier | null>(null);
  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly needsDocuments = signal(false);
  protected readonly isCourier = computed(() => this.auth.roles().includes('COURIER'));

  protected readonly canApply = computed(() => {
    const courier = this.courier();
    return courier === null || courier.status === 'REJECTED';
  });

  private readonly statusHeading = viewChild<ElementRef<HTMLElement>>('statusHeading');
  private readonly errorAlert = viewChild<ElementRef<HTMLElement>>('errorAlert');

  protected readonly form = this.fb.group({
    nationalIdNumber: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(20), Validators.pattern(/\S/)]],
    vehicleType: ['' as VehicleType | '', Validators.required],
    drivingLicenceNumber: ['', Validators.maxLength(20)],
    vehicleRegistrationNumber: ['', Validators.maxLength(20)],
  });

  constructor() {
    // A bicycle needs no licence or registration; every other vehicle needs both.
    this.form.controls.vehicleType.valueChanges.pipe(takeUntilDestroyed()).subscribe((type) => {
      const needed = needsVehicleDocuments(type);
      this.needsDocuments.set(needed);
      for (const control of [this.form.controls.drivingLicenceNumber, this.form.controls.vehicleRegistrationNumber]) {
        control.setValidators(needed ? DOCUMENT_VALIDATORS : Validators.maxLength(20));
        control.updateValueAndValidity();
      }
    });
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.couriers.me().subscribe({
      next: (courier) => this.show(courier),
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.courier.set(null);
          this.state.set('ready');
        } else {
          this.state.set('error');
        }
      },
    });
  }

  protected error(field: Field): string | null {
    return fieldErrorMessage(this.form.get(field), this.submitted(), MESSAGES[field]);
  }

  protected submit(): void {
    this.submitted.set(true);
    this.serverError.set(null);
    this.notice.set(null);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
      return;
    }
    this.submitting.set(true);
    const wasRejected = this.courier()?.status === 'REJECTED';
    this.couriers.apply(this.request()).subscribe({
      next: (courier) => {
        this.submitting.set(false);
        this.submitted.set(false);
        this.notice.set(
          wasRejected
            ? 'Application sent again. A dispatcher will review it.'
            : 'Application sent. A dispatcher will review it.',
        );
        this.show(courier);
        afterNextRender(() => this.statusHeading()?.nativeElement.focus(), { injector: this.injector });
      },
      error: (error: unknown) => this.handleSubmitError(error),
    });
  }

  private show(courier: Courier): void {
    this.courier.set(courier);
    this.state.set('ready');
    if (courier.status === 'REJECTED') {
      this.form.reset({
        nationalIdNumber: courier.nationalIdNumber ?? '',
        vehicleType: courier.vehicleType,
        drivingLicenceNumber: courier.drivingLicenceNumber ?? '',
        vehicleRegistrationNumber: courier.vehicleRegistrationNumber ?? '',
      });
    }
    // Approved while signed in: the session still has the old roles until a refresh.
    if (courier.status === 'APPROVED' && !this.isCourier()) {
      this.auth.refresh().subscribe({ error: () => undefined });
    }
  }

  private request(): CourierApplicationRequest {
    const value = this.form.getRawValue();
    const request: CourierApplicationRequest = {
      nationalIdNumber: value.nationalIdNumber.trim(),
      vehicleType: value.vehicleType as VehicleType,
    };
    // Leave out documents for a bicycle, even if something was typed before switching.
    if (this.needsDocuments()) {
      request.drivingLicenceNumber = value.drivingLicenceNumber.trim();
      request.vehicleRegistrationNumber = value.vehicleRegistrationNumber.trim();
    }
    return request;
  }

  private handleSubmitError(error: unknown): void {
    this.submitting.set(false);
    const problem = readProblem(error);
    if (problem.code === 'APPLICATION_ALREADY_EXISTS') {
      this.notice.set('You already have a courier application. Here is its current status.');
      this.load();
      return;
    }
    if (applyServerErrors(this.form, problem)) {
      afterNextRender(() => focusFirstInvalid(this.host.nativeElement), { injector: this.injector });
      return;
    }
    this.serverError.set(errorMessage(error, SUBMIT_ERRORS));
    afterNextRender(() => this.errorAlert()?.nativeElement.focus(), { injector: this.injector });
  }
}
