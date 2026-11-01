import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { homeFor } from '../../../core/auth/auth-routing';
import { errorMessage, readProblem } from '../../../core/auth/problem-messages';

interface Field {
  name: string;
  label: string;
  required?: boolean;
  max: number;
  min?: number;
  options?: string[];
  type?: string;
}
interface Application {
  lastDecision?: { reason?: string };
  businessAddress?: Record<string, string>;
  [key: string]: unknown;
}
@Component({
  selector: 'app-onboarding-page',
  imports: [ReactiveFormsModule],
  templateUrl: './onboarding-page.html',
  styleUrl: './onboarding-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  protected readonly user = this.auth.user;
  protected readonly merchant = this.user()?.accountRole === 'MERCHANT';
  private readonly endpoint = '/api/v1/' + (this.merchant ? 'merchants' : 'couriers');
  protected readonly busy = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly reason = signal('');
  protected readonly editable = computed(() =>
    ['APPLICATION_REQUIRED', 'REJECTED'].includes(this.user()?.onboardingState ?? ''),
  );
  protected readonly statusLabel = computed(
    () =>
      ({
        APPLICATION_REQUIRED: 'Application required',
        PENDING: 'Pending approval',
        REJECTED: 'Rejected',
        SUSPENDED: 'Suspended',
        READY: 'Approved',
        ROLE_REQUIRED: 'Choose a role',
      })[this.user()?.onboardingState ?? 'APPLICATION_REQUIRED'],
  );
  protected readonly fields: Field[] = this.merchant
    ? [
        { name: 'businessName', label: 'Business name', required: true, min: 2, max: 120 },
        {
          name: 'businessType',
          label: 'Business type',
          required: true,
          max: 30,
          options: ['INDIVIDUAL', 'SOLE_PROPRIETORSHIP', 'PARTNERSHIP', 'COMPANY'],
        },
        { name: 'businessRegistrationNumber', label: 'Business registration number', max: 50 },
        { name: 'contactEmail', label: 'Contact email', type: 'email', max: 254 },
        {
          name: 'contactPhone',
          label: 'Contact phone (+country code)',
          required: true,
          type: 'tel',
          max: 16,
        },
        { name: 'line1', label: 'Address line 1', required: true, max: 120 },
        { name: 'line2', label: 'Address line 2', max: 120 },
        { name: 'city', label: 'City', required: true, max: 80 },
        { name: 'postalCode', label: 'Postal code', max: 20 },
      ]
    : [
        { name: 'nationalIdNumber', label: 'National ID number', required: true, min: 5, max: 20 },
        {
          name: 'vehicleType',
          label: 'Vehicle type',
          required: true,
          max: 20,
          options: ['BICYCLE', 'MOTORCYCLE', 'THREE_WHEELER', 'CAR', 'VAN', 'TRUCK'],
        },
        { name: 'drivingLicenceNumber', label: 'Driving licence number', max: 20 },
        { name: 'vehicleRegistrationNumber', label: 'Vehicle registration number', max: 20 },
      ];
  protected readonly form = new FormGroup<Record<string, FormControl<string>>>(
    Object.fromEntries(
      this.fields.map((f) => [
        f.name,
        new FormControl(f.options?.[0] ?? '', {
          nonNullable: true,
          validators: [
            Validators.maxLength(f.max),
            ...(f.required ? [Validators.required, Validators.pattern(/\S/)] : []),
            ...(f.min ? [Validators.minLength(f.min)] : []),
            ...(f.type === 'email' ? [Validators.email] : []),
            ...(f.type === 'tel' ? [Validators.pattern(/^\+[1-9][0-9]{7,14}$/)] : []),
          ],
        }),
      ]),
    ),
  );
  ngOnInit(): void {
    this.configureConditionalFields();
    this.loadApplication();
  }
  protected configureConditionalFields(): void {
    const values = this.form.getRawValue();
    const required = this.merchant
      ? ['COMPANY', 'PARTNERSHIP'].includes(values['businessType'])
      : values['vehicleType'] !== 'BICYCLE';
    for (const name of this.merchant
      ? ['businessRegistrationNumber']
      : ['drivingLicenceNumber', 'vehicleRegistrationNumber']) {
      const control = this.form.controls[name];
      control.setValidators([
        Validators.maxLength(this.merchant ? 50 : 20),
        ...(required ? [Validators.required, Validators.pattern(/\S/)] : []),
      ]);
      control.updateValueAndValidity();
    }
  }
  protected required(field: Field): boolean {
    return this.form.controls[field.name].hasValidator(Validators.required);
  }
  protected invalid(name: string): boolean {
    const c = this.form.controls[name];
    return c.touched && c.invalid;
  }
  protected label(value: string): string {
    return value.toLowerCase().replaceAll('_', ' ');
  }
  protected loadApplication(): void {
    if (this.user()?.onboardingState === 'APPLICATION_REQUIRED') return;
    this.loading.set(true);
    this.error.set('');
    this.http
      .get<Application>(this.endpoint + '/me')
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (application) => {
          this.reason.set(application.lastDecision?.reason ?? '');
          const values: Record<string, string> = {};
          for (const field of this.fields) {
            const value = application.businessAddress?.[field.name] ?? application[field.name];
            if (typeof value === 'string') values[field.name] = value;
          }
          this.form.patchValue(values);
          this.configureConditionalFields();
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
  }
  protected submit(): void {
    this.configureConditionalFields();
    this.form.markAllAsTouched();
    if (this.busy() || this.loading() || this.form.invalid) return;
    this.busy.set(true);
    this.error.set('');
    const values = Object.fromEntries(
      Object.entries(this.form.getRawValue()).map(([k, v]) => [k, v.trim()]),
    );
    const body: Record<string, unknown> = { ...values };
    if (this.merchant) {
      body['businessAddress'] = Object.fromEntries(
        ['line1', 'line2', 'city', 'postalCode'].map((k) => [k, values[k] || undefined]),
      );
      for (const k of ['line1', 'line2', 'city', 'postalCode']) delete body[k];
      if (!body['contactEmail']) delete body['contactEmail'];
    }
    this.http
      .post(this.endpoint, body)
      .pipe(
        switchMap(() => this.auth.refresh()),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => this.loadApplication(),
        error: (e) => {
          this.error.set(errorMessage(e));
          for (const item of readProblem(e).errors ?? [])
            this.form.controls[item.field.replace('businessAddress.', '')]?.setErrors({
              server: true,
            });
        },
      });
  }
  protected refreshStatus(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.auth
      .refresh()
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (user) => {
          if (homeFor(user) !== '/onboarding') void this.router.navigateByUrl(homeFor(user));
          else this.loadApplication();
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
  }
  protected logout(): void {
    this.auth
      .logout()
      .subscribe({
        next: () => void this.router.navigateByUrl('/login'),
        error: () => void this.router.navigateByUrl('/login'),
      });
  }
}
