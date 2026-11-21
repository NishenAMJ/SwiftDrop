import { Component, ChangeDetectionStrategy, signal, computed, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { PricingService, PricingRule, PricingRuleRequest } from '../services/pricing.service';

/**
 * Pricing Rules Management page for Dispatchers.
 * Allows viewing, creating, and editing pricing rules.
 * UI mirrors other Dispatcher pages (Delivery Zones, Dashboard).
 */
@Component({
  selector: 'app-pricing-rules',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, DecimalPipe],
  templateUrl: './pricing-rules.component.html',
  styleUrl: './pricing-rules.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PricingRulesComponent {
  // Toggle active status of a rule
  toggleStatus(rule: PricingRule): void {
    const updated = { ...rule, active: !rule.active };
    // Prepare request payload matching PricingRuleRequest
    const payload: PricingRuleRequest = {
      ruleName: updated.ruleName,
      ruleCode: updated.ruleCode,
      description: updated.description,
      baseFee: updated.baseFee,
      ratePerKg: updated.ratePerKg,
      defaultZoneCharge: updated.defaultZoneCharge,
      urgentSurchargeEnabled: updated.urgentSurchargeEnabled ?? false,
      urgentSurcharge: updated.urgentSurcharge,
      minWeightKg: updated.minWeightKg,
      maxWeightKg: updated.maxWeightKg,
      active: updated.active,
    };
    this.pricingService.updateRule(updated.id, payload).subscribe({
      next: () => this.loadRules(),
      error: () => alert('Failed to change status'),
    });
  }
  private readonly pricingService = inject(PricingService);
  private readonly fb = inject(FormBuilder);

  // State signals
  readonly rules = signal<PricingRule[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  // Filters
  readonly searchCtrl = this.fb.control('');
  readonly statusCtrl = this.fb.control('ALL');

  // Derived filtered list
  readonly filteredRules = computed(() => {
    const term = this.searchCtrl.value?.toLowerCase() ?? '';
    const status = this.statusCtrl.value;
    return this.rules()
      .filter((r) => {
        const matchesTerm =
          r.ruleName.toLowerCase().includes(term) ||
          r.ruleCode.toLowerCase().includes(term) ||
          (r.description?.toLowerCase().includes(term) ?? false);
        const matchesStatus = status === 'ALL' || (r.active ? 'ACTIVE' : 'INACTIVE') === status;
        return matchesTerm && matchesStatus;
      })
      .sort((a, b) => a.id - b.id);
  });

  // Form handling (shared for add & edit)
  ruleForm: FormGroup;
  isEditMode = false;
  editingRuleId: number | null = null;

  @ViewChild('pricingDialog') pricingDialog!: ElementRef<HTMLDialogElement>;

  constructor() {
    this.ruleForm = this.fb.group({
      ruleName: ['', [Validators.required, Validators.maxLength(100)]],
      ruleCode: ['', [Validators.required, Validators.maxLength(50)]],
      description: [''],
      baseFee: [0, [Validators.required, Validators.min(0)]],
      ratePerKg: [0, [Validators.required, Validators.min(0)]],
      defaultZoneCharge: [0, [Validators.required, Validators.min(0)]],
      urgentSurchargeEnabled: [false],
      urgentSurcharge: [{ value: 0, disabled: true }, [Validators.min(0)]],
      minWeightKg: [0.01, [Validators.required, Validators.min(0.01)]],
      maxWeightKg: [0.01, [Validators.required, Validators.min(0.01)]],
      active: [true],
    });

    // Enable / disable urgent surcharge field based on checkbox
    this.ruleForm.get('urgentSurchargeEnabled')?.valueChanges.subscribe((enabled) => {
      const control = this.ruleForm.get('urgentSurcharge');
      if (enabled) {
        control?.enable();
        control?.setValidators([Validators.min(0)]);
      } else {
        control?.disable();
        control?.clearValidators();
      }
      control?.updateValueAndValidity();
    });

    this.loadRules();
  }

  loadRules(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.pricingService.getAllRules().subscribe({
      next: (data) => this.rules.set(data),
      error: () => this.errorMessage.set('Failed to load pricing rules'),
      complete: () => this.loading.set(false),
    });
  }

  openAddModal(): void {
    this.isEditMode = false;
    this.editingRuleId = null;
    this.ruleForm.reset({
      baseFee: 0,
      ratePerKg: 0,
      defaultZoneCharge: 0,
      urgentSurchargeEnabled: false,
      urgentSurcharge: { value: 0, disabled: true },
      minWeightKg: 0.01,
      maxWeightKg: 0.01,
      active: true,
    });
    this.pricingDialog.nativeElement.showModal();
  }

  openEditModal(rule: PricingRule): void {
    this.isEditMode = true;
    this.editingRuleId = rule.id;
    this.ruleForm.patchValue({
      ruleName: rule.ruleName,
      ruleCode: rule.ruleCode,
      description: rule.description,
      baseFee: rule.baseFee,
      ratePerKg: rule.ratePerKg,
      defaultZoneCharge: rule.defaultZoneCharge,
      urgentSurchargeEnabled: rule.urgentSurchargeEnabled ?? false,
      urgentSurcharge: rule.urgentSurcharge ?? 0,
      minWeightKg: rule.minWeightKg,
      maxWeightKg: rule.maxWeightKg,
      active: rule.active ?? false,
    });
    this.pricingDialog.nativeElement.showModal();
  }

  submitRule(): void {
    if (this.ruleForm.invalid) return;
    const payload: PricingRuleRequest = this.ruleForm.getRawValue();
    if (this.isEditMode && this.editingRuleId !== null) {
      this.pricingService.updateRule(this.editingRuleId, payload).subscribe({
        next: () => this.afterSave(),
        error: () => alert('Update failed'),
      });
    } else {
      this.pricingService.createRule(payload).subscribe({
        next: () => this.afterSave(),
        error: () => alert('Create failed'),
      });
    }
  }

  afterSave(): void {
    this.pricingDialog.nativeElement.close();
    this.loadRules();
  }

  closeDialog(): void {
    this.pricingDialog.nativeElement.close();
  }
}
