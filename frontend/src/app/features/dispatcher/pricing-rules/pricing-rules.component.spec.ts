import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { PricingRule, PricingService } from '../services/pricing.service';
import { PricingRulesComponent } from './pricing-rules.component';

function rule(overrides: Partial<PricingRule>): PricingRule {
  return {
    id: 1,
    ruleName: 'Standard',
    ruleCode: 'STD',
    description: 'Default domestic pricing',
    baseFee: 300,
    ratePerKg: 50,
    defaultZoneCharge: 150,
    urgentSurcharge: 200,
    urgentSurchargeEnabled: false,
    minWeightKg: 0.01,
    maxWeightKg: 30,
    active: true,
    ...overrides,
  };
}

describe('PricingRulesComponent', () => {
  const rules = [
    rule({ id: 2, ruleName: 'Express', ruleCode: 'EXP', description: 'Same day', active: false }),
    rule({ id: 1 }),
  ];

  function setup(load: () => Observable<PricingRule[]> = () => of(rules)) {
    const api = {
      getAllRules: vi.fn(load),
      createRule: vi.fn(() => of(rule({ id: 3 }))),
      updateRule: vi.fn(() => of(rule({}))),
    };
    TestBed.configureTestingModule({
      imports: [PricingRulesComponent],
      providers: [{ provide: PricingService, useValue: api }],
    });
    const fixture = TestBed.createComponent(PricingRulesComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    // jsdom has no <dialog> modal support.
    component.pricingDialog.nativeElement.showModal = vi.fn();
    component.pricingDialog.nativeElement.close = vi.fn();
    return { fixture, component, api };
  }

  it('lists rules sorted by id', () => {
    const { component } = setup();
    expect(component.filteredRules().map((r) => r.id)).toEqual([1, 2]);
  });

  it('re-filters when the dispatcher types a search term', () => {
    const { component } = setup();
    component.searchCtrl.setValue('express');
    expect(component.filteredRules().map((r) => r.ruleCode)).toEqual(['EXP']);
    component.searchCtrl.setValue('same day');
    expect(component.filteredRules().map((r) => r.ruleCode)).toEqual(['EXP']);
  });

  it('re-filters when the status filter changes', () => {
    const { component } = setup();
    component.statusCtrl.setValue('INACTIVE');
    expect(component.filteredRules().map((r) => r.ruleCode)).toEqual(['EXP']);
    component.statusCtrl.setValue('ACTIVE');
    expect(component.filteredRules().map((r) => r.ruleCode)).toEqual(['STD']);
  });

  it('stops loading and shows the error when rules cannot be loaded', () => {
    const { component, fixture } = setup(() => throwError(() => ({ status: 500 })));
    fixture.detectChanges();
    expect(component.errorMessage()).toBe('Failed to load pricing rules');
    expect(component.loading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Failed to load pricing rules');
  });

  it('keeps the urgent surcharge disabled until it is switched on', () => {
    const { component } = setup();
    const surcharge = component.ruleForm.get('urgentSurcharge')!;
    expect(surcharge.disabled).toBe(true);
    component.ruleForm.get('urgentSurchargeEnabled')!.setValue(true);
    expect(surcharge.enabled).toBe(true);
    surcharge.setValue(-1);
    expect(surcharge.invalid).toBe(true);
    component.ruleForm.get('urgentSurchargeEnabled')!.setValue(false);
    expect(surcharge.disabled).toBe(true);
  });

  it('does not submit an invalid rule', () => {
    const { component, api } = setup();
    component.openAddModal();
    component.ruleForm.patchValue({ ruleName: '', ruleCode: 'X', baseFee: -1, minWeightKg: 0 });
    component.submitRule();
    expect(api.createRule).not.toHaveBeenCalled();
  });

  it('creates a new rule and reloads the list', () => {
    const { component, api } = setup();
    component.openAddModal();
    component.ruleForm.patchValue({ ruleName: 'Bulk', ruleCode: 'BULK', minWeightKg: 1, maxWeightKg: 100 });
    component.submitRule();
    expect(api.createRule).toHaveBeenCalledWith(expect.objectContaining({ ruleName: 'Bulk', ruleCode: 'BULK', urgentSurcharge: 0 }));
    expect(component.pricingDialog.nativeElement.close).toHaveBeenCalled();
    expect(api.getAllRules).toHaveBeenCalledTimes(2);
  });

  it('edits an existing rule by id', () => {
    const { component, api } = setup();
    component.openEditModal(rules[1]);
    expect(component.isEditMode).toBe(true);
    expect(component.ruleForm.get('ruleName')!.value).toBe('Standard');
    component.ruleForm.patchValue({ baseFee: 350 });
    component.submitRule();
    expect(api.updateRule).toHaveBeenCalledWith(1, expect.objectContaining({ baseFee: 350 }));
  });

  it('toggles a rule between active and inactive without losing its other fields', () => {
    const { component, api } = setup();
    component.toggleStatus(rules[0]);
    expect(api.updateRule).toHaveBeenCalledWith(2, expect.objectContaining({ active: true, ruleCode: 'EXP', baseFee: 300 }));
  });

  it('reports failed saves', () => {
    const { component, api } = setup();
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => undefined);
    api.createRule.mockReturnValueOnce(throwError(() => ({ status: 409 })) as never);
    component.openAddModal();
    component.ruleForm.patchValue({ ruleName: 'Dup', ruleCode: 'STD', minWeightKg: 1, maxWeightKg: 2 });
    component.submitRule();
    expect(alertSpy).toHaveBeenCalledWith('Create failed');
  });
});
