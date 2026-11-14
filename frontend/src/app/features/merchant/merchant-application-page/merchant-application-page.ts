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
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage, readProblem } from '../../../core/auth/problem-messages';
import { ApplicationStatusBadge } from '../../../shared/components/application-status-badge/application-status-badge';
import {
  FieldMessages,
  applyServerErrors,
  fieldErrorMessage,
  focusFirstInvalid,
} from '../../../shared/forms/form-errors';
import { PHONE_E164 } from '../../../shared/models/application';
import { ColomboDatePipe } from '../../../shared/pipes/colombo-date.pipe';
import {
  BUSINESS_TYPE_LABELS,
  BusinessType,
  Merchant,
  MerchantApplicationRequest,
  REGISTERED_BUSINESS_TYPES,
  formatAddress,
} from '../merchant.models';
import { MerchantService } from '../merchant.service';

type Field =
  | 'businessName'
  | 'businessType'
  | 'businessRegistrationNumber'
  | 'contactEmail'
  | 'contactPhone'
  | 'businessAddress.line1'
  | 'businessAddress.line2'
  | 'businessAddress.city'
  | 'businessAddress.postalCode';

const MESSAGES: Record<Field, FieldMessages> = {
  businessName: { required: 'Enter your business name.' },
  businessType: { required: 'Choose your business type.' },
  businessRegistrationNumber: { required: 'Enter the registration number for a company or partnership.' },
  contactEmail: { required: 'Enter a contact email.' },
  contactPhone: {
    required: 'Enter a contact phone number.',
    pattern: 'Enter the number with its country code, like +94771234567.',
  },
  'businessAddress.line1': { required: 'Enter the first line of the address.' },
  'businessAddress.line2': { required: '' },
  'businessAddress.city': { required: 'Enter the city.' },
  'businessAddress.postalCode': { required: '' },
};

const SUBMIT_ERRORS: Record<string, string> = {
  FORBIDDEN: 'Only customer accounts can apply to become a merchant.',
};

type LoadState = 'loading' | 'ready' | 'error';

/**
 * A customer applies to become a merchant, follows the decision, and resubmits after a
 * rejection. Approval adds the MERCHANT role, which reaches the session at its next refresh.
 */
@Component({
  selector: 'app-merchant-application-page',
  imports: [ReactiveFormsModule, RouterLink, ApplicationStatusBadge, ColomboDatePipe],
  templateUrl: './merchant-application-page.html',
  styleUrl: './merchant-application-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MerchantApplicationPage implements OnInit {
  private readonly merchants = inject(MerchantService);
  private readonly auth = inject(AuthService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly businessTypes = Object.entries(BUSINESS_TYPE_LABELS) as [BusinessType, string][];
  protected readonly businessTypeLabels = BUSINESS_TYPE_LABELS;
  protected readonly formatAddress = formatAddress;

  protected readonly state = signal<LoadState>('loading');
  protected readonly merchant = signal<Merchant | null>(null);
  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly isMerchant = computed(() => this.auth.roles().includes('MERCHANT'));

  /** A first application, or a resubmission after a rejection. */
  protected readonly canApply = computed(() => {
    const merchant = this.merchant();
    return merchant === null || merchant.status === 'REJECTED';
  });

  private readonly statusHeading = viewChild<ElementRef<HTMLElement>>('statusHeading');
  private readonly errorAlert = viewChild<ElementRef<HTMLElement>>('errorAlert');

  protected readonly form = this.fb.group({
    businessName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120), Validators.pattern(/\S/)]],
    businessType: ['' as BusinessType | '', Validators.required],
    businessRegistrationNumber: ['', Validators.maxLength(50)],
    contactEmail: ['', [Validators.email, Validators.maxLength(254)]],
    contactPhone: ['', [Validators.required, Validators.pattern(PHONE_E164)]],
    businessAddress: this.fb.group({
      line1: ['', [Validators.required, Validators.maxLength(120), Validators.pattern(/\S/)]],
      line2: ['', Validators.maxLength(120)],
      city: ['', [Validators.required, Validators.maxLength(80), Validators.pattern(/\S/)]],
      postalCode: ['', Validators.maxLength(20)],
    }),
  });

  protected readonly needsRegistration = signal(false);

  constructor() {
    // Companies and partnerships must give a registration number.
    this.form.controls.businessType.valueChanges.pipe(takeUntilDestroyed()).subscribe((type) => {
      const needed = !!type && REGISTERED_BUSINESS_TYPES.includes(type);
      this.needsRegistration.set(needed);
      const control = this.form.controls.businessRegistrationNumber;
      control.setValidators(needed ? [Validators.required, Validators.maxLength(50), Validators.pattern(/\S/)] : Validators.maxLength(50));
      control.updateValueAndValidity();
    });
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.merchants.me().subscribe({
      next: (merchant) => this.show(merchant),
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.merchant.set(null); // never applied
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
    const wasRejected = this.merchant()?.status === 'REJECTED';
    this.merchants.apply(this.request()).subscribe({
      next: (merchant) => {
        this.submitting.set(false);
        this.submitted.set(false);
        this.notice.set(
          wasRejected
            ? 'Application sent again. A dispatcher will review it.'
            : 'Application sent. A dispatcher will review it.',
        );
        this.show(merchant);
        afterNextRender(() => this.statusHeading()?.nativeElement.focus(), { injector: this.injector });
      },
      error: (error: unknown) => this.handleSubmitError(error),
    });
  }

  private show(merchant: Merchant): void {
    this.merchant.set(merchant);
    this.state.set('ready');
    if (merchant.status === 'REJECTED') {
      this.prefill(merchant);
    }
    // Approved while signed in: the session still has the old roles until a refresh.
    if (merchant.status === 'APPROVED' && !this.isMerchant()) {
      this.auth.refresh().subscribe({ error: () => undefined });
    }
  }

  private prefill(merchant: Merchant): void {
    this.form.reset({
      businessName: merchant.businessName,
      businessType: merchant.businessType,
      businessRegistrationNumber: merchant.businessRegistrationNumber ?? '',
      contactEmail: merchant.contactEmail ?? '',
      contactPhone: merchant.contactPhone,
      businessAddress: {
        line1: merchant.businessAddress.line1,
        line2: merchant.businessAddress.line2 ?? '',
        city: merchant.businessAddress.city,
        postalCode: merchant.businessAddress.postalCode ?? '',
      },
    });
  }

  private request(): MerchantApplicationRequest {
    const value = this.form.getRawValue();
    const optional = (text: string) => text.trim() || undefined;
    return {
      businessName: value.businessName.trim(),
      businessType: value.businessType as BusinessType,
      businessRegistrationNumber: this.needsRegistration()
        ? value.businessRegistrationNumber.trim()
        : optional(value.businessRegistrationNumber),
      contactEmail: optional(value.contactEmail),
      contactPhone: value.contactPhone.trim(),
      businessAddress: {
        line1: value.businessAddress.line1.trim(),
        line2: optional(value.businessAddress.line2),
        city: value.businessAddress.city.trim(),
        postalCode: optional(value.businessAddress.postalCode),
      },
    };
  }

  private handleSubmitError(error: unknown): void {
    this.submitting.set(false);
    const problem = readProblem(error);
    if (problem.code === 'APPLICATION_ALREADY_EXISTS') {
      // Sent from another tab, or already decided: show what the server has.
      this.notice.set('You already have a merchant application. Here is its current status.');
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
