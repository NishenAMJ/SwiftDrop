import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DeliveryZoneService } from './delivery-zone.service';
import { PricingService, PricingRuleRequest } from './pricing.service';

describe('Dispatcher pricing and zone services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('maps pricing rule operations to the parcel-pricing endpoints', () => {
    const pricing = TestBed.inject(PricingService);
    const rule: PricingRuleRequest = {
      ruleName: 'Standard', ruleCode: 'STD', baseFee: 300, ratePerKg: 50, defaultZoneCharge: 150,
      minWeightKg: 0.01, maxWeightKg: 30,
    };

    pricing.getAllRules().subscribe();
    expect(http.expectOne('/api/pricing/rules').request.method).toBe('GET');
    pricing.getRule(7).subscribe();
    http.expectOne('/api/pricing/rules/7').flush({});
    pricing.createRule(rule).subscribe();
    const create = http.expectOne('/api/pricing/rules');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(rule);
    pricing.updateRule(7, rule).subscribe();
    expect(http.expectOne('/api/pricing/rules/7').request.method).toBe('PUT');
  });

  it('maps zone operations, including activate/deactivate as a status patch', () => {
    const zones = TestBed.inject(DeliveryZoneService);
    zones.getZones().subscribe();
    http.expectOne('/api/zones').flush([]);
    zones.createZone({ code: 'CMB', name: 'Colombo' }).subscribe();
    expect(http.expectOne('/api/zones').request.method).toBe('POST');
    zones.updateZone('z1', { name: 'Colombo 1' }).subscribe();
    expect(http.expectOne('/api/zones/z1').request.method).toBe('PUT');
    zones.changeStatus('z1', 'INACTIVE').subscribe();
    const status = http.expectOne('/api/zones/z1/status');
    expect(status.request.method).toBe('PATCH');
    expect(status.request.body).toEqual({ status: 'INACTIVE' });
  });
});
