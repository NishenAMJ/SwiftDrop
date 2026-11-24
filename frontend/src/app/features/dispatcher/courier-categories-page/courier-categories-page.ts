import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { errorMessage } from '../../../core/auth/problem-messages';
import { CourierCategory } from '../../courier/courier-delivery.models';
import { CourierDeliveryService } from '../../courier/courier-delivery.service';

@Component({
  selector: 'app-courier-categories-page',
  imports: [ReactiveFormsModule],
  templateUrl: './courier-categories-page.html',
  styleUrl: './courier-categories-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourierCategoriesPage implements OnInit {
  private readonly service = inject(CourierDeliveryService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly categories = signal<CourierCategory[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly actionId = signal<string | null>(null);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    code: ['', [Validators.required, Validators.maxLength(30), Validators.pattern(/^\s*[A-Za-z0-9_-]+\s*$/)]],
    name: ['', [Validators.required, Validators.maxLength(80)]],
    description: ['', Validators.maxLength(300)],
    maxWeightKg: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  protected create(): void {
    this.submitted.set(true);
    this.clearMessages();
    const maxWeight = this.parseWeight();
    if (this.form.invalid || maxWeight === undefined || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.service
      .createCategory({
        code: value.code.trim().toUpperCase(),
        name: value.name.trim(),
        description: value.description.trim() || null,
        maxWeightKg: maxWeight,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (created) => {
          this.form.reset();
          this.submitted.set(false);
          this.notice.set(`Category ${created.name} created.`);
          this.load(false);
        },
        error: (error: unknown) =>
          this.error.set(
            errorMessage(error, {
              CONFLICT: 'A category with this code already exists.',
            }),
          ),
      });
  }

  protected toggle(category: CourierCategory): void {
    if (this.actionId()) return;
    this.clearMessages();
    const next = category.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.actionId.set(category.id);
    this.service
      .changeCategoryStatus(category.id, next)
      .pipe(finalize(() => this.actionId.set(null)))
      .subscribe({
        next: (updated) => {
          this.categories.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
          this.notice.set(
            next === 'ACTIVE' ? `${updated.name} is active again.` : `${updated.name} deactivated.`,
          );
        },
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  protected fieldError(field: 'code' | 'name' | 'description' | 'maxWeightKg'): string | null {
    const control = this.form.controls[field];
    if (!(control.touched || this.submitted())) return null;
    if (field === 'maxWeightKg') {
      return this.parseWeight() === undefined ? 'Enter a weight greater than 0, or leave blank.' : null;
    }
    if (!control.errors) return null;
    if (control.errors['required']) return 'This field is required.';
    if (control.errors['pattern']) return 'Use letters, numbers, hyphens or underscores only.';
    const limit = control.errors['maxlength']?.requiredLength;
    return limit ? `Use no more than ${limit} characters.` : 'Check this field.';
  }

  /** null = blank (no limit); undefined = invalid. */
  private parseWeight(): number | null | undefined {
    const raw = this.form.controls.maxWeightKg.value.trim();
    if (!raw) return null;
    const weight = Number(raw);
    return Number.isFinite(weight) && weight > 0 ? weight : undefined;
  }

  private load(showLoading = true): void {
    if (showLoading) this.loading.set(true);
    this.service
      .listCategories()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => this.categories.set(page.content),
        error: (error: unknown) => this.error.set(errorMessage(error)),
      });
  }

  private clearMessages(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
